package webapp.services;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import webapp.controllers.EventsController;

@Service
public class ModelTrainingService {

    final static Logger logger = LogManager.getLogger(ModelTrainingService.class);

    private static boolean trainingInProgress = false;

    @Async("processExecutor")
    public void process(EventsController eventsController) {
        if (!trainingInProgress) {
            trainingInProgress = true;
            logger.info("Begin model training.");
            double accuracy = eventsController.initiateModelTraining();
            logger.info("Model training complete.  Model accuracy: " + accuracy);
            trainingInProgress = false;
        }
    }
}
