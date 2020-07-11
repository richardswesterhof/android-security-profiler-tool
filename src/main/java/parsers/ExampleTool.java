package parsers;

import org.apache.commons.cli.CommandLine;
import org.ini4j.Ini;

import java.io.File;
import java.util.Collection;
import java.util.Map;

public class ExampleTool extends Parser {

    private static final String toolName = "ExampleTool";

    private static final String[] headers = {"some", "example", "headers"};

    public ExampleTool(Ini ini, CommandLine commandLine) {
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
        //the array that is returned here should (of course) be the same length as the headers array
        String[] values = new String[headers.length];

        //each one of these records represents the execution of one of the commands
        //specified in the ini section of this tool, all on the same apk
        for(Map<String, String> record : csvRecords) {
            //do some parsing based on the record
            //the record will contain values for the following keys:
            //apk_name, tool_name, exit_code, command, time_taken, stdout_path, stderr_path, has_been_parsed
            //(aka the headers of the meta-report.csv file)
            values = new String[] {"and", "example", "values"};
        }
        return values;
    }
}
