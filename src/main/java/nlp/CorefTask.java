package nlp;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;

import java.util.ArrayList;
import java.util.Collection;

public class CorefTask {
    private StanfordCoreNLP pipeline;
    private Collection<CorefChain> corefs;
    private boolean isDone;
    private int lineStart;

    public CorefTask(StanfordCoreNLP pipeline, int lineStart) {
        this.pipeline = pipeline;
        corefs = null;
        isDone = false;
        this.lineStart = lineStart;
    }

    public void resolve(String text) {
        Annotation annotation = new Annotation(text);
        pipeline.annotate(annotation, this::processAnnotation);
    }

    private void processAnnotation(Annotation annotation) {
        isDone = true;
        try {
            corefs = annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class).values();
        } catch (Exception e) {
            corefs = new ArrayList<>();
        }
    }

    public Collection<CorefChain> getCorefs() {
        return corefs;
    }

    public boolean isDone() {
        return isDone;
    }

    public int getLineStart() {
        return lineStart;
    }
}
