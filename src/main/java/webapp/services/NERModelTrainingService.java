package webapp.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import webapp.controllers.DocumentsController;

import java.io.IOException;
import java.util.List;

@Service
public class NERModelTrainingService {
    final static Logger logger = LogManager.getLogger(NERModelTrainingService.class);

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
                logger.info("Begin model training.");
                documentsController.initiateNERModelTraining(categories);
                logger.info("Model training complete.");
                trainingInProgress = false;
            }
        } catch (Exception e) {
            trainingInProgress = false;
            throw e;
        }
    }
}
