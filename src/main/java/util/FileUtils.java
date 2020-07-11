package util;

import org.json.JSONObject;

import java.util.Date;

public class FileUtils {

    public static void putIndividualDetails(JSONObject report, long totalTimeRaw) {
        String totalTime = TimeConverter.millisToTime(totalTimeRaw);
        report.put("general", new JSONObject().put("total_time", totalTime).put("total_time_raw", totalTimeRaw));
        ((JSONObject) report.get("general")).put("date_generated", new Date(System.currentTimeMillis()))
                .put("date_generated_raw", System.currentTimeMillis());
    }

    public static void putMassDetails(JSONObject report, long totalTimeRaw, int apkCount) {
        putIndividualDetails(report, totalTimeRaw);
        ((JSONObject) report.get("general")).put("apk_count", apkCount);
    }
}
