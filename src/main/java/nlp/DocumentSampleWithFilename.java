package nlp;

import opennlp.tools.doccat.DocumentSample;

import java.util.*;

public class DocumentSampleWithFilename extends DocumentSample {
    private String filename;
    private String category;

    public DocumentSampleWithFilename(String filename, String category, String[] text) {
        super(category, text);
        this.filename = filename;
        this.category = Objects.requireNonNull(category, "category must not be null");
    }

    public DocumentSampleWithFilename(String filename, String category, String[] text, Map<String, Object> extraInformation) {
        super(category, text, extraInformation);
        this.filename = filename;
    }

    @Override
    public String toString() {

        StringBuilder sampleString = new StringBuilder();

        sampleString.append("\"" + filename + "\"").append(": ");
        sampleString.append("\"" + category + "\"");

        return sampleString.toString();
    }

    public String getFilename() {
        return filename;
    }
}
