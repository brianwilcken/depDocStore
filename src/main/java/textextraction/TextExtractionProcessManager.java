package textextraction;

import java.util.concurrent.Semaphore;

public class TextExtractionProcessManager {
    public static final Semaphore semaphore;

    static {
        semaphore = new Semaphore(16);
    }

    public Thread startProcess(final Runnable runExtraction) {
        ManagedTask task = new ManagedTask(runExtraction);
        Thread thread = new Thread(task);
        thread.start();

        return thread;
    }

    private class ManagedTask implements Runnable {

        private Runnable runExtraction;
        public ManagedTask(Runnable runExtraction) {
            this.runExtraction = runExtraction;
        }

        @Override
        public void run() {
            try {
                semaphore.acquire();
                runExtraction.run();
            } catch (Throwable t) {

            } finally {
                semaphore.release();
            }
        }
    }
}
