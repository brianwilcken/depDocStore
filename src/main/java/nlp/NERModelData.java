package nlp;

import common.FacilityTypes;
import common.Tools;
import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.stat.descriptive.moment.Mean;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NERModelData  {
    private String modelVersion;
    private String modelDate;
    private int numModelSent;
    private int numSent;
    private int minSentSize;
    private int maxSentSize;
    private Mean avgSentSize = new Mean();
    private int tagCnt;
    private Mean entityAcc = new Mean();
    private Mean entityFM = new Mean();
    private StringBuilder detailedAccuracy = new StringBuilder();
    private StringBuilder confusionMatrix = new StringBuilder();
    private StringBuilder testReport = new StringBuilder();

    private Pattern numPattern = Pattern.compile("\\d*\\.?\\d+%?");

    private String SECTION_TERMINATOR = "<-end>";

    public static void main(String[] args) {
        try {
            File modelFolder = new File("E:\\apache-tomcat-9.0.6\\bin\\data\\ner\\Water\\74");
            NERModelData data = new NERModelData(modelFolder, "Water");
            data.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public NERModelData(File modelFolder, String category) throws IOException {
        final List<String> facilityTypes = FacilityTypes.dictionary.get(category);
        this.modelVersion = modelFolder.getName();
        for (String facilityType : facilityTypes) {
            String rprtFilePath = modelFolder.getPath() + "/" + facilityType + "_report.txt";
            if (new File(rprtFilePath).exists()) {
                File rprtFile = new File(rprtFilePath);
                long lastModified = rprtFile.lastModified();
                modelDate = Tools.getFormattedDateTimeString(Instant.ofEpochMilli(lastModified));
                String rprt = FileUtils.readFileToString(rprtFile, Charset.defaultCharset());
                testReport.append(rprt);
                testReport.append(System.lineSeparator());
                testReport.append(System.lineSeparator());
                testReport.append(System.lineSeparator());
                consumeReport(rprt);
            }
        }
    }

    private void consumeReport(String rprt) {
        String[] rprtLines = rprt.split("\n");

        int lineNum;
        for (lineNum = 0; lineNum < rprtLines.length; lineNum++) {
            String line = rprtLines[lineNum];

            if (line.contains("Number of sentences")) {
                numSent += getNumberFromLine(line);
            } else if (line.contains("Min sentence size")) {
                minSentSize = Math.min(getNumberFromLine(line).intValue(), minSentSize);
            } else if (line.contains("Max sentence size")) {
                maxSentSize = Math.max(getNumberFromLine(line).intValue(), maxSentSize);
            } else if (line.contains("Average sentence size")) {
                avgSentSize.increment(getNumberFromLine(line));
            } else if (line.contains("Tags count")) {
                tagCnt += getNumberFromLine(line).intValue();
            } else if (line.contains("Entity Accuracy")) {
                entityAcc.increment(getNumberFromLine(line));
            } else if (line.contains("Entity F-Measure")) {
                entityFM.increment(getNumberFromLine(line));
            }

            if (line.contains(SECTION_TERMINATOR)) {
                break;
            }
        }

        StringBuilder detailedAccuracyBldr = new StringBuilder();
        try {
            String line = rprtLines[++lineNum];
            do {
                detailedAccuracyBldr.append(line + System.lineSeparator());
                line = rprtLines[++lineNum];
            } while (!line.contains(SECTION_TERMINATOR));
            detailedAccuracy.append(detailedAccuracyBldr.toString());
            detailedAccuracy.append(System.lineSeparator());

            StringBuilder confusionMatrixBldr = new StringBuilder();
            line = rprtLines[++lineNum];
            do {
                confusionMatrixBldr.append(line + System.lineSeparator());
                line = rprtLines[++lineNum];
            } while (!line.contains(SECTION_TERMINATOR));
            confusionMatrix.append(confusionMatrixBldr.toString());
            confusionMatrix.append(System.lineSeparator());

            do {
                line = rprtLines[lineNum++];
                if (line.contains("Number of model sentences:")) {
                    numModelSent += getNumberFromLine(line).intValue();
                }
            } while (lineNum < rprtLines.length);
        } catch (Exception e) {
            //do nothing here.... just want to make sure as much report data is written as possible
        }
    }

    private Double getNumberFromLine(String line) {
        Matcher numMatcher = numPattern.matcher(line);
        if (numMatcher.find()) {
            Double val = Double.parseDouble(line.substring(numMatcher.start(), numMatcher.end()).replace("%", ""));
            val = line.contains(" -") ? 0.0 : val;
            return val;
        } else {
            return 0.0;
        }
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getModelDate() {
        return modelDate;
    }

    public String getNumModelSentences() {
        return Integer.toString(numModelSent);
    }

    public String getNumSentences() {
        return Integer.toString(numSent);
    }

    public String getMinSentenceSize() {
        return Integer.toString(minSentSize);
    }

    public String getMaxSentenceSize() {
        return Integer.toString(maxSentSize);
    }

    public String getAvgSentenceSize() {
        return Double.toString(avgSentSize.getResult());
    }

    public String getTagCount() {
        return Integer.toString(tagCnt);
    }

    public String getEntityAccuracy() {
        return Double.toString(entityAcc.getResult());
    }

    public String getEntityFMeasure() {
        return Double.toString(entityFM.getResult());
    }

    public String getDetailedAccuracy() {
        return detailedAccuracy.toString();
    }

    public String getConfusionMatrix() {
        return confusionMatrix.toString();
    }

    public String getTestReport() {
        return testReport.toString();
    }
}
