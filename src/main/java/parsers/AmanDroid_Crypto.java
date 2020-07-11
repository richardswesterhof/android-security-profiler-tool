package parsers;

import org.apache.commons.cli.CommandLine;
import org.ini4j.Ini;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import static util.LoggingUtils.handleError;

public class AmanDroid_Crypto extends AmanDroid_Generic {

    private static final String toolName = "AmanDroid_Crypto";

    private static final String[] headers = {"crypto_misuses"};

    public AmanDroid_Crypto(Ini ini, CommandLine commandLine) {
        super(ini, commandLine);
    }


    @Override
    public String getIniSectionName() {
        return toolName;
    }

    @Override
    public String[] getCSVHeaders() {
        return headers;
    }

    @Override
    public String[] parseOutputCSV(Collection<Map<String, String>> csvRecords, String originalDir, File reportDir) {
        return parseOutputCSV(csvRecords, headers, "CryptographicMisuse", originalDir, reportDir);
    }

}
