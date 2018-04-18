package webapp.services;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import webapp.controllers.EventsController;

@Service
public class ModelTrainingService {

    final static Logger logger = LogManager.getLogger(ModelTrainingService.class);

    @Async("processExecutor")
    public void process(EventsController eventsController) {
        double accuracy = eventsController.initiateModelTraining();
        logger.info("Model training complete.  Model accuracy: " + accuracy);
    }
}
