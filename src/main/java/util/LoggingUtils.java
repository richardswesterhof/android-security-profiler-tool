package util;

import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

public class LoggingUtils {

    public static final String ANALYSIS_LOGGER = "FileLogger";
    public static final String PARSE_LOGGER = "ParseLogger";

    private static String LOGGER_TYPE;

    public static Logger getCurrentLogger() {
        switch(LOGGER_TYPE) {
            case ANALYSIS_LOGGER: return LogManager.getLogger(ANALYSIS_LOGGER);
            default: return LogManager.getLogger(PARSE_LOGGER);
        }
    }

    public static void handleDebug(String debugText) {
        getCurrentLogger().debug(debugText);
    }


    public static void handleWarning(String warningText) {
        getCurrentLogger().warn(warningText);
    }

    public static void handleError(Exception e, String errorText, boolean stacktrace) {
        handleError(errorText);
        if(stacktrace) e.printStackTrace();
    }

    public static void handleError(String errorText) {
        getCurrentLogger().error(errorText);
    }

    public static void handleError(String errorDescription, String errorText, boolean stacktrace) {
        handleError(errorText);
        if(stacktrace) getCurrentLogger().error(errorDescription);
    }

    public static void handleInfo(String info) {
        getCurrentLogger().info(info);
    }


    public static void setLogLevels(CommandLine cli, String logger) {
        LOGGER_TYPE = logger;
        Level logLevel = getLeastSpecific(
                Level.toLevel(cli.getOptionValue("log-level", "ERROR"), Level.ERROR),
                Level.ERROR);
        Level logLevelConsole = getLeastSpecific(
                Level.toLevel(cli.getOptionValue("log-level-console", "ERROR"), Level.ERROR),
                Level.ERROR);

        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final Configuration config = context.getConfiguration();
        LoggerConfig fileLoggerConfig = config.getLoggers().get(ANALYSIS_LOGGER);
        LoggerConfig parseLoggerConfig = config.getLoggers().get(PARSE_LOGGER);

        //set the loglevel for the log file
        fileLoggerConfig.removeAppender("File");
        fileLoggerConfig.addAppender(config.getAppender("File"), logLevel, null);

        parseLoggerConfig.removeAppender("ParserFile");
        parseLoggerConfig.addAppender(config.getAppender("ParserFile"), logLevel,null);

        //set the loglevel for the console
        fileLoggerConfig.removeAppender("STDOUT");
        fileLoggerConfig.addAppender(config.getAppender("STDOUT"), logLevelConsole, null);

        parseLoggerConfig.removeAppender("STDOUT");
        parseLoggerConfig.addAppender(config.getAppender("STDOUT"), logLevelConsole, null);

        context.updateLoggers();
    }


    private static Level getLeastSpecific(Level l1, Level l2) {
        if(l1.isLessSpecificThan(l2)) return l1;
        return l2;
    }
}
