package nlp;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.pipeline.Annotation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

public class CorefTask extends StanfordNLPTask {
    private final static Logger logger = LogManager.getLogger(CorefTask.class);
    private StanfordCoreNLPWithThreadControl pipeline;
    private Collection<CorefChain> corefChains;

    public CorefTask(StanfordCoreNLPWithThreadControl pipeline, int lineStart) {
        this.pipeline = pipeline;
        this.lineStart = lineStart;
        corefChains = null;
        activated = false;
    }

    public void enqueue(String text) {
        Annotation annotation = new Annotation(text);
        myThread = pipeline.annotateWithThreadControl(annotation, this::activate, this::evaluateCorefs);
    }

    public void evaluateCorefs(Annotation annotation) {
        done = true;
        Map<Integer, CorefChain> coreMap = annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
        if (coreMap != null) {
            corefChains = coreMap.values();
            logger.info("Coref: finished resolving coreferences for " + lineStart);
        }
    }

    public void activate(Instant start) {
        logger.info("Coref: begin resolving coreferences for " + lineStart);
        this.startTime = start;
        activated = true;
    }

    public Collection<CorefChain> getCorefChains() {
        return corefChains;
    }
}
