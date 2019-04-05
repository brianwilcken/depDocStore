package nlp;

public class NERModelEvaluation {
    private String stats;
    private String refLines;
    private String predLines;

    public NERModelEvaluation(String stats, String refLines, String predLines) {
        this.stats = stats;
        this.refLines = refLines;
        this.predLines = predLines;
    }

    public String getStats() {
        return stats;
    }

    public String getRefLines() {
        return refLines;
    }

    public String getPredLines() {
        return predLines;
    }
}
