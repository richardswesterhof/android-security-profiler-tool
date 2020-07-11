package parsers;

import org.apache.commons.cli.CommandLine;
import org.ini4j.Ini;
import org.json.JSONObject;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import static util.LoggingUtils.handleError;


public class FlowDroid extends Parser {

    private static final String toolName = "FlowDroid";

    private static final String[] headers = {"sources", "sinks"};

    public FlowDroid(Ini ini, CommandLine commandLine) {
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
        String sources = "", sinks = "";

        for(Map<String, String> record : csvRecords) {
            if(record.get("command").contains("cmd.exe")) continue;

            String toolOutputPath = "";

            try {
                toolOutputPath = reportDir.getAbsolutePath() + "/tool_output/FlowDroid/" +
                        record.get("apk_name") + "_results.xml";

                File inputFile = new File(toolOutputPath);
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(inputFile);
                doc.getDocumentElement().normalize();

                NodeList performanceData = doc.getElementsByTagName("PerformanceData");

                if(performanceData.getLength() == 0) {
                    sources = "0";
                    sinks = "0" ;
                    continue;
                }

                NodeList performanceEntries = performanceData.item(0).getChildNodes();

                for(int i = 0; i < performanceEntries.getLength(); i++) {
                    Node n = performanceEntries.item(i);

                    NamedNodeMap attrs = n.getAttributes();
                    if(n.getNodeName().equals("PerformanceEntry")) {
                        if(attrs.getNamedItem("Name").getTextContent().equals("SourceCount")) {
                            sources = attrs.getNamedItem("Value").getTextContent();
                        }
                        else if(attrs.getNamedItem("Name").getTextContent().equals("SinkCount")) {
                            sinks = attrs.getNamedItem("Value").getTextContent();
                        }
                    }
                }

            } catch(ParserConfigurationException | IOException | SAXException e) {
                handleError(e, toolName + ": Could not parse xml output at '" + toolOutputPath + "'",
                        cli.hasOption("stacktrace"));
            }
        }

        return new String[] {sources, sinks};
    }
}
