package parsers;

import org.apache.commons.cli.CommandLine;
import org.ini4j.Ini;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static util.LoggingUtils.handleError;

public class RiskInDroid extends Parser {

    private static final String toolName = "RiskInDroid";

    private static final String[] headers = {"riskindroid_risk_score", "permissions_declared",
            "permissions_required_and_used", "permissions_required_not_used", "permissions_not_required_used"};

    public RiskInDroid(Ini ini, CommandLine commandLine) {
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
        int declared = -1, requiredAndUsed = -1, requiredNotUsed = -1, notRequiredUsed = -1;
        String risk = "";

        for(Map<String, String> record : csvRecords) {

            String fullStdoutPath = reportDir + "/" + record.get("stdout_path");

            try {
                List<String> lines = Files.readAllLines(new File(fullStdoutPath).toPath());

                //the file only contains one line, which is raw json
                JSONObject json = new JSONObject(lines.get(0));
                risk = json.get("risk").toString();

                if(!json.has("permissions")) continue;

                JSONArray permissions = json.getJSONArray("permissions");
                Iterator<Object> permIter = permissions.iterator();

                declared = requiredAndUsed = requiredNotUsed = notRequiredUsed = 0;

                while(permIter.hasNext()) {
                    JSONObject nextPerm = (JSONObject) permIter.next();
                    switch(nextPerm.getString("cat")) {
                        case "Declared": {
                            declared++;
                            break;
                        }
                        case "Required and Used": {
                            requiredAndUsed++;
                            break;
                        }
                        case "Required but Not Used": {
                            requiredNotUsed++;
                            break;
                        }
                        case "Not Required but Used": {
                            notRequiredUsed++;
                            break;
                        }
                    }
                }

            } catch(IOException e) {
                handleError(e, toolName + ": Could not read file '"  +
                        fullStdoutPath + "'", cli.hasOption("stacktrace"));
            }
        }

        return new String[] {risk, declared < 0 ? "" : Integer.toString(declared),
                requiredAndUsed < 0 ? "" : Integer.toString(requiredAndUsed),
                requiredNotUsed < 0 ? "" : Integer.toString(requiredNotUsed),
                notRequiredUsed < 0 ? "" : Integer.toString(notRequiredUsed)
        };
    }
}
