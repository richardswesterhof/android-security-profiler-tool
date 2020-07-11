package util;

public class TimeConverter {

    public static String millisToTime(long millis) {
        long diffHours = millis / (1000*60*60);
        long diffMinutes = (millis - diffHours * 1000*60*60) / (1000*60);
        long diffSeconds = (millis - diffHours * 1000*60*60 - diffMinutes * 1000*60) / 1000;
        long diffMillis = (millis - diffHours * 1000*60*60 - diffMinutes * 1000*60 - diffSeconds*1000);
        return diffHours + "hrs:" + diffMinutes + "min:" + diffSeconds + "sec:" + diffMillis + "ms";
    }
}
