package restapi;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import common.Tools;
import eventsregistryapi.EventRegistryClient;
import eventsregistryapi.model.EventsRegistryEventStreamResponse;
import eventsregistryapi.model.EventsRegistryEventsResponse;
import nlp.EventCategorizer;
import solrapi.SolrClient;

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
		//svc.indexEventsByConcept(0);
		//svc.updateSearchInferredEvents();
		//svc.refreshEventsFromEventRegistry();
		//svc.dumpTrainingDataToFile();
		//svc.dumpSearchInferredEvents();
		//svc.initiateModelTraining(100, 1);
		//svc.dumpDataToFile("data/events.json", "categorizationState:I", null, 1000);
		svc.updateIndexedEventsByFile("data/events.json");
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
	
	public void updateSearchInferredEvents() {
		Map<String, List<String>> concepts = getConceptsMap();
		
		concepts.entrySet().stream().forEach(p -> {
			updateIndexedEventsByFile("data/search-inferred/" + p.getKey() + "-data.json");
		});
	}
	
	public void updateIndexedEventsByFile(String filePath) {
		solrClient.UpdateIndexedEventsFromFile(filePath);
	}
	
	public void indexEventsByConcept(int sensitivity) {
		Map<String, List<String>> concepts = getConceptsMap();
		
		concepts.entrySet().stream().forEach(p -> {
			queryEvents(p.getKey(), p.getValue(), sensitivity);
			dumpSearchInferredEventsByConcept(p.getKey());
		});
	}
	
	public void dumpSearchInferredEvents() {
		Map<String, List<String>> concepts = getConceptsMap();
		concepts.entrySet().stream().forEach(p -> {
			dumpSearchInferredEventsByConcept(p.getKey());
		});
	}
	
	public void dumpSearchInferredEventsByConcept(String conceptUri) {
		dumpDataToFile("data/search-inferred/" + conceptUri + "-data.json", "categorizationState:S", "category:" + conceptUri, 500);
	}
	
	public void dumpTrainingDataToFile() {
		solrClient.WriteEventCategorizationTrainingDataToFile(Tools.getProperty("nlp.doccatTrainingFile"));
	}
	
	public void dumpDataToFile(String filename, String query, String filterQueries, int numRows) {
		solrClient.WriteEventDataToFile(filename, query, filterQueries, numRows);
	}
	
	public void initiateModelTrainingNoIndexDump(int iterations, int cutoff) {
		categorizer.TrainEventCategorizationModel(Tools.getProperty("nlp.doccatTrainingFile"), iterations, cutoff);
	}
	
	public void initiateModelTraining(int iterations, int cutoff) {
		dumpTrainingDataToFile();
		categorizer.TrainEventCategorizationModel(Tools.getProperty("nlp.doccatTrainingFile"), iterations, cutoff);
	}
	
	public void evaluateEventCategorizationModelPerformance() {
		
	}
	
	public void refreshEventsFromEventRegistry() {
		EventsRegistryEventStreamResponse response = eventRegistryClient.QueryEventRegistryMinuteStream();
		eventRegistryClient.PipelineProcessEventStreamResponse(response);
	}
	
	public void queryEvents(String conceptUri, List<String> subConcepts, int sensitivity) {
		EventsRegistryEventsResponse response = eventRegistryClient.QueryEvents(conceptUri, subConcepts);
		eventRegistryClient.PipelineProcessEvents(response, conceptUri, subConcepts, sensitivity);
	}
}
