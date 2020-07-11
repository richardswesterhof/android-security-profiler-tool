package util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

import static util.LoggingUtils.handleError;
import static util.StringUtils.splitString;

public class CSVUtils {

    public static final String[] metaHeaders = {"apk_name", "tool_name", "exit_code", "file_size",
            "command", "time_taken", "stdout_path", "stderr_path", "has_been_parsed"};

    public static final String csvSplitter = "\\s*?,\\s*?";


    public static CSVRecord getByApkAndCommand(String apkName, String command, Collection<CSVRecord> csv) {
        Iterator<CSVRecord> csvIter = csv.iterator();

        while(csvIter.hasNext()) {
            CSVRecord next = csvIter.next();
            if(next.get("apk_name").equals(apkName) && next.get("command").equals(command)) return next;
        }
        return null;
    }

    public static Collection<CSVRecord> getSubSetByToolName(String toolName, Collection<CSVRecord> csv) {
        Iterator<CSVRecord> csvIter = csv.iterator();
        Collection<CSVRecord> hits = new HashSet<>();

        while(csvIter.hasNext()) {
            CSVRecord next = csvIter.next();
            if(next.get("tool_name").equals(toolName)) hits.add(next);
        }

        return hits;
    }

    public static boolean containsByApkAndCommand(String apkName, String command, Collection<CSVRecord> csv) {
        return getByApkAndCommand(apkName, command, csv) != null;
    }

    public static Collection<CSVRecord> subCollectionByColVal(Collection<CSVRecord> original, String colName, String colVal) {
        Collection<CSVRecord> subCollection = new HashSet<>(original);
        subCollection.removeIf(record -> !record.get(colName).equals(colVal));
        return subCollection;
    }


    public static boolean createCSV(File file, String[] headers, boolean stacktrace) {
        try(FileWriter csvWriter = new FileWriter(file, false);
            CSVPrinter printer = new CSVPrinter(csvWriter, CSVFormat.DEFAULT.withHeader(headers)))
        {
            //printing a comment will create the file without having to add an actual entry
            printer.printComment("this is a comment");
        } catch(IOException e) {
            handleError(e, "Could not create new meta report at '" + file.getAbsolutePath() + "'", stacktrace);
            return false;
        }
        return true;
    }


    public static boolean createCSV(String filename, String[] headers, boolean stacktrace) {
        return createCSV(new File(filename), headers, stacktrace);
    }


    public static boolean addToCSV(File file, boolean stacktrace, String ...values) {
        try(FileWriter csvWriter = new FileWriter(file, true);
            CSVPrinter printer = new CSVPrinter(csvWriter, CSVFormat.DEFAULT))
        {
            printer.printRecord((Object[]) values);
        } catch(IOException e) {
            handleError(e, "Could not create new meta report at '" + file.getAbsolutePath() + "'", stacktrace);
            return false;
        }
        return true;
    }

    public static boolean addToCSV(String filename, boolean stacktrace, String ...values) {
        return addToCSV(new File(filename), stacktrace, (String[]) values);
    }

    /**
     * WARNING: this method will truncate the newVal if the amount of bytes you are writing
     * is more than the amount of bytes you are overwriting!!!
     * @param file
     * @param apkName
     * @param command
     * @param colName
     * @param newVal
     * @param stacktrace
     * @return
     */
    public static boolean editCSVCellBytes(File file, String apkName, String command, String colName, String newVal, boolean stacktrace) {
        if(file == null || !file.exists()) return false;

        //format the command according to the default format, so it will look exactly like in the file
        command = CSVFormat.DEFAULT.format(command);

        try(RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            String nextLine;
            String[] headers = splitString(raf.readLine(), csvSplitter);
            List<String> headerList = Arrays.asList(headers);
            int apkInd = headerList.indexOf("apk_name");
            int cmdInd = headerList.indexOf("command");
            int colInd = headerList.indexOf(colName);
            long startOfLine = raf.getFilePointer();

            while((nextLine = raf.readLine()) != null) {
                String[] nextLineValues = splitString(nextLine, csvSplitter);

                if(nextLineValues[apkInd].equals(apkName) && nextLineValues[cmdInd].equals(command)) {
                    int lineOffset = walkToCol(colName, headers, nextLine);
                    int finalOffset = (int)startOfLine + lineOffset;
                    int targetLen = nextLineValues[colInd].length();
                    StringBuilder properNewVal = new StringBuilder(newVal.substring(0, Math.min(targetLen, newVal.length())));

                    while(properNewVal.length() < targetLen) {
                        properNewVal.append(" ");
                    }

                    if(properNewVal.length() + targetLen <= raf.length()) {
                        long currFp = raf.getFilePointer();
                        raf.seek(finalOffset);
                        raf.write(properNewVal.toString().getBytes());
                        raf.seek(currFp);
                    }
                    else {
                        handleError("Could not edit csv cell due to index out of bounds. Index: " +
                                properNewVal.length() + ". Max index: " + raf.length());
                        return false;
                    }
                }

                startOfLine = raf.getFilePointer();
            }
        } catch(IOException e) {
            handleError(e, "Could not open '" + file.getAbsolutePath() + "' to edit cell at ("
                    + apkName + " - " + command + ", " + colName + ")", stacktrace);
            return false;
        }

        return true;
    }

    private static int walkToCol(String colName, String[] headers, String line) {
        int goalColInd = Arrays.asList(headers).indexOf(colName), currColInd = 0, lineInd = 0;
        if(goalColInd < 0) return -1;

        boolean insideQuotes = false;
        while(currColInd < goalColInd) {
            while(true) {
                if(!insideQuotes && line.charAt(lineInd) == ',') break;
                else if(line.charAt(lineInd) == '"') insideQuotes = !insideQuotes;
                lineInd++;
            }

            lineInd++;
            currColInd++;
        }

        //found the correct column
        //skip any remaining whitespace before the actual value
        while(Character.isWhitespace(line.charAt(lineInd))) lineInd++;

        return lineInd;
    }
}
