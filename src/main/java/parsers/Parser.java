package parsers;

import org.apache.commons.cli.CommandLine;
import org.ini4j.Ini;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import static util.LoggingUtils.handleError;

public abstract class Parser {

    protected Ini config;
    protected Ini.Section myConfig;

    protected CommandLine cli;

    public static final String genericFileSeparator = "[/\\\\]|\\\\\\\\";

    public Parser(Ini ini, CommandLine commandLine) {
        config = ini;
        myConfig = ini.get(getIniSectionName());
        cli = commandLine;
    }

    public abstract String getIniSectionName();

    public abstract String[] getCSVHeaders();


    public abstract String[] parseOutputCSV(Collection<Map<String, String>> csvRecords, String originalDir, File reportDir);


    public static String getPathOnMachine(String fullPath, String oldBase, String newBase, boolean stacktrace) {
        try {
            String fullPathFormatted = new File(fullPath).getCanonicalPath();
            String oldBaseFormatted = new File(oldBase).getCanonicalPath();
            String newBaseFormatted = new File(newBase).getCanonicalPath();
            return fullPathFormatted.replace(oldBaseFormatted, newBaseFormatted);
        } catch(IOException e) {
            handleError(e, "Could not get path on machine for '" +
                    fullPath + "'", stacktrace);
        }
        return null;
    }

}
