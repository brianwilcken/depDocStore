package webapp.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import webapp.controllers.DocumentsController;

@Service
public class NERModelTrainingService {
    final static Logger logger = LogManager.getLogger(NERModelTrainingService.class);

    private static boolean trainingInProgress = false;

    @Async("processExecutor")
    public void process(DocumentsController documentsController, String category) {
        if (!trainingInProgress) {
            trainingInProgress = true;
            logger.info("Begin model training.");
            documentsController.initiateNERModelTraining(category);
            logger.info("Model training complete.");
            trainingInProgress = false;
        }
    }
}
