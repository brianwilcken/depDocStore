package webapp.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import solrapi.model.IndexedObject;
import webapp.controllers.DocumentsController;

import java.io.IOException;
import java.util.concurrent.Future;

@Service
public class DoccatModelTrainingService {
    final static Logger logger = LogManager.getLogger(DoccatModelTrainingService.class);

    private static boolean trainingInProgress = false;

    @Async("processExecutor")
    public Future<String> processAsync(DocumentsController documentsController) {
        try {
            String report = process(documentsController);

            return new AsyncResult<>(report);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return new AsyncResult<>("");
        }
    }

    public String process(DocumentsController documentsController) throws IOException {
        if (!trainingInProgress) {
            trainingInProgress = true;
            logger.info("Begin model training.");
            String report = documentsController.initiateDoccatModelTraining();
            logger.info("Model training complete.");
            trainingInProgress = false;

            return report;
        }
        return "";
    }
}
