package webapp.services;

import common.Tools;
import datacapableapi.model.Event;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import eventsregistryapi.model.EventsRegistryEventStreamResponse;
import solrapi.model.IndexedEvent;
import webapp.controllers.EventsController;
import webscraper.WebClient;

import java.util.List;

@Service
public class RefreshEventsService {

	final static Logger logger = LogManager.getLogger(RefreshEventsService.class);

    private final Boolean erAutoRefresh = Boolean.parseBoolean(Tools.getProperty("eventRegistry.autoRefresh"));
    private final Boolean dcAutoRefresh = Boolean.parseBoolean(Tools.getProperty("dataCapable.autoRefresh"));
    private final Boolean wsAutoRefresh = Boolean.parseBoolean(Tools.getProperty("webScraper.autoRefresh"));

	private final int BASE_TIMEOUT = 15000; //milliseconds
    private final int DATA_CAPABLE_INTERVAL = Integer.parseInt(Tools.getProperty("dataCapable.refreshInterval"));
    private final int EVENT_REGISTRY_INTERVAL = Integer.parseInt(Tools.getProperty("eventRegistry.refreshInterval"));
    private final int WEB_SCRAPER_INTERVAL = Integer.parseInt(Tools.getProperty("webScraper.refreshInterval"));

    private static class RefreshTimer{
        private final int interval;
        private final Tools.CheckedConsumer<EventsController> refresher;
        private final EventsController eventsController;
        private final Boolean autoRefresh;

        private int totalElapsed = 0;

        public RefreshTimer(int interval, Tools.CheckedConsumer<EventsController> refresher, EventsController eventsController, Boolean autoRefresh) {
            this.interval = interval;
            this.refresher = refresher;
            this.eventsController = eventsController;
            this.autoRefresh = autoRefresh;
        }

        public void refresh(int millis) throws Exception {
            if (autoRefresh) {
                totalElapsed += millis;

                if (totalElapsed >= interval) {
                    refresher.apply(eventsController);
                    totalElapsed = 0;
                }
            }
        }
    }

	@Async("processExecutor")
    public void process(EventsController eventsController) throws Exception {
        try {
            RefreshTimer erRefresher = new RefreshTimer(EVENT_REGISTRY_INTERVAL, this::refreshEventRegistry, eventsController, erAutoRefresh);
            RefreshTimer dcRefresher = new RefreshTimer(DATA_CAPABLE_INTERVAL, this::refreshDataCapable, eventsController, dcAutoRefresh);
            RefreshTimer wsRefresher = new RefreshTimer(WEB_SCRAPER_INTERVAL, this::refreshWebScraper, eventsController, wsAutoRefresh);
            while(true) {
                Thread.sleep(BASE_TIMEOUT);

                erRefresher.refresh(BASE_TIMEOUT);
                dcRefresher.refresh(BASE_TIMEOUT);
                wsRefresher.refresh(BASE_TIMEOUT);
            }
        } catch (Exception e) {
            eventsController.refreshEventsProcessExceptionHandler(e);
            throw e;
        }
    }

    private void refreshEventRegistry(EventsController eventsService) throws Exception {
        logger.info("Query Event Registry Minute Stream");
        EventsRegistryEventStreamResponse response = eventsService.getEventRegistryClient().QueryEventRegistryMinuteStream();
        List<IndexedEvent> indexedEvents = eventsService.getEventRegistryClient().PipelineProcessEventStreamResponse(response);
        logger.info("Number Event Registry events returned: " + indexedEvents.size());
    }

    private void refreshDataCapable(EventsController eventsService) throws Exception {
        logger.info("Query Data Capable");
        Event[] response = eventsService.getDataCapableClient().QueryEvents();
        List<IndexedEvent> indexedEventList = eventsService.getDataCapableClient().ProcessEvents(response);
        logger.info("Number Data Capable events returned: " + indexedEventList.size());
    }

    private void refreshWebScraper(EventsController eventsService) {
        logger.info("Start Scraping!!!");
        WebClient client = eventsService.getWebClient();
        int totalScraped = client.queryGoogle(WebClient.QUERY_TIMEFRAME_LAST_HOUR, client::processSearchResult);
        logger.info("Number of events scraped from the web: " + totalScraped);
    }
}
