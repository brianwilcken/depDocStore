package webapp.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import webapp.controllers.DocumentsController;

import java.util.List;

@Service
public class W2VModelTrainingService {
    final static Logger logger = LogManager.getLogger(W2VModelTrainingService.class);

    private static boolean trainingInProgress = false;

    @Async("processExecutor")
    public void processAsync(DocumentsController documentsController, List<String> categories) {
        try {
            process(documentsController, categories);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    public void process(DocumentsController documentsController, List<String> categories) throws Exception {
        try {
            if (!trainingInProgress) {
                trainingInProgress = true;
                logger.info("Begin W2V model training.");
                documentsController.initiateW2VModelTraining(categories);
                logger.info("W2V Model training complete.");
                trainingInProgress = false;
            }
        } catch (Exception e) {
            trainingInProgress = false;
            throw e;
        }
    }
}
