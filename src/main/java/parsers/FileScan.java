package parsers;

import analysers.Investigator;
import org.apache.commons.cli.CommandLine;
import org.ini4j.Ini;
import util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static util.LoggingUtils.handleError;

public class FileScan extends Parser {

    private static final String toolName = "FileScan";

    private static final String[] headers = {"filescan_risk_score", "embedded_apks",
            "infected_files", "suspicious_files", "shell_commands", "urls", "possible_phone_numbers"};

    public FileScan(Ini ini, CommandLine commandLine) {
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
        String totalRisk = "";
        int embedded = -1, infected = -1, suspicious = -1, commands = -1, urls = -1, phoneNums = -1;

        for(Map<String, String> record : csvRecords) {

            String fullStdoutPath = reportDir + "/" + record.get("stdout_path");
            String toolOutputPath = null;

            File rippedPathFile = null;

            try {
                StringBuilder sha1 = new StringBuilder();
                boolean gobbledFileName = false;

                //there is always only one line on stdout
                String stdoutLine = Files.readAllLines(new File(fullStdoutPath).toPath()).get(0);
                for(char c : stdoutLine.toCharArray()) {
                    if(!gobbledFileName && c == ';') gobbledFileName = true;
                    else if(c == ';') break;
                    else if(gobbledFileName) sha1.append(c);
                }

                rippedPathFile = new File(StringUtils.resolveSysVars(
                                myConfig.fetch("execution_pwd")) +
                        "/" + sha1.toString() + ".filescan.txt");
                String rippedPath = rippedPathFile.getCanonicalPath();
                toolOutputPath = getPathOnMachine(rippedPath, originalDir,
                        reportDir.getAbsolutePath(), cli.hasOption("stacktrace"));

                if(toolOutputPath == null) continue;

                embedded = infected = suspicious = commands = urls = phoneNums = 0;

                String sectionName = "";
                List<String> lines = Files.readAllLines(new File(toolOutputPath).toPath());
                //easy hack to add some breathing at the end of the file
                lines.add("");
                List<String> sectionEntries = new LinkedList<>();

                for(int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if(line.matches("\\[.*?\\].*?") || i == lines.size()-1) {
                        switch(sectionName) {
                            case "Embedded apk": {
                                embedded = sectionEntries.size();
                                break;
                            }
                            case "Infected files": {
                                infected = sectionEntries.size();
                                break;
                            }
                            case "Suspect files": {
                                suspicious = sectionEntries.size();
                                break;
                            }
                            case "Shell commands": {
                                commands = sectionEntries.size();
                                break;
                            }
                            case "Urls": {
                                urls = sectionEntries.size();
                                break;
                            }
                            case "Possible phone numbers": {
                                phoneNums = sectionEntries.size();
                                break;
                            }
                        }

                        sectionName = line.length() > 2 ? line.substring(1, line.length() - 1) : "";
                        sectionEntries = new LinkedList<>();
                    }
                    else if(line.startsWith("Total risk score:"))
                        totalRisk = line.substring("Total risk score:".length()).trim();
                    else if(!line.equals("") && !line.startsWith("-----")) {
                        if(sectionName.equals("Infected files")) {
                            if(!line.startsWith("From") && !line.startsWith("(")) sectionEntries.add(line);
                        }
                        else sectionEntries.add(line);
                    }
                }
            } catch(IOException e) {
                handleError(e, "Could not read file '" + fullStdoutPath +
                        "', or resolve '" + rippedPathFile.getAbsolutePath() + "'",
                        cli.hasOption("stacktrace"));
            }
        }

        return new String[] {totalRisk, Integer.toString(embedded),
                Integer.toString(infected), Integer.toString(suspicious),
                Integer.toString(commands), Integer.toString(urls),
                Integer.toString(phoneNums)
        };
    }
}
