package webapp.controllers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

import common.Tools;
import eventsregistryapi.EventRegistryClient;
import eventsregistryapi.model.EventRegistryEventArticlesResponse;
import eventsregistryapi.model.EventsRegistryEventStreamResponse;
import eventsregistryapi.model.EventsRegistryEventsResponse;
import eventsregistryapi.model.IndexedArticle;
import eventsregistryapi.model.IndexedArticlesQueryParams;
import eventsregistryapi.model.IndexedEvent;
import eventsregistryapi.model.IndexedEventsQueryParams;
import nlp.EventCategorizer;
import solrapi.SolrClient;
import solrapi.SolrConstants;
import webapp.models.JsonResponse;
import webapp.services.RefreshEventsService;

@CrossOrigin
@RestController
public class EventsController {
	private EventRegistryClient eventRegistryClient;
	private SolrClient solrClient;
	private EventCategorizer categorizer;
	private Gson gson;
	private List<Exception> exceptions;
	
	final static Logger logger = LogManager.getLogger(EventsController.class);
	
	@Autowired
	private RefreshEventsService refreshEventsService;
	
	public EventsController() {
		eventRegistryClient = new EventRegistryClient();
		solrClient = new SolrClient(Tools.getProperty("solr.url"));
		categorizer = new EventCategorizer();
		gson = new Gson();
		exceptions = new ArrayList<Exception>();
	}
	
	public EventRegistryClient getEventRegistryClient() {
		return eventRegistryClient;
	}
	
	//@PostConstruct
	public void initRefreshEventsProcess() {
		try {
			refreshEventsService.process(this);
		} catch (Exception e) {}
	}
	
	public void refreshEventsProcessExceptionHandler(Exception e) {
		exceptions.add(e);
		try {
			refreshEventsService.process(this);
		} catch (Exception e1) {}
	}
	
	//Public REST API
	@RequestMapping(value="/api/events/refresh", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonResponse> refreshEventsFromEventRegistry() {
		try {
			EventsRegistryEventStreamResponse response = eventRegistryClient.QueryEventRegistryMinuteStream();
			List<IndexedEvent> events = eventRegistryClient.PipelineProcessEventStreamResponse(response);
			return ResponseEntity.ok().body(formJsonResponse(events));
		} catch (Exception e) {
			logger.error(e);
			exceptions.add(e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(formJsonResponse(null));
		}
	}
	
	@RequestMapping(value="/api/modelTraining/train", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonResponse> initiateModelTraining() {
		dumpTrainingDataToFile();
		double accuracy = processTrainingData();
		
		return ResponseEntity.ok().body(formJsonResponse(accuracy));
	}
	
	@RequestMapping(value="/api/events/findSimilar", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonResponse> findSimilarDocuments(@RequestParam String searchText) {
		try {
			List<SolrDocument> docs = solrClient.FindSimilarDocuments(searchText);
			return ResponseEntity.ok().body(formJsonResponse(docs));
		} catch (Exception e) {
			logger.error(e);
			exceptions.add(e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(formJsonResponse(null));
		}
	}
	
	@RequestMapping(value="/api/events", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonResponse> getEvents(IndexedEventsQueryParams params) {
		try {
			List<IndexedEvent> events = solrClient.QueryIndexedDocuments(IndexedEvent.class, params.getQuery(), params.getQueryRows(), null, params.getFacetedQueries());
			return ResponseEntity.ok().body(formJsonResponse(events, params.getQueryTimeStamp()));
		} catch (Exception e) {
			logger.error(e);
			exceptions.add(e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(formJsonResponse(null, params.getQueryTimeStamp()));
		}
	}
	
	@RequestMapping(value="/api/event/{id}", method=RequestMethod.DELETE, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonResponse> deleteEventById(@PathVariable(name="id") String id) {
		try {
			logger.info("Trying to delete event with id: " + id);
			List<IndexedEvent> events = solrClient.QueryIndexedDocuments(IndexedEvent.class, "id:" + id, 1, null);
			if (!events.isEmpty()) {
				IndexedEvent event = events.get(0);
				event.setEventState(SolrConstants.Events.EVENT_STATE_DELETED);
				solrClient.IndexDocuments(events);
				logger.info("Deleted event with id: " + id);
				return ResponseEntity.ok().body(formJsonResponse(event));
			} else {
				logger.info("Failed to deleted event with id: " + id);
				return ResponseEntity.notFound().build();
			}
		} catch (Exception e) {
			logger.error(e);
			exceptions.add(e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(formJsonResponse(null));
		}
	}
	
	@RequestMapping(value="/api/event/{id}", method=RequestMethod.POST, consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonResponse> updateEvent(@RequestBody IndexedEvent event) {
		try {
			solrClient.IndexDocument(event);
			return ResponseEntity.ok().body(formJsonResponse(event));
		} catch (Exception e) {
			logger.error(e);
			exceptions.add(e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(formJsonResponse(null));
		}
	}
	
	@RequestMapping(value="/api/articles", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonResponse> getArticles(IndexedArticlesQueryParams params) {
		try {
			List<IndexedArticle> articles = solrClient.QueryIndexedDocuments(IndexedArticle.class, params.getQuery(), params.getQueryRows(), null, params.getFacetedQueries());
			return ResponseEntity.ok().body(formJsonResponse(articles, params.getQueryTimeStamp()));
		} catch (Exception e) {
			logger.error(e);
			exceptions.add(e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(formJsonResponse(null, params.getQueryTimeStamp()));
		}
	}

	@RequestMapping(value="/api/articles/refresh", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonResponse> refreshArticlesFromEventRegistry(@PathVariable(name="eventUri") String eventUri) {
		EventRegistryEventArticlesResponse response = eventRegistryClient.QueryEventArticles(eventUri);
		
		try {
			List<IndexedArticle> articles = eventRegistryClient.PipelineProcessEventArticles(response);
			return ResponseEntity.ok().body(formJsonResponse(articles));
		} catch (Exception e) {
			logger.error(e);
			exceptions.add(e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(formJsonResponse(null));
		}
	}
	
	@RequestMapping(value="/api/modelTraining/refreshData", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonResponse> getModelTrainingDataFromEventRegistry() {
		List<IndexedEvent> events = indexEventsByConcept();

		return ResponseEntity.ok().body(formJsonResponse(events));
	}

	//******************************************************************************
	
	//Private "Helper" Methods
	private JsonResponse formJsonResponse(Object data) {
		JsonResponse response = new JsonResponse();
		response.setData(data);
		response.setExceptions(exceptions);
		exceptions.clear();
		
		return response;
	}
	
	private JsonResponse formJsonResponse(Object data, String timeStamp) {
		JsonResponse response = new JsonResponse(timeStamp);
		response.setData(data);
		response.setExceptions(exceptions);
		exceptions.clear();
		
		return response;
	}
	
	private Map<String, List<String>> getConceptsMap() {
		String conceptsJson = Tools.GetFileString(Tools.getProperty("eventRegistry.searchConcepts"));
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
	
	private void updateIndexedArticlesByFile(String filePath) throws SolrServerException {
		solrClient.UpdateIndexedArticlesFromFile(filePath);
	}
	
	private void updateIndexedEventsByFile(String filePath) throws SolrServerException {
		solrClient.UpdateIndexedEventsFromFile(filePath);
	}
	
	private List<IndexedEvent> indexEventsByConcept() {
		Map<String, List<String>> concepts = getConceptsMap();
		
		List<IndexedEvent> events = new ArrayList<IndexedEvent>();
		concepts.entrySet().stream().forEach(p -> {
			try {
				events.addAll(queryEvents(p.getKey(), p.getValue()));
			} catch (Exception e) {
				exceptions.add(e);
			}
		});
		
		return events;
	}
	
	private List<IndexedEvent> queryEvents(String conceptUri, List<String> subConcepts) throws SolrServerException {
		EventsRegistryEventsResponse response = eventRegistryClient.QueryEvents(conceptUri, subConcepts);
		return eventRegistryClient.PipelineProcessEvents(response, conceptUri, subConcepts);
	}
	
	private void dumpTrainingDataToFile() {
		solrClient.WriteEventCategorizationTrainingDataToFile(Tools.getProperty("nlp.doccatTrainingFile"));
	}
	
	private void dumpDataToFile(String filename, String query, String filterQueries, int numRows) throws SolrServerException {
		solrClient.WriteEventDataToFile(filename, query, numRows, filterQueries);
	}
	
	private double processTrainingData() {
		double accuracy = categorizer.TrainEventCategorizationModel(Tools.getProperty("nlp.doccatTrainingFile"));
		return accuracy;
	}
}
