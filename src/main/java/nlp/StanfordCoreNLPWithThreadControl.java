package nlp;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.RuntimeInterruptedException;

import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

public class StanfordCoreNLPWithThreadControl extends StanfordCoreNLP {

    public static final Semaphore semaphore;

    static {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores > 2) {
            int usedCores = cores / 2;
            semaphore = new Semaphore(usedCores);
        } else {
            semaphore = new Semaphore(1);
        }
    }

    public StanfordCoreNLPWithThreadControl(Properties props) {
        super(props);
    }

    public Thread annotateWithThreadControl(final Annotation annotation, final Consumer<Instant> activator, final Consumer<Annotation> callback){

        AnnotationTask task = new AnnotationTask(annotation, activator, callback);
        Thread thread = new Thread(task);
        thread.start();

        return thread;
    }

    private class AnnotationTask implements Runnable {

        private Annotation annotation;
        private Consumer<Annotation> callback;
        private Consumer<Instant> activator;

        public AnnotationTask(final Annotation annotation, final Consumer<Instant> activator, final Consumer<Annotation> callback) {
            this.annotation = annotation;
            this.activator = activator;
            this.callback = callback;
        }

        @Override
        public void run() {
            try {
                semaphore.acquire();
                activator.accept(Instant.now());
                annotate(annotation);
            } catch (Throwable t) {
                annotation.set(CoreAnnotations.ExceptionAnnotation.class, t);
            } finally {
                semaphore.release();
            }
            if (callback != null) {
                callback.accept(annotation);
            }
        }
    }

}
