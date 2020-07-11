package parsers;

import org.apache.commons.cli.CommandLine;
import org.ini4j.Ini;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static util.LoggingUtils.handleError;


public class AndroBugs extends Parser {

    private static final String toolName = "AndroBugs";

    private static final String[] headers = {"androbugs_critical", "androbugs_warning", "androbugs_notice", "androbugs_info"};

    public AndroBugs(Ini ini, CommandLine commandLine) {
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
        int criticals = -1, warnings = -1, notices = -1, infos = -1;

        for(Map<String, String> record : csvRecords) {
            String fullStdoutPath = reportDir + "/" + record.get("stdout_path");
            String toolOutputPath = null;

            try {
                List<String> stdoutLines = Files.readAllLines(new File(fullStdoutPath).toPath());
                for(String stdoutLine : stdoutLines) {
                    int ind = stdoutLine.indexOf("report is generated: ");
                    if(ind >= 0) {
                        String rippedPath = stdoutLine.substring(ind + "report is generated: ".length(), stdoutLine.indexOf(" >>>"));
                        toolOutputPath = getPathOnMachine(rippedPath, originalDir,
                                reportDir.getAbsolutePath(), cli.hasOption("stacktrace"));
                    }
                }

                if(toolOutputPath == null) continue;

                criticals = warnings = notices = infos = 0;

                List<String> lines = Files.readAllLines(new File(toolOutputPath).toPath());
                for(String ln : lines) {
                    String line = ln.trim();
                    //yes, this could have been done cleaner,
                    //but for such a small amount of options
                    //this works fine and is easy to read
                    if(line.startsWith("[Critical]")) criticals++;
                    else if(line.startsWith("[Warning]")) warnings++;
                    else if(line.startsWith("[Notice]")) notices++;
                    else if(line.startsWith("[Info]")) infos++;
                }
            } catch(IOException e) {
                handleError(e, toolName + ": Could not read file '" + fullStdoutPath + "'",
                        cli.hasOption("stacktrace"));
            }
        }

        return new String[] {criticals < 0 ? "" : Integer.toString(criticals),
                warnings < 0 ? "" : Integer.toString(warnings),
                notices < 0 ? "" : Integer.toString(notices),
                infos < 0 ? "" : Integer.toString(infos)};
    }
}
