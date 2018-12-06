package webapp.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import webapp.controllers.DocumentsController;

import java.io.IOException;

@Service
public class NERModelTrainingService {
    final static Logger logger = LogManager.getLogger(NERModelTrainingService.class);

    private static boolean trainingInProgress = false;

    @Async("processExecutor")
    public void processAsync(DocumentsController documentsController, String category) {
        try {
            process(documentsController, category);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public void process(DocumentsController documentsController, String category) throws IOException {
        try {
            if (!trainingInProgress) {
                trainingInProgress = true;
                logger.info("Begin model training.");
                documentsController.initiateNERModelTraining(category);
                logger.info("Model training complete.");
                trainingInProgress = false;
            }
        } catch (IOException e) {
            trainingInProgress = false;
            throw e;
        }
    }
}
