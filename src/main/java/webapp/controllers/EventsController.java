package webapp.controllers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.gson.Gson;

import datacapableapi.DataCapableClient;
import eventsregistryapi.model.*;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.solr.client.solrj.SolrQuery.SortClause;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import common.Tools;
import eventsregistryapi.EventRegistryClient;
import nlp.EventCategorizer;
import solrapi.SolrClient;
import solrapi.SolrConstants;
import solrapi.model.IndexedEvent;
import solrapi.model.IndexedEventSource;
import solrapi.model.IndexedEventsQueryParams;
import webapp.models.JsonResponse;
import webapp.services.ModelTrainingService;
import webapp.services.RefreshEventsService;
import webscraper.WebClient;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

@CrossOrigin
@RestController
@RequestMapping("/api/events")
public class EventsController {
	private EventRegistryClient eventRegistryClient;
	private DataCapableClient dataCapableClient;
	private WebClient webClient;
	private SolrClient solrClient;
	private EventCategorizer categorizer;
	private Gson gson;
	private List<Exception> exceptions;
	
	final static Logger logger = LogManager.getLogger(EventsController.class);
	
	@Autowired
	private RefreshEventsService refreshEventsService;

	@Autowired
	private ModelTrainingService modelTrainingService;

	@Autowired
	private HttpServletRequest context;

	public static void main(String[] args) {
		EventsController ctrl = new EventsController();
		try {
			//ctrl.dumpEventDataToFile("data/events.json", "eventState:*", null, 100);
			//ctrl.dumpSourceDataToFile("data/sources.json", "eventId:*", null, 100);
			ctrl.updateIndexedEventsByFile("data/events.json");
			//ctrl.updateIndexedEventSourcesByFile("data/sources.json");
		} catch (SolrServerException e) {
			e.printStackTrace();
		}
	}

	public EventsController() {
		eventRegistryClient = new EventRegistryClient();
		dataCapableClient = new DataCapableClient();
		webClient = new WebClient();
		solrClient = new SolrClient(Tools.getProperty("solr.url"));
		categorizer = new EventCategorizer(solrClient);
		gson = new Gson();
		exceptions = new ArrayList<>();
	}
	
	public EventRegistryClient getEventRegistryClient() {
		return eventRegistryClient;
	}

	public DataCapableClient getDataCapableClient() {
		return dataCapableClient;
	}

	public WebClient getWebClient() { return webClient; }

	@PostConstruct
	public void initRefreshEventsProcess() {
		try {
			logger.info("Initializing events refresh process");
			refreshEventsService.process(this);
		} catch (Exception e) {}
	}
	
	public void refreshEventsProcessExceptionHandler(Exception e) {
		Tools.getExceptions().add(e);
		try {
			refreshEventsService.process(this);
		} catch (Exception e1) {}
	}
	
	//Public REST API

	//Get events containing similar text in the title/description
	@RequestMapping(value="/equivalents", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonResponse> findSimilarDocuments(@RequestParam String searchText) {
		try {
			logger.info(context.getRemoteAddr() + " -> " + "Finding similar events");
			List<IndexedEvent> docs = solrClient.FindSimilarEvents(searchText);
			return ResponseEntity.ok().body(Tools.formJsonResponse(docs));
		} catch (Exception e) {
			logger.error(context.getRemoteAddr() + " -> " + e);
			Tools.getExceptions().add(e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
		}
	}

	//Get events using query parameters
	@RequestMapping(method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonResponse> getEvents(IndexedEventsQueryParams params) {
		logger.info(context.getRemoteAddr() + " -> " + "In getEvents method");
		try {
			SortClause sort = new SortClause("lastUpdated", "desc");
			List<IndexedEvent> events = solrClient.QueryIndexedDocuments(IndexedEvent.class, params.getQuery(), params.getQueryRows(), params.getQueryStart(), sort, params.getFilterQueries());
			JsonResponse response;
			if (params.getIncludeDeletedIds() != null && params.getIncludeDeletedIds()) {
				logger.info(context.getRemoteAddr() + " -> " + "Including deleted ids");
				//Obtain set of deleted event Ids
				List<String> deletedEventIds = events.stream()
						.filter(p -> p.getEventState() != null && p.getEventState().compareTo(SolrConstants.Events.EVENT_STATE_DELETED) == 0)
						.map(p -> p.getId())
						.collect(Collectors.toList());
				logger.info(context.getRemoteAddr() + " -> " + "Total number of deleted events: " + deletedEventIds.size());

				//Filter to produce list of non-deleted events
				List<IndexedEvent> nonDeletedEvents = events.stream().filter(p -> p.getEventState() != null &&
						p.getEventState().compareTo(SolrConstants.Events.EVENT_STATE_DELETED) != 0).collect(Collectors.toList());

				logger.info(context.getRemoteAddr() + " -> " + "Total number of non-deleted events: " + nonDeletedEvents.size());
				//form response with deleted event ids
				response = Tools.formJsonResponse(nonDeletedEvents, params.getQueryTimeStamp());
				response.setDeletedEvents(deletedEventIds);
			} else {
				response = Tools.formJsonResponse(events, params.getQueryTimeStamp());
			}

			logger.info(context.getRemoteAddr() + " -> " + "Returning events");
			return ResponseEntity.ok().body(response);
		} catch (Exception e) {
			logger.error(context.getRemoteAddr() + " -> " + e);
			Tools.getExceptions().add(e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null, params.getQueryTimeStamp()));
		}
	}

	//Delete specific event
	@RequestMapping(value="/{id}", method=RequestMethod.DELETE, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonResponse> deleteEventById(@PathVariable(name="id") String id) {
		try {
			logger.info(context.getRemoteAddr() + " -> " + "Trying to delete event with id: " + id);
			List<IndexedEvent> events = solrClient.QueryIndexedDocuments(IndexedEvent.class, "id:" + id, 1, 0, null);
			if (!events.isEmpty()) {
				IndexedEvent event = events.get(0);
				event.setEventState(SolrConstants.Events.EVENT_STATE_DELETED);
				event.updateLastUpdatedDate();
				solrClient.indexDocuments(events);
				logger.info(context.getRemoteAddr() + " -> " + "Deleted event with id: " + id);
				return ResponseEntity.ok().body(Tools.formJsonResponse(event));
			} else {
				logger.info(context.getRemoteAddr() + " -> " + "Failed to delete event with id: " + id);
				return ResponseEntity.notFound().build();
			}
		} catch (Exception e) {
			logger.error(context.getRemoteAddr() + " -> " + e);
			Tools.getExceptions().add(e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
		}
	}

	//Create new event
	@RequestMapping(method=RequestMethod.POST, consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonResponse> createEvent(@RequestBody IndexedEvent event) {
		try {
			logger.info(context.getRemoteAddr() + " -> " + "Creating new event");
			event.initId();
			if (event.getUri() == null || event.getUri().isEmpty()) {
				event.setUri("N/A");
			} else if (solrClient.IsDocumentAlreadyIndexed(event.getUri())){
				return updateEvent(event.getId(), event);
			}
			event.setFeedType(SolrConstants.Events.FEED_TYPE_AUTHORITATIVE);
			event.setCategorizationState(SolrConstants.Events.CATEGORIZATION_STATE_USER_UPDATED);
			event.setEventState(SolrConstants.Events.EVENT_STATE_NEW);
			event.updateLastUpdatedDate();
			List<IndexedEvent> coll = new ArrayList<>();
			coll.add(event);
			solrClient.indexDocuments(coll);
			logger.info(context.getRemoteAddr() + " -> " + "New event has been indexed with id: " + event.getId());
			indexEventSources(event);
			return ResponseEntity.ok().body(Tools.formJsonResponse(event, event.getLastUpdated()));
		} catch (Exception e) {
			logger.error(context.getRemoteAddr() + " -> " + e);
			Tools.getExceptions().add(e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
		}
	}

	private void indexEventSources(IndexedEvent event) throws SolrServerException {
		if (event.getSources().size() > 0) {
			logger.info(context.getRemoteAddr() + " -> " + "Indexing sources for event with id: " + event.getId());
			//delete previous sources
			solrClient.deleteDocuments("eventId:" + event.getId());

			//index new sources
			for (IndexedEventSource source : event.getSources()) {
				source.setEventId(event.getId());
				source.initId();
				source.setUri("N/A");
			}
			solrClient.indexDocuments(event.getSources());
			logger.info(context.getRemoteAddr() + " -> " + event.getSources().size() + " sources indexed for event with id: " + event.getId());
		}
	}

	//update specific event with user input
	@RequestMapping(value="{id}", method=RequestMethod.PUT, consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonResponse> updateEvent(@PathVariable(name="id") String id, @RequestBody IndexedEvent updEvent) {
		try {
			logger.info(context.getRemoteAddr() + " -> " + "Updating event with id: " + id);
			List<IndexedEvent> events = solrClient.QueryIndexedDocuments(IndexedEvent.class, "id:" + id, 1, 0, null);
			if (!events.isEmpty()) {
				logger.info(context.getRemoteAddr() + " -> " + "Event exists... proceeding with update");
				IndexedEvent event = events.get(0);
				//A conditional update occurs when an automated job posts events to the service.
				//Whether or not to update the last updated date on the indexed event is determined through logic.
				if (!updEvent.getConditionalUpdate() || updEvent.compareTo(event) != 0) {
					event.updateLastUpdatedDate();
				}
				event.setLatitude(updEvent.getLatitude());
				event.setLongitude(updEvent.getLongitude());
				event.setLocation(updEvent.getLocation());
				if (!Strings.isNullOrEmpty(updEvent.getEventState())) {
					//The only valid state transition communicated directly from the client is "Watched"
					event.setEventState(updEvent.getEventState());
				} else {
					if (!updEvent.getConditionalUpdate()) {
						//If the client has not provided state information on an event then the event transitions to the reviewed state
						if (event.getEventState().compareTo(SolrConstants.Events.EVENT_STATE_NEW) == 0 ||
								event.getEventState().compareTo(SolrConstants.Events.EVENT_STATE_WATCHED) == 0) {
							event.setEventState(SolrConstants.Events.EVENT_STATE_REVIEWED);
						}
					}
				}
				event.setSummary(updEvent.getSummary());
                if (!updEvent.getConditionalUpdate()) {
                    event.setCategory(updEvent.getCategory());
                    event.setTitle(updEvent.getTitle());
                    event.setDashboard(updEvent.getDashboard());
                    event.setCategorizationState(SolrConstants.Events.CATEGORIZATION_STATE_USER_UPDATED);
                    event.setFeatureIds(updEvent.getFeatureIds());
                }
				List<IndexedEvent> coll = new ArrayList<>();
				coll.add(event);
				solrClient.indexDocuments(coll);
                if (!updEvent.getConditionalUpdate()) {
                    event.setSources(updEvent.getSources());
                    indexEventSources(event);
                    logger.info(context.getRemoteAddr() + " -> " + "Updated event indexed... proceeding with model training");
                    modelTrainingService.process(this);
                }
				return ResponseEntity.ok().body(Tools.formJsonResponse(event, event.getLastUpdated()));
			} else {
				return ResponseEntity.notFound().build();
			}
		} catch (Exception e) {
			logger.error(context.getRemoteAddr() + " -> " + e);
			Tools.getExceptions().add(e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
		}
	}

	//Get sources for specific event
	@RequestMapping(value="/{id}/sources", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonResponse> getEventSources(@PathVariable(name="id") String id) {
		logger.info(context.getRemoteAddr() + " -> " + "Getting indexed sources for event with id: " + id);
		try {
			List<IndexedEvent> events = solrClient.QueryIndexedDocuments(IndexedEvent.class, "id:" + id, 1, 0, null);
			if (!events.isEmpty()) {
				logger.info(context.getRemoteAddr() + " -> " + "Event exists... proceeding to lookup sources");
				List<IndexedEventSource> sources = solrClient.QueryIndexedDocuments(IndexedEventSource.class, "eventId:" + id, 10000, 0, null);
				logger.info(context.getRemoteAddr() + " -> " + "Number of sources found: " + sources.size());
				return ResponseEntity.ok().body(Tools.formJsonResponse(sources));
			} else {
				logger.info(context.getRemoteAddr() + " -> " + "The specified event does not exist. id: " + id);
				return ResponseEntity.notFound().build();
			}
		} catch (Exception e) {
			logger.error(context.getRemoteAddr() + " -> " + e);
			Tools.getExceptions().add(e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
		}
	}

	//refresh sources from Event Registry for specific event
	@RequestMapping(value="/{id}/sources", method=RequestMethod.POST, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonResponse> refreshEventSources(@PathVariable(name="id") String id) {
		logger.info(context.getRemoteAddr() + " -> " + "Attempting to refresh sources for event with id: " + id);
		try {
			List<IndexedEvent> events = solrClient.QueryIndexedDocuments(IndexedEvent.class, "id:" + id, 1, 0, null);
			if (!events.isEmpty()) {
				logger.info(context.getRemoteAddr() + " -> " + "Event exists... proceeding to query Event Registry for updated sources");
				IndexedEvent event = events.get(0);
				EventRegistryEventArticlesResponse response = eventRegistryClient.QueryEventSources(event.getUri());
				List<IndexedEventSource> sources = eventRegistryClient.PipelineProcessEventSources(id, response);
				logger.info(context.getRemoteAddr() + " -> " + "Total number of sources returned by Event Registry: " + sources.size());
				return ResponseEntity.ok().body(Tools.formJsonResponse(sources));
			} else {
				return ResponseEntity.notFound().build();
			}
		} catch (Exception e) {
			logger.error(context.getRemoteAddr() + " -> " + e);
			Tools.getExceptions().add(e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
		}
	}
	//******************************************************************************
	
	//Private "Helper" Methods
	private Map<String, List<String>> getConceptsMap() {
		String conceptsJson = Tools.getResource(Tools.getProperty("eventRegistry.searchConcepts"));
		ObjectMapper mapper = new ObjectMapper();
		Map<String, List<String>> concepts = null;
		try {
			concepts = mapper.readValue(conceptsJson, Map.class);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return concepts;
	}
	
	private void updateIndexedEventSourcesByFile(String filePath) throws SolrServerException {
		solrClient.UpdateIndexedArticlesFromFile(filePath);
	}
	
	private void updateIndexedEventsByFile(String filePath) throws SolrServerException {
		solrClient.UpdateIndexedEventsFromJsonFile(filePath);
	}

	public List<IndexedEvent> getModelTrainingDataFromEventRegistry() {
		List<IndexedEvent> events = indexEventsByConcept();

		return events;
	}
	
	private List<IndexedEvent> indexEventsByConcept() {
		Map<String, List<String>> concepts = getConceptsMap();
		
		List<IndexedEvent> events = new ArrayList<IndexedEvent>();
		concepts.entrySet().stream().forEach(p -> {
			try {
				events.addAll(queryEvents(p.getKey(), p.getValue()));
			} catch (Exception e) {
				Tools.getExceptions().add(e);
			}
		});
		
		return events;
	}
	
	private List<IndexedEvent> queryEvents(String conceptUri, List<String> subConcepts) throws SolrServerException, IOException {
		EventsRegistryEventsResponse response = eventRegistryClient.QueryEvents(conceptUri, subConcepts);
		return eventRegistryClient.PipelineProcessEvents(response, conceptUri, subConcepts);
	}

	private void dumpEventCategorizationTrainingDataToFile() {
		solrClient.writeTrainingDataToFile(Tools.getProperty("nlp.doccatTrainingFile"), solrClient::getDoccatDataQuery,
				solrClient::formatForEventCategorization, new SolrClient.EventCategorizationThrottle(SolrConstants.Events.CATEGORY_IRRELEVANT, 0.2));
	}

	private void dumpEventClusteringTrainingDataToFile() {
		solrClient.writeTrainingDataToFile(Tools.getProperty("nlp.clusteringTrainingFile"), solrClient::getClusteringDataQuery,
				solrClient::formatForClustering, new SolrClient.ClusteringThrottle("", 0));
	}
	
	private void dumpEventDataToFile(String filename, String query, String filterQueries, int numRows) throws SolrServerException {
		solrClient.WriteEventDataToFile(filename, query, numRows, filterQueries);
	}

	private void dumpSourceDataToFile(String filename, String query, String filterQueries, int numRows) throws SolrServerException {
		solrClient.WriteSourceDataToFile(filename, query, numRows, filterQueries);
	}

	public List<IndexedEvent> refreshEventsFromEventRegistry() {
		try {
			EventsRegistryEventStreamResponse response = eventRegistryClient.QueryEventRegistryMinuteStream();
			List<IndexedEvent> events = eventRegistryClient.PipelineProcessEventStreamResponse(response);
			return events;
		} catch (Exception e) {
			logger.error(e);
			return null;
		}
	}

	public double initiateModelTraining() {
		double accuracy = categorizer.trainEventCategorizationModel();

		return accuracy;
	}
}
