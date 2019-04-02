package textextraction;

import textextraction.ProcessedPage;

import java.util.ArrayList;
import java.util.List;

public class ProcessedDocument {
    private List<ProcessedPage> schematics;
    private String extractedText;

    public void cleanup() {
        for (ProcessedPage processedPage : schematics) {
            processedPage.cleanup();
        }
    }

    public ProcessedDocument() {
        schematics = new ArrayList<>();
    }

    public List<ProcessedPage> getSchematics() {
        return schematics;
    }

    public void setSchematics(List<ProcessedPage> schematics) {
        this.schematics = schematics;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }
}
