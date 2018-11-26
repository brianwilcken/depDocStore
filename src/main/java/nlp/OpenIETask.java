package nlp;

import edu.stanford.nlp.pipeline.Annotation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;

public class OpenIETask extends StanfordNLPTask {
    private final static Logger logger = LogManager.getLogger(OpenIETask.class);
    private StanfordCoreNLPWithThreadControl pipeline;
    private Annotation annotation;

    public OpenIETask(StanfordCoreNLPWithThreadControl pipeline, int lineStart) {
        this.pipeline = pipeline;
        this.lineStart = lineStart;
        annotation = null;
        activated = false;
    }

    public void enqueue(String text) {
        Annotation annotation = new Annotation(text);
        myThread = pipeline.annotateWithThreadControl(annotation, this::activate, this::evaluate);
    }

    public void evaluate(Annotation annotation) {
        done = true;
        logger.info("OpenIE: finished annotations for " + lineStart);
        this.annotation = annotation;
    }

    public void activate(Instant start) {
        logger.info("OpenIE: begin resolving annotations for " + lineStart);
        this.startTime = start;
        activated = true;
    }

    public Annotation getAnnotations() {
        return annotation;
    }
}
