package util;

import org.apache.commons.cli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static util.LoggingUtils.handleError;

public class StringUtils {

    public static final String CAPK_KEYWORD = "@CURRENT_APK";
    public static final String INDIR_KEYWORD = "@INPUT_DIR";
    public static final String OUTDIR_KEYWORD = "@OUTPUT_DIR";
    public static final String APKNM_KEYWORD = "@APK_NAME";
    public static final String APK_PARENT_KEYWORD = "@APK_PARENT_FILE";
    public static final String CUSTOM_ENV_VAR_KEY = "ENV_VAR_";

    /**
     * splits a string on a delimiter character,
     * but keeps anything between string quotes ("some string") together
     * Note that the quotes will be left in place,
     * so running this method multiple times will not change the result
     * @param s the string to split
     * @param regex the delimiter to use
     * @return an array of substrings
     */
    public static String[] splitString(String s, String regex) {
        List<String> subStrings = new ArrayList<>();
        char[] chars = s.toCharArray();
        boolean insideQuotes = false;
        StringBuilder subString = new StringBuilder();
        for(char c : chars) {
            //flip insideQuotes if we find a (") character
            if(c == '"') insideQuotes = !insideQuotes;

            if(!insideQuotes && Character.toString(c).matches(regex)) {
                subStrings.add(subString.toString());
                subString = new StringBuilder();
            }
            else subString.append(c);
        }
        subStrings.add(subString.toString());
        return subStrings.toArray(new String[0]);
    }


    public static String formatAsCommand(List<String> strings) {
        StringBuilder sb = new StringBuilder();
        for(String s : strings) {
            if(strings.indexOf(s) > 0) sb.append(' ');

            boolean alreadyAppended = false;
            for(char c : s.toCharArray()) {
                if(Character.isWhitespace(c)) {
                    sb.append('\"');
                    sb.append(s);
                    sb.append('\"');
                    alreadyAppended = true;
                    break;
                }
            }
            if(!alreadyAppended) sb.append(s);
        }

        return sb.toString();
    }


    public static String[] findAllMatches(String source, String regex) {
        List<String> matches = new LinkedList<>();
        Matcher m = Pattern.compile(regex).matcher(source);
        while(m.find()) {
            matches.add(m.group());
        }

        return matches.toArray(new String[0]);
    }


    public static String resolveKeywords(String raw, File pathToApk, File inputDir, File outputDir, boolean stacktrace) {
        String replaced = raw;
        try {
            replaced = replaced.replace(CAPK_KEYWORD, "\"" + pathToApk.getCanonicalPath() + "\"");
            replaced = replaced.replace(APKNM_KEYWORD, "\"" + pathToApk.getName() + "\"");
            replaced = replaced.replace(INDIR_KEYWORD, "\"" + inputDir.getCanonicalPath() + "\"");
            replaced = replaced.replace(OUTDIR_KEYWORD, "\"" + outputDir.getCanonicalPath() + "\"");
            replaced = replaced.replace(APK_PARENT_KEYWORD, "\"" + pathToApk.getParentFile().getCanonicalPath() + "\"");
        } catch(IOException e) {
            handleError(e, "Could not resolve path while replacing keywords in '" + raw + "'", stacktrace);
        }
        return resolveSysVars(replaced);
    }


    public static String resolveSysVars(String raw) {
        StringBuilder sb = new StringBuilder();
        StringBuilder sysVar = new StringBuilder();
        boolean insidePercent = false;
        //go through all characters
        for(int i = 0; i < raw.length(); i++) {
            // % flips insidePercent
            if(raw.charAt(i) == '%') {
                //if we are inside percent, we need to get the value of the system variable
                if(insidePercent) sb.append(System.getenv(sysVar.toString()));
                insidePercent = !insidePercent;
                sysVar = new StringBuilder();
            }
            //if we are insidePercent, we add this character to the system variable name
            else if(insidePercent) sysVar.append(raw.charAt(i));
                //if we are not insidePercent, we add this character to the resolved string
            else sb.append(raw.charAt(i));
        }

        return sb.toString();
    }
}
