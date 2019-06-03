package nlp;

import java.util.Arrays;
import java.util.List;

public class TextChunkTopic {
    private int startLine;
    private int endLine;
    private String chunkText;
    private List<String> ldaCategory;

    public TextChunkTopic(int startLine, int endLine, String chunkText, List<String> ldaCategory) {
        this.startLine = startLine;
        this.endLine = endLine;
        this.chunkText = chunkText;
        this.ldaCategory = ldaCategory;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public String getChunkText() {
        return chunkText;
    }

    public void setChunkText(String chunkText) {
        this.chunkText = chunkText;
    }

    public List<String> getLdaCategory() {
        return ldaCategory;
    }

    public void setLdaCategory(List<String> ldaCategory) {
        this.ldaCategory = ldaCategory;
    }

    @Override
    public String toString() {
        String out = "start: " + startLine + " end: " + endLine + " categories: " + ldaCategory.stream().reduce((c, n) -> c + ", " + n).orElse("");

        return out;
    }
}
