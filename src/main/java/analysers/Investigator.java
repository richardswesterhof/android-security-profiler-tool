package analysers;

import defs.ERROR_CODES;
import org.apache.commons.cli.CommandLine;
import org.ini4j.Ini;
import util.Session;
import util.StreamReader;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static util.LoggingUtils.getCurrentLogger;
import static util.LoggingUtils.handleError;
import static util.StringUtils.*;

public class Investigator {

    //the Investigator constructor takes care of setting the config and myConfig
    protected Ini config;
    protected Ini.Section myConfig;

    protected String toolName;

    public static final String CAPK_KEYWORD = "@CURRENT_APK";
    public static final String INDIR_KEYWORD = "@INPUT_DIR";
    public static final String OUTDIR_KEYWORD = "@OUTPUT_DIR";
    public static final String APKNM_KEYWORD = "@APK_NAME";
    public static final String APK_PARENT_KEYWORD = "@APK_PARENT_FILE";
    public static final String CUSTOM_ENV_VAR_KEY = "ENV_VAR_";

    protected Map<String, String> environment;

    protected Session session;

    //every implementation of an Investigator has access to the command line arguments
    protected CommandLine cli;

    protected Map<String, String> customEnvVars;

    public class ExecutionResults {

        private String stdout;
        private String stderr;
        private int exitCode;
        private String command;

        public ExecutionResults(String command, String stdout, String stderr, int exitCode) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
            this.command = command;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getStderr() {
            return stderr;
        }

        public String getStdout() {
            return stdout;
        }

        public String getCommand() {
            return command;
        }

        public String getStream(ParentInvestigator.StreamName name) {
            switch(name) {
                case STDOUT: return getStdout();
                case STDERR: return getStderr();
                default: return null;
            }
        }

        @Override
        public String toString() {
            return "Execution Results: process exited with code " + exitCode +
                    "\n-----stdout-----:\n" + stdout + "<end stdout>" +
                    "\n-----stderr-----:\n" + stderr + "<end stderr>";
        }
    }

    public Investigator(String toolName, Ini ini, CommandLine commandLine) {
        config = ini;
        this.toolName = toolName;
        myConfig = ini.get(toolName);
        cli = commandLine;
        customEnvVars = new HashMap<>();
        //setup the custom environment variables
        myConfig.forEach((key, value) -> {
            if(key.startsWith(CUSTOM_ENV_VAR_KEY)) {
                String varName = key.substring(CUSTOM_ENV_VAR_KEY.length());
                customEnvVars.put(varName, value);
            }
        });
    }

    /**
     * every instance of an investigator must be able to specify its section name in the ini file
     * @return the section name corresponding to the specific class that implements this method
     */
    public String getIniSectionName() {
        return toolName;
    }


    public List<ProcessBuilder> initProcesses(File pathToApk, File inputDir, File outputDir) {
        String[] execCmds = myConfig.fetchAll("execution_command", String[].class);
        List<ProcessBuilder> pbs = new LinkedList<>();
        String execPwd = myConfig.fetch("execution_pwd");

        for(String execCmd : execCmds) {
            execCmd = resolveKeywords(execCmd, pathToApk, inputDir, outputDir,
                    Boolean.parseBoolean(cli.getOptionValue("stacktrace")));
            String[] splitCmd = splitString(execCmd, "\\s");
            for(int i = 0; i < splitCmd.length; i++) {
                splitCmd[i] = splitCmd[i].replace("\"", "");
            }
            ProcessBuilder pb = createProcess(splitCmd,
                    resolveKeywords(execPwd, pathToApk, inputDir, outputDir,
                            Boolean.parseBoolean(cli.getOptionValue("stacktrace"))));
            pbs.add(pb);
        }

        session = new Session(pathToApk, inputDir, outputDir);

        return pbs;
    }


    /**
     * executes a process represented by the \code{ProcessBuilder}
     * @param pb the \code{ProcessBuilder} to execute the process from
     * @return the results of the execution: exit code, stdout output, stderr output
     */
    public ExecutionResults executeProcess(ProcessBuilder pb, boolean warnNonZeroExitCode) {
        Process p = null;
        int status = ERROR_CODES.INTERNAL_ERROR.getCode();
        String stdout = null;
        String stderr = null;
        try {
            p = pb.start();

            //create new threads that will read stdout and stderr of the new process
            //this needs to be done on separate threads, to make sure no blocking occurs
            StreamReader stdoutReader = new StreamReader(p.getInputStream(), cli.hasOption("stacktrace"));
            Thread stdoutGobbler = new Thread(stdoutReader);
            StreamReader stderrReader = new StreamReader(p.getErrorStream(), cli.hasOption("stacktrace"));
            Thread stderrGobbler = new Thread(stderrReader);
            stdoutGobbler.start();
            stderrGobbler.start();

            status = p.waitFor();


            //as soon as the process has finished we should be able to read its output,
            //close the streams, and join the threads
            stdoutReader.close();
            stderrReader.close();

            stdoutGobbler.join();
            stderrGobbler.join();

            stdout = stdoutReader.getOutput();
            stderr = stderrReader.getOutput();

            if(status != 0 && warnNonZeroExitCode) {
                getCurrentLogger().warn("Process for tool '" +
                        getIniSectionName() + "' with command '" + formatAsCommand(pb.command()) +
                        "' exited with code " + status + "");
            }
        } catch(IOException e) {
            handleError(e, "Could not start process for tool '" +
                    getIniSectionName() + "' with command '" + formatAsCommand(pb.command()) + "'",
                    cli.hasOption("stacktrace"));
        } catch(InterruptedException f) {
            handleError(f, "Main process got interrupted while waiting for subprocess for tool '" +
                    getIniSectionName() + "' with command '" + formatAsCommand(pb.command()) + "'",
                    cli.hasOption("stacktrace"));
        } catch(IllegalThreadStateException g) {
            handleError(g, "Thread was still running while trying to read its output.",
                    cli.hasOption("stacktrace"));
        }

        if(p != null) p.destroy();

        return new ExecutionResults(formatAsCommand(pb.command()), stdout, stderr, status);
    }


    /**
     * creates an executable process, using the pwd that the program is run from
     * @param command the command to execute
     * @return the process
     */
    protected ProcessBuilder createProcess(String[] command) {
        return createProcess(command, new File(System.getProperty("user.dir")));
    }


    /**
     * creates an executable process, providing a path to the pwd that the command should be executed in
     * @param command the command to execute
     * @param pwdPath the path to the folder the command should be executed in
     * @return the process
     */
    protected ProcessBuilder createProcess(String[] command, String pwdPath) {
        return createProcess(command, new File(pwdPath));
    }


    /**
     * creates an executable process, providing a File which is the folder the command should be executed in
     * @param command the command to execute
     * @param pwd the folder to execute the command in
     * @return the process
     */
    protected ProcessBuilder createProcess(String[] command, File pwd) {
        ProcessBuilder pb = new ProcessBuilder(command);
        environment = pb.environment();
        //add all custom environment variables specified in the ini file
        environment.putAll(customEnvVars);
        getCurrentLogger().debug("custom env vars: " + customEnvVars);

        pb.directory(pwd);

        return pb;
    }
}
