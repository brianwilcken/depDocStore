package nlp;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.TokenNameFinderEvaluationMonitor;
import opennlp.tools.util.SequenceCodec;

/**
 * Generates a detailed report for the NameFinder.
 * <p>
 * It is possible to use it from an API and access the statistics using the
 * provided getters
 */
public class TokenNameFinderFineGrainedReportListener
        extends FineGrainedReportListener implements TokenNameFinderEvaluationMonitor {

    private SequenceCodec<String> sequenceCodec;
    private List<String> references;
    private List<String> predictions;

    /**
     * Creates a listener that will print to {@link System#err}
     */
    public TokenNameFinderFineGrainedReportListener(SequenceCodec<String> seqCodec) {
        this(seqCodec, System.err);
    }

    /**
     * Creates a listener that prints to a given {@link OutputStream}
     */
    public TokenNameFinderFineGrainedReportListener(SequenceCodec<String> seqCodec, OutputStream outputStream) {
        super(outputStream);
        this.sequenceCodec = seqCodec;
        references = new ArrayList<>();
        predictions = new ArrayList<>();
    }

    // methods inherited from EvaluationMonitor

    public void missclassified(NameSample reference, NameSample prediction) {
        statsAdd(reference, prediction);
    }

    public void correctlyClassified(NameSample reference,
                                    NameSample prediction) {
        statsAdd(reference, prediction);
    }

    private void statsAdd(NameSample reference, NameSample prediction) {
        String[] refTags = sequenceCodec.encode(reference.getNames(), reference.getSentence().length);
        String[] predTags = sequenceCodec.encode(prediction.getNames(), prediction.getSentence().length);

        // we don' want it to compute token frequency, so we pass an array of empty strings instead
        // of tokens
        getStats().add(new String[reference.getSentence().length], refTags, predTags);

        references.add(reference.toString());
        predictions.add(prediction.toString());
    }

    @Override
    public Comparator<String> getMatrixLabelComparator(Map<String, ConfusionMatrixLine> confusionMatrix) {
        return new GroupedMatrixLabelComparator(confusionMatrix);
    }

    @Override
    public Comparator<String> getLabelComparator(Map<String, Counter> map) {
        return new GroupedLabelComparator(map);
    }

    public void writeReport() {
        printGeneralStatistics();
        printTagsErrorRank();
        printGeneralConfusionTable();
    }

    public String getReferenceLines() {
        return getLines(references);
    }

    public String getPredictionLines() {
        return getLines(predictions);
    }

    private String getLines(List<String> lst) {
        StringBuilder bldr = new StringBuilder();
        for (String line : lst) {
            bldr.append(line);
            bldr.append(System.lineSeparator());
        }

        return bldr.toString();
    }
}
