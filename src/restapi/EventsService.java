package restapi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

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
import reactor.core.publisher.Mono;
import solrapi.SolrClient;

@Component
public class EventsService {
	private EventRegistryClient eventRegistryClient;
	private SolrClient solrClient;
	private EventCategorizer categorizer;
	
	public EventsService() {
		eventRegistryClient = new EventRegistryClient();
		solrClient = new SolrClient(Tools.getProperty("solr.url"));
		categorizer = new EventCategorizer();
	}
	
	public static void main(String[] args) {
		EventsService svc = new EventsService();
		//svc.updateIndexedArticlesByFile("data/articles.json");
		//svc.updateIndexedEventsByFile("data/events.json");
		//svc.indexEventsByConcept(0);
		//svc.dumpTrainingDataToFile();
		//svc.dumpDataToFile("data/events.json", "categorizationState:S", null, 1000);
	}
	
	//Public REST API
	public Mono<ServerResponse> refreshEventsFromEventRegistry(ServerRequest request) {
		EventsRegistryEventStreamResponse response = eventRegistryClient.QueryEventRegistryMinuteStream();
		List<IndexedEvent> events = eventRegistryClient.PipelineProcessEventStreamResponse(response);
		
		return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromObject(events));
	}
	
	public Mono<ServerResponse> initiateModelTraining(ServerRequest request) {
		dumpTrainingDataToFile();
		double accuracy = processTrainingData();
		
		return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromObject(accuracy));
	}
	
	public Mono<ServerResponse> getIndexedEvents(ServerRequest request) {
		IndexedEventsQueryParams params = request.bodyToMono(IndexedEventsQueryParams.class).block();

		List<IndexedEvent> events = solrClient.QueryIndexedDocuments(IndexedEvent.class, params.getQuery(), params.getRows(), params.getFacetedQueries());
		
		return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromObject(events));
	}
	
	public Mono<ServerResponse> getIndexedArticles(ServerRequest request) {
		IndexedArticlesQueryParams params = request.bodyToMono(IndexedArticlesQueryParams.class).block();

		List<IndexedArticle> articles = solrClient.QueryIndexedDocuments(IndexedArticle.class, params.getQuery(), params.getRows(), params.getFacetedQueries());
		
		return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromObject(articles));
	}
	
	public Mono<ServerResponse> refreshArticlesFromEventRegistry(ServerRequest request) {
		String eventUri = request.bodyToMono(String.class).block();

		EventRegistryEventArticlesResponse response = eventRegistryClient.QueryEventArticles(eventUri);
		List<IndexedArticle> articles = eventRegistryClient.PipelineProcessEventArticles(response);

		return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromObject(articles));
	}
	
	public Mono<ServerResponse> getModelTrainingDataFromEventRegistry(ServerRequest request) {
		List<IndexedEvent> events = indexEventsByConcept();

		return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(BodyInserters.fromObject(events));
	}
	
	//******************************************************************************
	
	//Private "Helper" Methods
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
	
	private void updateIndexedArticlesByFile(String filePath) {
		solrClient.UpdateIndexedArticlesFromFile(filePath);
	}
	
	private void updateIndexedEventsByFile(String filePath) {
		solrClient.UpdateIndexedEventsFromFile(filePath);
	}
	
	private List<IndexedEvent> indexEventsByConcept() {
		Map<String, List<String>> concepts = getConceptsMap();
		
		List<IndexedEvent> events = new ArrayList<IndexedEvent>();
		concepts.entrySet().stream().forEach(p -> {
			events.addAll(queryEvents(p.getKey(), p.getValue()));
		});
		
		return events;
	}
	
	private List<IndexedEvent> queryEvents(String conceptUri, List<String> subConcepts) {
		EventsRegistryEventsResponse response = eventRegistryClient.QueryEvents(conceptUri, subConcepts);
		return eventRegistryClient.PipelineProcessEvents(response, conceptUri, subConcepts);
	}
	
	private void dumpTrainingDataToFile() {
		solrClient.WriteEventCategorizationTrainingDataToFile(Tools.getProperty("nlp.doccatTrainingFile"));
	}
	
	private void dumpDataToFile(String filename, String query, String filterQueries, int numRows) {
		solrClient.WriteEventDataToFile(filename, query, numRows, filterQueries);
	}
	
	private double processTrainingData() {
		double accuracy = categorizer.TrainEventCategorizationModel(Tools.getProperty("nlp.doccatTrainingFile"));
		return accuracy;
	}
}
