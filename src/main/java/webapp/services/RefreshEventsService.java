package webapp.services;

import datacapableapi.model.Event;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import eventsregistryapi.model.EventsRegistryEventStreamResponse;
import solrapi.model.IndexedEvent;
import webapp.controllers.EventsController;

import java.util.List;

@Service
public class RefreshEventsService {

	final static Logger logger = LogManager.getLogger(RefreshEventsService.class);

	private final int BASE_TIMEOUT = 15000; //milliseconds
    private final int EVENT_REGISTRY_INTERVAL = 60000;

	@Async("processExecutor")
    public void process(EventsController eventsService) throws Exception {
        //refreshEventRegistry(eventsService);

        int currentMillis = 0;
		while(true) {
			try {
                Thread.sleep(BASE_TIMEOUT);
//                currentMillis += BASE_TIMEOUT;
//                if (currentMillis >= EVENT_REGISTRY_INTERVAL) {
//                    refreshEventRegistry(eventsService);
//                    currentMillis = 0;
//                }
                refreshDataCapable(eventsService);
			} catch (Exception e) {
				eventsService.refreshEventsProcessExceptionHandler(e);
				throw e;
			}
		}
    }

    private void refreshEventRegistry(EventsController eventsService) throws Exception {
        logger.info("Query Event Registry Minute Stream");
        EventsRegistryEventStreamResponse response = eventsService.getEventRegistryClient().QueryEventRegistryMinuteStream();
        logger.info("Number Event Registry events returned: " + response.getRecentActivityEvents().getActivity().length);
        eventsService.getEventRegistryClient().PipelineProcessEventStreamResponse(response);
    }

    private void refreshDataCapable(EventsController eventsService) throws Exception {
        logger.info("Query Data Capable");
        Event[] response = eventsService.getDataCapableClient().QueryEvents();
        List<IndexedEvent> indexedEventList = eventsService.getDataCapableClient().ProcessEvents(response);
        logger.info("Number Data Capable events returned: " + indexedEventList.size());
    }
}
