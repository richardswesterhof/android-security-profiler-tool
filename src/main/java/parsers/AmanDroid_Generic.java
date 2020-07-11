package parsers;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.csv.CSVFormat;
import org.ini4j.Ini;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static util.LoggingUtils.handleError;

public abstract class AmanDroid_Generic extends Parser {

    public AmanDroid_Generic(Ini ini, CommandLine commandLine) {
        super(ini, commandLine);
    }

    public String[] parseOutputCSV(Collection<Map<String, String>> csvRecords, String[] headers, String modeKeyword, String originalDir, File reportDir) {
        String[] values = new String[headers.length];
        int misuseCount = 0;

        for(Map<String, String> record : csvRecords) {
            if(record.get("command").equals(CSVFormat.DEFAULT.format(
                    myConfig.fetch("execution_command"))))
            {
                try {
                    List<String> lines = Files.readAllLines(
                            new File(reportDir + record.get("stdout_path")).toPath());
                    boolean isMisuseLine = false;
                    for(String line : lines) {
                        if(line.contains(modeKeyword + ":")) isMisuseLine = true;
                        else if(isMisuseLine && !line.trim().isEmpty() && !line.contains("No misuse")) misuseCount++;
                    }
                } catch(IOException e) {
                    handleError(e, "Could not read stdout file from " + getIniSectionName() +
                            "'" + record.get("stdout_path") + "'", cli.hasOption("stacktrace"));
                }
            }
        }

        return new String[] {Integer.toString(misuseCount)};
    }
}
