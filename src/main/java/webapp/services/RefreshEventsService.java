package webapp.services;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import eventsregistryapi.model.EventsRegistryEventStreamResponse;
import webapp.controllers.EventsController;

@Service
public class RefreshEventsService {

	final static Logger logger = LogManager.getLogger(RefreshEventsService.class);
	
	@Async("processExecutor")
    public void process(EventsController eventsService) throws Exception {
		while(true) {
			try {
				Thread.sleep(60000);
				logger.info("Query Event Registry Minute Stream");
				EventsRegistryEventStreamResponse response = eventsService.getEventRegistryClient().QueryEventRegistryMinuteStream();
				logger.info("Number events returned: " + response.getRecentActivityEvents().getActivity().length);
				eventsService.getEventRegistryClient().PipelineProcessEventStreamResponse(response);
			} catch (Exception e) {
				eventsService.refreshEventsProcessExceptionHandler(e);
				throw e;
			}
		}
    }
}
