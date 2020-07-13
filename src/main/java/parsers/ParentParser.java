package parsers;

import defs.exceptions.IllegalClassNameException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.csv.CSVRecord;
import org.ini4j.Ini;
import org.ini4j.Profile;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.*;

import static util.CSVUtils.*;
import static util.LoggingUtils.*;

public class ParentParser {

    private List<Parser> tools;
    private Ini config;
    private CommandLine cli;
    private Collection<CSVRecord> metaReport;
    private File metaReportFile;
    private File parsedReportFile;


    public ParentParser(Collection<CSVRecord> metaReport, File metaReportFile, File parsedReportFile, Ini ini, CommandLine cli) {
        this.metaReport = metaReport;
        this.metaReportFile = metaReportFile;
        this.parsedReportFile = parsedReportFile;
        this.config = ini;
        this.cli = cli;

        this.tools = new LinkedList<>();
    }


    /**
     * adds all of the tools from the given file to the toolchain
     * @return this, to allow it to be chained with other methods during construction
     */
    public ParentParser initTools() {
        //gather all sections from the ini
        for(Map.Entry section : config.entrySet()) {
            //if the tool has the key 'enabled' and it is set to true
            if(Boolean.parseBoolean(((Profile.Section) section.getValue())
                    .fetch("enabled"))) {
                //add an instance of an Investigator for the tool
                try {
                    tools.add(createClass((String) section.getKey()));
                } catch(ClassNotFoundException e) {
                    handleError(e, "Could not find class with classname '" +
                            section.getKey() + "'", cli.hasOption("stacktrace"));
                }
            }
        }
		
		//to add a new tool manually, in case the reflection doesn't pick it up
		//replace [params...] by the actual parameters for the constructor
		//tools.add(new YourClass([params...]));

        List<String> toolNames = new LinkedList<>();
        tools.forEach(tool -> toolNames.add(tool.getIniSectionName()));
        handleInfo("Initialised ParentInvestigator with tools: " + toolNames);

        return this;
    }


    public void startParsing() {
        Collection<CSVRecord> metaCopy = new HashSet<>(metaReport);
        Collection<CSVRecord> alreadyParsed = new HashSet<>(metaReport);
        alreadyParsed.removeIf(record -> !Boolean.parseBoolean(record.get("has_been_parsed").trim()));
        Iterator<CSVRecord> metaIter = metaCopy.iterator();

        String originalDirTemp = metaReportFile.getParentFile().getAbsolutePath();
        try {
            originalDirTemp = Files.readAllLines(
                    new File(metaReportFile.getAbsoluteFile().getParent() + "/fullPath.txt")
                            .toPath()).get(0);
        } catch(IOException e) {
            handleError(e, "Could not retrieve original output path from '" +
                    metaReportFile.getAbsoluteFile().getParent() + "/fullPath.txt', assuming it is '" +
                    originalDirTemp + "'", cli.hasOption("stacktrace"));
        }

        final String originalDir = originalDirTemp;


        //create CSV file for report
        List<String> fullHeaders = getAllHeaders();
        if(!parsedReportFile.exists()) {
            createCSV(parsedReportFile, fullHeaders.toArray(new String[0]),
                    cli.hasOption("stacktrace"));
        }


        //for all records in the meta csv file
        for(int i = 0; metaIter.hasNext(); i++) {
            CSVRecord nextRecord = metaIter.next();
            if(alreadyParsed.contains(nextRecord)) {
                if(Boolean.parseBoolean(nextRecord.get("has_been_parsed").trim())) {
                    handleInfo("Tool '" + nextRecord.get("tool_name") +
                            "' has already parsed its output for '" + nextRecord.get("apk_name") +
                            "' with command '" + nextRecord.get("command") +
                            "', skipping to next meta entry");
                }
                continue;
            }

            String apkName = nextRecord.get("apk_name");
            Collection<CSVRecord> apkRecords = subCollectionByColVal(metaCopy,
                    "apk_name", apkName);
            List<String[]> apkResults = new LinkedList<>();

            //each tool will find its records for the current apk
            tools.forEach(tool -> {
                Collection<CSVRecord> toolApkRecords = getSubSetByToolName(tool.getIniSectionName(), apkRecords);

                Iterator<CSVRecord> toolApkRecIter = toolApkRecords.iterator();
                //remove all of the records that have already been parsed
                while(toolApkRecIter.hasNext()) {
                    CSVRecord nextToolApkRecord = toolApkRecIter.next();
                    if(alreadyParsed.contains(nextToolApkRecord)) {
                        if(Boolean.parseBoolean(nextToolApkRecord.get("has_been_parsed").trim())) {
                            handleInfo("The output for command '" + nextToolApkRecord.get("command") +
                                    "' executed on '" + apkName + "' has already been parsed");
                        }

                        toolApkRecIter.remove();
                    }
                }

                //the remaining records all need parsing
                String[] toolResult = new String[tool.getCSVHeaders().length];
                if(toolApkRecords.isEmpty()) {
                    toolResult = new String[tool.getCSVHeaders().length];
                    for(int j = 0; toolResult.length < tool.getCSVHeaders().length; j++) toolResult[j] = "";
                }
                else {
                    Collection<Map<String, String>> apkRecs = new HashSet<>();
                    toolApkRecords.forEach(rec -> apkRecs.add(rec.toMap()));

                    handleInfo("Tool '" + tool.getIniSectionName() +
                            "' is parsing its output for '" + apkName + "'");


                    //parse the actual output
                    try {
                        toolResult = tool.parseOutputCSV(apkRecs, originalDir,
                                metaReportFile.getParentFile());
                    } catch(Exception e) {
                        handleError(e, "An exception occurred during the parsing of '" +
                                nextRecord.get("apk_name") + "' by tool '" + tool.getIniSectionName() + "'",
                                cli.hasOption("stacktrace"));
                        for(int j = 0; toolResult.length < tool.getCSVHeaders().length; j++) toolResult[j] = "";
                    }


                    if(toolResult.length != tool.getCSVHeaders().length) {
                        handleError("Length of returned values (" + toolResult.length +
                                ") from " + tool.getIniSectionName() + " did not match the length of its headers (" +
                                tool.getCSVHeaders().length + ")");

                        //if the length doesn't match the header length, we cannot trust the results,
                        //and thus we set an array of empty strings
                        for(int j = 0; toolResult.length < tool.getCSVHeaders().length; j++) toolResult[j] = "";
                    }
                    else {
                        toolApkRecords.forEach(toolApkRecord -> {
                            editCSVCellBytes(metaReportFile, apkName, toolApkRecord.get("command"),
                                    "has_been_parsed", "true", cli.hasOption("stacktrace"));
                        });
                    }

                    //always add the toolApkRecords,
                    //because we already *tried* to parse them, and if it didn't work,
                    //there's probably no use in trying again later
                    alreadyParsed.addAll(toolApkRecords);
                }

                apkResults.add(toolResult);
            });

            List<String> apkValues = new LinkedList<>();
            apkValues.add(apkName);
            apkValues.add(nextRecord.get("file_size"));
            apkResults.forEach(resultSet -> apkValues.addAll(Arrays.asList(resultSet)));

            addToCSV(parsedReportFile, cli.hasOption("stacktrace"),
                    apkValues.toArray(new String[0]));
        }
    }


    private List<String> getAllHeaders() {
        List<String> lst = new LinkedList<>();
        lst.add("apk_name");
        lst.add("file_size");
        tools.forEach(tool -> lst.addAll(Arrays.asList(tool.getCSVHeaders())));
        return lst;
    }


    private Parser createClass(String className) throws ClassNotFoundException {
        String thisCN = this.getClass().getName();
        className = thisCN.substring(0, thisCN.lastIndexOf('.') + 1) + className;
        //find the class by name
        Class<?> clazz = Class.forName(className);
        if(!Parser.class.isAssignableFrom(clazz)) throw new IllegalClassNameException("Class '" + className +
                "' does not inherit from Parser");

        try {
            //get the constructor of the class by its argument types
            Constructor constructor = clazz.getConstructor(Ini.class, CommandLine.class);
            //return a new instance generated by the found constructor
            return (Parser) constructor.newInstance(config, cli);
        }
        //we only catch these exceptions (and not ClassNotFoundException)
        //because these are the exceptions caused by the code from this function itself,
        //whereas ClassNotFoundException is caused by passing the wrong parameter to this function
        catch(NoSuchMethodException | InstantiationException |
                IllegalAccessException | InvocationTargetException e) {
            handleError(e, "Could not initialise a new '" + className +
                            "' because the proper constructor could not be found.",
                    cli.hasOption("stacktrace"));
        }

        return null;
    }
}
