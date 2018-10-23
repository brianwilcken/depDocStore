package webapp.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import webapp.components.ApplicationContextProvider;

import java.util.concurrent.BlockingQueue;

@Service
public class WorkExecutorHeartbeatService {
    final static Logger logger = LogManager.getLogger(WorkExecutorHeartbeatService.class);

    @Async("processExecutor")
    public void process(String taskExecutor, int queueThreshold, int dequeueTarget) {
        ThreadPoolTaskExecutor processExecutor = ApplicationContextProvider.getApplicationContext().getBean(taskExecutor, ThreadPoolTaskExecutor.class);
        BlockingQueue<Runnable> queue = processExecutor.getThreadPoolExecutor().getQueue();

        while(true) {
            try {
                Thread.sleep(100);
                if (queue.size() > queueThreshold) {
                    logger.info(taskExecutor + " work queue has exceeded threshold capacity of " + queueThreshold + " queued tasks!  Dequeuing some tasks for execution...");
                    for (int i = 1; i <= dequeueTarget; i++) {
                        queue.take().run();
                    }
                }
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
