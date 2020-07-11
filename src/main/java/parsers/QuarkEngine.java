package parsers;

import org.apache.commons.cli.CommandLine;
import org.ini4j.Ini;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static util.LoggingUtils.handleError;

public class QuarkEngine extends Parser {

    private static final String toolName = "QuarkEngine";

    private static final String[] headers = {"quark_engine_risk_level", "quark_engine_max_risk", "quark_engine_weighted_risk", "quark_engine_risk_percentage"};

    public QuarkEngine(Ini ini, CommandLine commandLine) {
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
        String fullStdoutPath = "";
        String riskLevel = "", maxScore = "", scorePercentage = "";
        double weightedScore = -1;


        for(Map<String, String> record : csvRecords) {

            try {
                fullStdoutPath = new File(reportDir.getCanonicalPath() +
                        "/" + record.get("stdout_path")).getCanonicalPath();

                List<String> lines = Files.readAllLines(new File(fullStdoutPath).toPath());

                weightedScore = 0;

                int skippedTableHorLines = 0;

                for(String ln : lines) {
                    String line = ln.trim().toLowerCase();

                    if(line.startsWith("[!] warning: ")) {
                        riskLevel = line.substring("[!] warning: ".length(), line.lastIndexOf("risk") - 1);
                    }
                    else if(line.startsWith("[*] total score: ")) {
                        maxScore = line.substring("[*] total score: ".length());
                    }
                    else if(line.startsWith("| ") && skippedTableHorLines == 2) {
                        StringBuilder weight = new StringBuilder();
                        int readVertLines = 0;
                        for(char c : line.toCharArray()) {
                            if(c == '|') readVertLines++;
                            else if(readVertLines == 4) weight.append(c);
                        }
                        weightedScore += Double.parseDouble(weight.toString());
                    }
                    else if(line.startsWith("+---")) skippedTableHorLines++;
                }

            } catch(IOException e) {
                handleError(e, toolName + ": Could not resolve path of '" +
                        reportDir.getAbsolutePath() + "' or read file '" + fullStdoutPath + "'", cli.hasOption("stacktrace"));
            }
        }

        scorePercentage = weightedScore < 0 ? "" :
                String.format("%.2f", weightedScore / (Double.parseDouble(maxScore)) * 100);

        return new String[] {riskLevel, maxScore, Double.toString(weightedScore), scorePercentage};
    }
}
