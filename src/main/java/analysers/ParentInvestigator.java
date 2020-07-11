package analysers;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.csv.CSVRecord;
import org.ini4j.Ini;
import org.ini4j.Profile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static java.nio.file.Files.createDirectories;
import static util.CSVUtils.containsByApkAndCommand;
import static util.CSVUtils.addToCSV;
import static util.LoggingUtils.*;

public class ParentInvestigator {

    private Collection<File> apks;
    private Collection<Investigator> tools;
    private Ini config;
    private CommandLine cli;

    public enum StreamName {
        STDOUT("stdout"),
        STDERR("stderr");

        private String name;

        private StreamName(String stringValue) {
            name = stringValue;
        }

        @Override
        public String toString() {
            return name;
        }

        public static StreamName toStreamName(String name) {
            switch(name.toLowerCase()) {
                case "stdout": return STDOUT;
                case "stderr": return STDERR;
                default: return null;
            }
        }
    }

    public ParentInvestigator(Collection<File> inputs, Ini ini, CommandLine commandLine) {
        apks = inputs;
        config = ini;
        cli = commandLine;

        tools = new LinkedList<>();
    }

    /**
     * adds all of the tools from the given file to the toolchain
     * @return this, to allow it to be chained with other methods during construction
     */
    public ParentInvestigator initTools() {
        //gather all sections from the ini
        for(Map.Entry section : config.entrySet()) {
            //if the tool has the key 'enabled' and it is set to true
            if(Boolean.parseBoolean(((Profile.Section) section.getValue())
                    .fetch("enabled"))) {
                //add an instance of an Investigator for the tool
                tools.add(new Investigator(section.getKey().toString(),
                        config, cli));
            }
        }

        List<String> toolNames = new LinkedList<>();
        tools.forEach(tool -> toolNames.add(tool.getIniSectionName()));
        getCurrentLogger().info("Initialised ParentInvestigator with tools: " + toolNames);

        return this;
    }


    public void startInvestigation(File inputDir, File outputDir,
                                   Collection<CSVRecord> metaReport, String metaFileName)
    {
        Iterator<File> fileIterator = apks.iterator();

        int i = 0;
        while(fileIterator.hasNext()) {
            File apk = fileIterator.next();
            handleInfo("Analysing " + apk.getName() + " (" + (i+1) + "/" + apks.size() + ")");

            runToolChain(apk, inputDir, outputDir, metaReport, metaFileName);

            i++;
        }
    }


    public void runToolChain(File apk, File inputDir, File outputDir,
                             Collection<CSVRecord> metaReport, String metaFileName)
    {
        Iterator<Investigator> toolIterator = tools.iterator();

        while(toolIterator.hasNext()) {
            Investigator investigator = toolIterator.next();
            Ini.Section toolSection = config.get(investigator.getIniSectionName());

            //only analyze if the tool is set to enabled, and we haven't investigated this apk before
            if(Boolean.parseBoolean(toolSection.fetch("enabled"))) {
                handleInfo("Tool '" + investigator.getIniSectionName() +
                        "' is starting its analysis of '" + apk.getName() + "'");

                List<ProcessBuilder> pbs = investigator.initProcesses(
                        apk, inputDir, outputDir);
                int i = 0;

                for(ProcessBuilder pb : pbs) {
                    if(!containsByApkAndCommand(apk.getName(),
                            toolSection.fetch("execution_command", i),
                            metaReport))
                    {
                        long startTime = System.currentTimeMillis();

                        //execute the command given in the ini file
                        Investigator.ExecutionResults er = investigator.executeProcess(pb,
                                !Boolean.parseBoolean(toolSection.fetch(
                                        "suppress_exit_code_warning_" + i)));

                        //store the time it took to execute
                        long totalTime = System.currentTimeMillis() - startTime;

                        //store stdout
                        File stdoutFile = saveOutputStream(StreamName.STDOUT,
                                toolSection, apk.getName(), er);

                        //store stderr if the ini file says we should
                        File stderrFile = saveOutputStreamIfNecessary(StreamName.STDERR,
                                toolSection, apk.getName(), er);

                        String stdout = "";
                        String stderr = "";
                        try {
                            stdout = stdoutFile != null ? stdoutFile.getCanonicalPath().replace(outputDir.getCanonicalPath(), ".") : "";
                            stderr = stderrFile != null ? stderrFile.getCanonicalPath().replace(outputDir.getCanonicalPath(), ".") : "";
                        } catch(IOException e) {
                            handleError(e, "Could not resolve path of stored output from tool '" +
                                    investigator.getIniSectionName() + "'", cli.hasOption("stacktrace"));
                        }

                        if(!addToCSV(metaFileName, cli.hasOption("stacktrace"), apk.getName(),
                                investigator.getIniSectionName(), Integer.toString(er.getExitCode()),
                                Long.toString(apk.length()), toolSection.fetch("execution_command", i),
                                Long.toString(totalTime), stdout, stderr, "false")) //add false for output is not parsed yet
                        {
                            handleWarning("CSV records were not updated successfully for command '" + pb.command() + "'");
                        }

                        //quit unless the ini file explicitly says it's okay to continue when an error occurs
                        if(er.getExitCode() != 0 && !Boolean.parseBoolean(
                                toolSection.fetch("continue_on_error"))) {
                            break;
                        }
                    }
                    else {
                        handleInfo("Command '" + toolSection.fetch("execution_command", i) +
                                "' has already been run on '" + apk.getName() + "' before, skipping to next command");
                    }

                    i++;
                }
            }
            else {
                handleInfo("Skipping disabled tool '" +
                        investigator.getIniSectionName() + "'");
            }
        }

        handleInfo("Finished toolchain for " + apk.getName());
    }


    private File saveOutputStreamIfNecessary(String streamName, Profile.Section toolSection,
                                             String apkName, Investigator.ExecutionResults er)
            throws IllegalArgumentException
    {
        StreamName name = StreamName.toStreamName(streamName);
        if(name == null) throw new IllegalArgumentException("'" + streamName +
                "' is not a valid StreamName " + Arrays.toString(StreamName.values()));
        else return saveOutputStreamIfNecessary(name, toolSection,
                apkName, er);
    }


    private File saveOutputStreamIfNecessary(StreamName streamName, Profile.Section toolSection,
                                            String apkName, Investigator.ExecutionResults er)
    {
        String valueOfKey = toolSection.fetch("save_" + streamName).toLowerCase();
        switch(valueOfKey) {
            case "true": {
                return saveOutputStream(streamName, toolSection, apkName, er);
            }
            case "on_error": {
                if(er.getExitCode() != 0) return saveOutputStream(streamName,
                        toolSection, apkName, er);
                break;
            }
            case "not_empty": {
                if(!er.getStream(streamName).isEmpty()) return saveOutputStream(streamName,
                        toolSection, apkName, er);
                break;
            }
            case "if_useful": {
                if(er.getExitCode() != 0 || !er.getStream(streamName).isEmpty()) {
                    return saveOutputStream(streamName, toolSection,
                            apkName, er);
                }
                break;
            }
            default: {
                getCurrentLogger().debug(streamName + " for tool '" + toolSection.getName() +
                        "' won't be saved, as condition '" + valueOfKey + "' was not met");
            }
        }
        return null;
    }

    private File saveOutputStream(StreamName streamName, Profile.Section toolSection,
                                  String apkName, Investigator.ExecutionResults er)
    {
        String outputFileBase = cli.getOptionValue("output", "./apk_reports") +
                "/tool_output/" + toolSection.getName();
        String streamString = er.getStream(streamName);
        String filename = outputFileBase + '/' + streamName + '_' + apkName + ".txt";
        return outputToFile(streamString, filename);
    }

    private File outputToFile(String output, String filename) {
        File f = new File(filename);

        File fCopy = f;
        int i = 1;
        while(fCopy.exists()) {
            String[] parts = f.getAbsolutePath().split("\\.");
            fCopy = new File(String.join(".",
                    Arrays.copyOfRange(parts, 0, parts.length-1)) +
                    "(" + i + ")." + parts[parts.length-1]);
            i++;
        }

        f = fCopy;

        try {
            createDirectories(f.getParentFile().toPath());
            Files.write(f.toPath(), output.getBytes());
            return f;
        } catch(IOException e) {
            handleError(e, "Could not store output to file '" + f.getAbsolutePath() + "'",
                    cli.hasOption("stacktrace"));
        }
        return null;
    }
}
