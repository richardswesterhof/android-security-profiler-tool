import org.apache.commons.cli.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.ini4j.Ini;
import parsers.ParentParser;
import util.CSVUtils;

import java.io.*;
import java.net.URISyntaxException;
import java.util.*;

import static defs.ERROR_CODES.*;
import static org.apache.commons.io.FileUtils.listFiles;
import static util.LoggingUtils.*;
import static util.TimeConverter.millisToTime;

public class MainParser {

    private static final String JAR_NAME = "parser.jar";
    private static final String REL_CFG_PATH = "./config/config.ini";

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

        setLogLevels(cli, PARSE_LOGGER);

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
        String cfgPath = cli.hasOption("config") ? cli.getOptionValue("config") : getDefaultCfgPath(cli.hasOption("stacktrace"));
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
        File input = new File(cli.getOptionValue("input", "./apk_reports/"));

        //TODO: refactor these if statements to a method
        if(input.getAbsolutePath().lastIndexOf('.') > input.getAbsolutePath().lastIndexOf(System.getProperty("file.separator", "/"))) {
            handleError("Argument value for 'input': '" + input.getAbsolutePath() + "' is not a folder name");
            System.exit(INVALID_ARGUMENTS.getCode());
        }


        File metaReportFile = new File(input.getAbsolutePath() + "/meta-report.csv");
        File parsedReportFile = new File(input.getAbsolutePath() + "/report.csv");
        Collection<CSVRecord> metaReport = new HashSet<>();

        //check if the input file exists, and if it does, read the content
        if(input.exists()) {
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
            handleError("Could not find the given input path '" + input.getAbsolutePath() + "'");
            System.exit(INPUT_NOT_FOUND.getCode());
        }

        ParentParser pp = new ParentParser(metaReport, metaReportFile, parsedReportFile, config, cli).initTools();
        pp.startParsing();

        long totalTimeRaw = System.currentTimeMillis() - start;
        String totalTime = millisToTime(totalTimeRaw);

        handleInfo("Parsing all results took " + totalTime);
    }


    private static Options initOptions() {
        Option input = new Option("i", "input", true,
                "The folder specified as the output path during the analysis stage. " +
                        "The parsed report will be stored in this folder as well, as 'report.csv'. " +
                        "Default value is './apk_reports'.");
        input.setArgName("INPUT");

        Option help = new Option("h", "help", false,
                "Print this help message and exit.");

        Option version = new Option("v", "version", false,
                "Print the version number and exit.");

        Option config = new Option("c", "config", true,
                "The path to the config file to use. If left out, the default path will be used. (" +
                        REL_CFG_PATH + ")");
        config.setArgName("CONFIG_FILE");

        Option stacktrace = new Option("s", "stacktrace", false,
                "If provided, print the stacktrace of any exceptions that occur.");

        Option logLevel = new Option("l", "log-level", true,
                "Set the minimum level a message must be to be output to the log file. Can be one of {INFO, WARNING, ERROR, OFF}, default: ERROR.");
        logLevel.setArgName("LOG_LEVEL");

        Option logLevelConsole = new Option("lc", "log-level-console", true,
                "Set the minimum level a message must be to be output to stdout. Can be one of {INFO, WARNING, ERROR, OFF}, default: INFO.");
        logLevelConsole.setArgName("LOG_LEVEL");

        //collect all options into one object
        Options options = new Options();
        options
                .addOption(config)
                .addOption(help)
                .addOption(input)
                .addOption(logLevel)
                .addOption(logLevelConsole)
                .addOption(version)
                .addOption(stacktrace)
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
            File path = new File(new File(MainParser.class.
                    getProtectionDomain().getCodeSource().getLocation().
                    toURI()).getParent() + "/" + REL_CFG_PATH);
            return path.getCanonicalPath();
        } catch(URISyntaxException | IOException e) {
            handleError(e, "Default config path could not be resolved, " +
                    "please specify the path explicitly using the '--config' option.\n" +
                    "Please re-run with '--help' for more information", stacktrace);
            return null;
        }
    }
}
