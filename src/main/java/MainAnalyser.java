import analysers.ParentInvestigator;
import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.ini4j.Ini;
import util.CSVUtils;

import java.io.*;
import java.net.URISyntaxException;
import java.util.*;

import static defs.ERROR_CODES.*;
import static org.apache.commons.io.FileUtils.listFiles;
import static util.LoggingUtils.*;
import static util.TimeConverter.millisToTime;

public class MainAnalyser {

    private static final String JAR_NAME = "analyser.jar";
    private static final String REL_CFG_PATH = "/config/config.ini";

    public static void main(String[] args) {
        //keep track of the time
        long start = System.currentTimeMillis();

        //initialise all command line options
        Options opts = initOptions();

        //parse the given command line arguments
        CommandLineParser clip = new DefaultParser();
        CommandLine cli = null;

        try {
            cli = clip.parse(opts, args);
        } catch(ParseException e) {
            //if there are any parse errors, print help message and exit
            printHelp(opts);
            System.exit(INVALID_ARGUMENTS.getCode());
        }

        //if we get here it means that the arguments were parsed successfully

        setLogLevels(cli, ANALYSIS_LOGGER);

        //check for special modes, i.e. --help or --version
        if(cli.hasOption("help")) {
            printHelp(opts);
            System.exit(0);
        }
        else if(cli.hasOption("version")) {
            printVersion();
            System.exit(0);
        }


        //read the configuration file
        String cfgPath = cli.hasOption("config") ? cli.getOptionValue("config") :
                getDefaultCfgPath(cli.hasOption("stacktrace"));
        if(cfgPath == null) System.exit(UNEXPECTED_ERROR.getCode());

        Ini config = new Ini();
        String resolvedCfgPath = cfgPath;

        try {
            resolvedCfgPath = new File(cfgPath).getCanonicalPath();
            config.load(new FileReader(resolvedCfgPath));
        } catch(FileNotFoundException e) {
            handleError(e, "The given config file '" + cfgPath + "' could not be found",
                    cli.hasOption("stacktrace"));
            System.exit(FILE_NOT_FOUND.getCode());
        } catch(IOException f) {
            handleError(f, "IOException occurred while loading config file.",
                    cli.hasOption("stacktrace"));
            System.exit(UNEXPECTED_ERROR.getCode());
        }

        //parse the rest of the arguments
        String reportType = "csv";
        File input = new File(cli.getOptionValue("input"));
        File output = new File(cli.getOptionValue("output", "./apk_reports"));
        Collection<File> inputs = null;

        if(output.getAbsolutePath().lastIndexOf('.') > output.getAbsolutePath().lastIndexOf(System.getProperty("file.separator", "/"))) {
            handleError("Argument value for 'output': '" + output.getAbsolutePath() + "' is not a folder name");
            System.exit(INVALID_ARGUMENTS.getCode());
        }

        if(!output.exists()) output.mkdirs();

        //check if the input path exists, and if it does, gather the files
        if(input.exists()) {
            if(cli.hasOption("bulk") && input.isDirectory()) {
                //these are the extensions we want the files to have,
                //if it has another extensions, it won't be added to the list
                String[] extensions = {"apk"};
                //gather all files, potentially recursively going through all subdirectories
                inputs = listFiles(input, extensions, cli.hasOption("recursive"));
                //enforce the capacity limit from the command line arguments
                File[] files = inputs.toArray(new File[0]);
                int max = Integer.parseInt(cli.getOptionValue("max-apks", Integer.toString(inputs.size())));
                inputs.retainAll(Arrays.asList(Arrays.copyOfRange(files, 0, max)));
            }
            else if(!cli.hasOption("bulk") && input.isFile()) {
                //single file mode
                inputs = new LinkedList<>();
                inputs.add(input);
            }
            else {
                handleError("Type of input (" + (input.isDirectory() ? "directory" : "file") +
                        ") did not match quantity mode specified (" +
                        (cli.hasOption("bulk") ? "bulk" : "single") + ")");
                System.exit(WRONG_MODE.getCode());
            }
        }
        else {
            String path;
            try {
                //getCanonicalPath resolves '.' and '..'
                path = input.getCanonicalPath();
            } catch(IOException e) {
                //however, if that fails, we can use the absolute path,
                //which keeps any '.' or '..' in the path
                path = input.getAbsolutePath();
                if(cli.hasOption("stacktrace")) e.printStackTrace();
            }
            handleError("Could not find the given input path (" + path + ")");
            System.exit(INPUT_NOT_FOUND.getCode());
        }

        handleInfo("Found " + inputs.size() + " apk file" + (inputs.size() == 1 ? "" : "s") + ".");

        File metaReportFile = new File(output.getAbsolutePath() + "/meta-report" + "." + reportType);
        Collection<CSVRecord> metaReport = new HashSet<>();

        if(metaReportFile.exists()) {
            try(FileReader metaReader = new FileReader(metaReportFile)) {
                CSVFormat.DEFAULT.withHeader(CSVUtils.metaHeaders)
                        .withFirstRecordAsHeader()
                        .parse(metaReader)
                        .forEach(metaReport::add);
            } catch(IOException e) {
                handleError(e, "Could not read meta report from '" + metaReportFile + "'",
                        cli.hasOption("stacktrace"));
                System.exit(INVALID_META_CSV.getCode());
            }
        }
        else {
            CSVUtils.createCSV(metaReportFile, CSVUtils.metaHeaders, cli.hasOption("stacktrace"));
        }

        //store the directory name this was run in, to get the best chance possible
        // that the paths can be reconstructed and changed on other pcs
        File fullPathDescriptor = new File(output.getAbsolutePath() + "/fullPath.txt");

        try (FileWriter fw = new FileWriter(fullPathDescriptor)) {
            fw.append(output.getCanonicalPath());
        } catch(IOException e) {
            handleError(e, "Could not open file '" + fullPathDescriptor.getAbsolutePath() + "'",
                    cli.hasOption("stacktrace"));
        }

        //start the actual investigation
        ParentInvestigator pi = new ParentInvestigator(inputs, config, cli).initTools();
        pi.startInvestigation(input, output.getAbsoluteFile(),
                metaReport, metaReportFile.getAbsolutePath());

        long totalTimeRaw = System.currentTimeMillis() - start;
        String totalTime = millisToTime(totalTimeRaw);

        handleInfo("Execution of entire analysis took " + totalTime);
    }


    private static Options initOptions() {
        //declare all command line options
        Option bulk = new Option("b", "bulk", false,
                "If this argument is provided, " +
                        "the INPUT argument should be a directory containing all the APKs to be analysed.");

        Option recursive = new Option("r", "recursive", false,
                "Goes through all subdirectories of the provided INPUT directory. " +
                        "Only works in bulk mode.");

        Option input = new Option("i", "input", true,
                "Either the directory containing all APKs to be analysed in bulk mode, " +
                        "or the APK file to be analysed in single mode.");
        input.setRequired(true);
        input.setArgName("INPUT");

        Option output = new Option("o", "output", true,
                "The directory to store the output to. If left out, " +
                        "it will be in a subdirectory called 'apk_reports' relative to your current directory.");
        output.setArgName("OUTPUT");

        Option help = new Option("h", "help", false,
                "Print this help message and exit.");

        Option version = new Option("v", "version", false,
                "Print the version number and exit.");

        Option time = new Option("t", "time", false,
                "Set a stopwatch for the investigation of each apk and the entire program.");

        Option config = new Option("c", "config", true,
                "The path to the config file to use. If left out, the default path will be used.");
        config.setArgName("CONFIG_FILE");

        Option stacktrace = new Option("s", "stacktrace", false,
                "If provided, print the stacktrace of any exceptions that occur.");

        Option logLevel = new Option("l", "log-level", true,
                "Set the minimum level a message must be to be output to the log file. Can be one of {INFO, WARNING, ERROR, OFF}, default: ERROR.");
        logLevel.setArgName("LOG_LEVEL");

        Option logLevelConsole = new Option("lc", "log-level-console", true,
                "Set the minimum level a message must be to be output to stdout. Can be one of {INFO, WARNING, ERROR, OFF}, default: INFO.");
        logLevelConsole.setArgName("LOG_LEVEL");

        Option maxAPKCount = new Option("m", "max-apks", true,
                "The maximum amount of apks to analyze.");

        //collect all options into one object
        Options options = new Options();
        options
                .addOption(bulk)
                .addOption(config)
                .addOption(help)
                .addOption(input)
                .addOption(logLevel)
                .addOption(logLevelConsole)
                .addOption(maxAPKCount)
                .addOption(output)
                .addOption(recursive)
                .addOption(stacktrace)
                .addOption(version)
        ;

        return options;
    }


    private static void printHelp(Options opts) {
        HelpFormatter hf = new HelpFormatter();
        hf.printHelp("java -jar " + JAR_NAME + " -i INPUT [options]", opts);
    }


    private static void printVersion() {
        String version = "version 0.2.0";
        //TODO: version checking
        System.out.println(version);
    }


    private static String getDefaultCfgPath(boolean stacktrace) {
        try {
            File path = new File(new File(MainAnalyser.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent() + REL_CFG_PATH);
            return path.getCanonicalPath();
        } catch(URISyntaxException | IOException e) {
            handleError(e, "Default config path could not be resolved, please specify the path explicitly using the '--config' option.\n" +
                    "Please re-run with '--help' for more information", stacktrace);
            return null;
        }
    }
}
