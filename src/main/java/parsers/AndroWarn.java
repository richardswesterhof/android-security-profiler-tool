package parsers;

import org.apache.commons.cli.CommandLine;
import org.ini4j.Ini;

import java.io.File;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.util.*;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static util.LoggingUtils.handleDebug;
import static util.LoggingUtils.handleError;

public class AndroWarn extends Parser {

    private static final String toolName = "AndroWarn";

    private static final String[] headers = {"package_name", "app_version",
            "telephony_identifiers_leakage", "device_settings_harvesting",
            "connection_interfaces_exfiltration", "telephony_services_abuse",
            "is_signed", "min_sdk", "effective_target_sdk", "max_sdk"}; //length = 10


    public AndroWarn(Ini ini, CommandLine commandLine) {
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

    //I am aware this code doesn't look very good, but due the hard to parse format, this is the easiest way
    @Override
    public String[] parseOutputCSV(Collection<Map<String, String>> csvRecords, String originalDir, File reportDir) {
        String version = "", pkgName = "", isSigned = "", targetSDK = "", minSDK = "", maxSDK = "";
        int cie = -1, dsh = -1, til = -1, tsa = -1;

        for(Map<String, String> record : csvRecords) {
            if(record.get("command").contains("cmd.exe")) continue;
            String fullStdoutPath = reportDir.getAbsolutePath() + "/" + record.get("stdout_path");
            String toolOutputPath = null;

            try {
                List<String> stdoutLines = Files.readAllLines(new File(fullStdoutPath).toPath());
                for(String stdoutLine : stdoutLines) {
                    int ind = stdoutLine.indexOf("report available ");
                    if(ind >= 0) {
                        toolOutputPath = getPathOnMachine(stdoutLine.substring(ind + "report available ".length() + 1,
                                stdoutLine.length() - 1), originalDir, reportDir.getAbsolutePath(), cli.hasOption("stacktrace"));
                    }
                }

                //if the toolOutputPath cannot be found in the stdout, return all empty values by continuing here
                if(toolOutputPath == null) continue;

                cie = dsh = til = tsa = 0;

                List<String> lines;
                try {
                    lines = Files.readAllLines(
                            new File(toolOutputPath).toPath());
                } catch(MalformedInputException e) {
                    handleDebug(toolName + ": File '" + toolOutputPath +
                            "' could not be loaded in normal encoding, trying ISO_8859_1");
                    lines = Files.readAllLines(new File(toolOutputPath).toPath(), ISO_8859_1);
                }

                String currCat = "";
                List<String> catEntries = new LinkedList<>();
                for(int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    line = line.trim();
                    //lines that start with [.] are new categories
                    if(line.startsWith("[.]")) {
                        switch(currCat) {
                            case "Application Version": {
                                version = getTrimmed(catEntries, 0);
                                version = version == null ? "" : version.substring(2);
                                break;
                            }
                            case "Package Name": {
                                pkgName = getTrimmed(catEntries, 0);
                                pkgName = pkgName == null ? "" : pkgName.substring(2);
                                break;
                            }
                            case "Connection Interfaces Exfiltration": {
                                cie = catEntries.size();
                                break;
                            }
                            case "Device Settings Harvesting": {
                                dsh = catEntries.size();
                                break;
                            }
                            case "Telephony Identifiers Leakage": {
                                til = catEntries.size();
                                break;
                            }
                            case "Telephony Services Abuse": {
                                tsa = catEntries.size();
                                break;
                            }
                        }

                        currCat = line.substring(4);
                        catEntries = new LinkedList<>();
                    }
                    else if(line.trim().startsWith("- APK is signed: ")) {
                        isSigned = line.substring("- APK is signed: ".length());
                    }
                    else if(line.trim().startsWith("- Effective target SDK: ")) {
                        targetSDK = line.substring("- Effective target SDK: ".length());
                    }
                    else if(line.trim().startsWith("- Min SDK: ")) {
                        minSDK = line.substring("- Min SDK: ".length());
                    }
                    else if(line.trim().startsWith("- Max SDK: ")) {
                        maxSDK = line.trim().substring("- Max SDK: ".length());
                    }
                    else if(line.trim().startsWith("-")) catEntries.add(line.trim());
                    //ignore other lines
                }
            } catch(IOException e) {
                handleError(e, "Could not read lines from file '" + fullStdoutPath + "' or '" +
                        toolOutputPath + "'", cli.hasOption("stacktrace"));
            }
        }

        return new String[] {pkgName, version, til < 0 ? "" : Integer.toString(til),
                dsh < 0 ? "" : Integer.toString(dsh), cie < 0 ? "" : Integer.toString(cie),
                tsa < 0 ? "" : Integer.toString(tsa), isSigned, minSDK, targetSDK, maxSDK};
    }

    private static String getTrimmed(List<String> strings, int ind) {
        if(strings.size() <= ind) {
            handleError(toolName + ": reached end of category entries before extracting a value");
            return null;
        }
        return strings.get(ind).trim();
    }
}