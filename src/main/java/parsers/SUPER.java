package parsers;

import org.apache.commons.cli.CommandLine;
import org.ini4j.Ini;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static util.LoggingUtils.handleError;

public class SUPER extends Parser {

    private static final String toolName = "SUPER";

    private static final String[] headers = {"SUPER_total_vulnerabilities", "SUPER_criticals", "SUPER_highs", "SUPER_mediums", "SUPER_lows", "SUPER_warnings"};

    private static final String packageName = "manifest package: ";

    private static final String diffOutputFolder = "Seems that the package in the AndroidManifest.xml is not the same as the application ID provided";

    public SUPER(Ini ini, CommandLine commandLine) {
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
        String total = "", crits = "", highs = "", mediums = "", lows = "", warns = "";

        for(Map<String, String> record : csvRecords) {
            String fullStdoutPath = reportDir + "/" + record.get("stdout_path");
            String toolOutputPath = "";

            try {

                String apkName = record.get("apk_name");
                toolOutputPath = reportDir.getAbsolutePath() + "/tool_output/SUPER/" +
                        apkName;

                File toolOutput = new File(toolOutputPath);
                File[] fls = toolOutput.listFiles();

                if(fls == null || fls.length == 0) {
                    handleError("Directory '" + toolOutputPath + "' is empty, cannot parse results");
                    continue;
                }
                else {
                    File fl = null;
                    boolean shouldContinue = false;
                    for(int i = 0; i < fls.length; i++) {
                        if(fls[i].isDirectory() && fl != null) {
                            handleError("Directory '" + toolOutputPath + "' contains more than 1 subdirectory, cannot know which one to use");
                            shouldContinue = true;
                            break;
                        }
                        else if(fls[i].isDirectory()) {
                            fl = fls[i];
                        }
                    }
                    if(shouldContinue) continue;
                    else if(fl != null) toolOutputPath = toolOutput.getAbsolutePath() + "/" + fl.getName() + "/results.json";
                    else {
                        handleError("Directory '" + toolOutputPath + "' does not contain any subdirectories, cannot find results");
                        continue;
                    }
                }

                //the output file contains one line, which is raw json
                List<String> lines = Files.readAllLines(new File(toolOutputPath).toPath());
                JSONObject json = new JSONObject(lines.get(0));

                total = json.get("total_vulnerabilities").toString();
                crits = json.get("criticals_len").toString();
                highs = json.get("highs_len").toString();
                mediums = json.get("mediums_len").toString();
                lows = json.get("lows_len").toString();
                warns = json.get("warnings_len").toString();

            } catch(IOException e) {
                handleError(e, "Could not read lines from file '" +
                        fullStdoutPath + "' or '" + toolOutputPath + "'", cli.hasOption("stacktrace"));
            }
        }

        return new String[] {total, crits, highs, mediums, lows, warns};
    }
}
