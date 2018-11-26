package nlp;

import java.time.Duration;
import java.time.Instant;

public abstract class StanfordNLPTask {
    protected boolean activated;
    private boolean cancelled;
    protected boolean done;
    protected int lineStart;
    protected Instant startTime;
    protected Thread myThread;

    public boolean isActivated() {
        return activated;
    }

    public boolean isDone() {
        return done;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        myThread.stop();
        cancelled = true;
    }

    public int getLineStart() {
        return lineStart;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public boolean hasElapsed(long sec) {
        if (activated) {
            Instant current = Instant.now();
            Duration duration = Duration.between(startTime, current);
            return duration.getSeconds() >= sec;
        } else {
            return false;
        }
    }

    public Thread getMyThread() {
        return myThread;
    }

}
