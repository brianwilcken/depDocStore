package webapp.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import webapp.controllers.DocumentsController;

@Service
public class DoccatModelTrainingService {
    final static Logger logger = LogManager.getLogger(DoccatModelTrainingService.class);

    private static boolean trainingInProgress = false;

    @Async("processExecutor")
    public void process(DocumentsController documentsController) {
        if (!trainingInProgress) {
            trainingInProgress = true;
            logger.info("Begin model training.");
            documentsController.initiateDoccatModelTraining();
            logger.info("Model training complete.");
            trainingInProgress = false;
        }
    }
}
