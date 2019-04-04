package nlp;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import common.Tools;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NERModelData  {
    private String modelVersion;
    private String modelDate;
    private String numModelSentences;
    private String numSentences;
    private String minSentenceSize;
    private String maxSentenceSize;
    private String avgSentenceSize;
    private String tagCount;
    private String entityAccuracy;
    private String entityFMeasure;
    private String detailedAccuracy;
    private String confusionMatrix;
    private String testReport;

    private Pattern numPattern = Pattern.compile("\\d*\\.?\\d+%?");

    private String SECTION_TERMINATOR = "<-end>";

    public static void main(String[] args) {
        try {
            File modelFolder = new File("E:\\apache-tomcat-9.0.6\\bin\\data\\ner\\Water\\1");
            NERModelData data = new NERModelData(modelFolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public NERModelData(File modelFolder) throws IOException {
        this.modelVersion = modelFolder.getName();
        String rprtFilePath = modelFolder.getPath() + "/report.txt";
        File rprtFile = new File(rprtFilePath);
        long lastModified = rprtFile.lastModified();
        modelDate = Tools.getFormattedDateTimeString(Instant.ofEpochMilli(lastModified));
        String rprt = Files.toString(rprtFile, Charsets.UTF_8);
        testReport = rprt;
        consumeReport(rprt);
    }

    private void consumeReport(String rprt) {
        String[] rprtLines = rprt.split("\n");

        int lineNum;
        for (lineNum = 0; lineNum < rprtLines.length; lineNum++) {
            String line = rprtLines[lineNum];

            if (line.contains("Number of sentences")) {
                numSentences = getNumberFromLine(line);
            } else if (line.contains("Min sentence size")) {
                minSentenceSize = getNumberFromLine(line);
            } else if (line.contains("Max sentence size")) {
                maxSentenceSize = getNumberFromLine(line);
            } else if (line.contains("Average sentence size")) {
                avgSentenceSize = getNumberFromLine(line);
            } else if (line.contains("Tags count")) {
                tagCount = getNumberFromLine(line);
            } else if (line.contains("Entity Accuracy")) {
                entityAccuracy = getNumberFromLine(line);
            } else if (line.contains("Entity F-Measure")) {
                entityFMeasure = getNumberFromLine(line);
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
            detailedAccuracy = detailedAccuracyBldr.toString();

            StringBuilder confusionMatrixBldr = new StringBuilder();
            line = rprtLines[++lineNum];
            do {
                confusionMatrixBldr.append(line + System.lineSeparator());
                line = rprtLines[++lineNum];
            } while (!line.contains(SECTION_TERMINATOR));
            confusionMatrix = confusionMatrixBldr.toString();

            do {
                line = rprtLines[lineNum++];
                if (line.contains("Number of model sentences:")) {
                    numModelSentences = getNumberFromLine(line);
                }
            } while (lineNum < rprtLines.length);
        } catch (Exception e) {
            //do nothing here.... just want to make sure as much report data is written as possible
        }
    }

    private String getNumberFromLine(String line) {
        Matcher numMatcher = numPattern.matcher(line);
        if (numMatcher.find()) {
            return line.substring(numMatcher.start(), numMatcher.end());
        } else {
            return null;
        }
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getModelDate() {
        return modelDate;
    }

    public String getNumModelSentences() {
        return numModelSentences;
    }

    public String getNumSentences() {
        return numSentences;
    }

    public String getMinSentenceSize() {
        return minSentenceSize;
    }

    public String getMaxSentenceSize() {
        return maxSentenceSize;
    }

    public String getAvgSentenceSize() {
        return avgSentenceSize;
    }

    public String getTagCount() {
        return tagCount;
    }

    public String getEntityAccuracy() {
        return entityAccuracy;
    }

    public String getEntityFMeasure() {
        return entityFMeasure;
    }

    public String getDetailedAccuracy() {
        return detailedAccuracy;
    }

    public String getConfusionMatrix() {
        return confusionMatrix;
    }

    public String getTestReport() {
        return testReport;
    }
}
