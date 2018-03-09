package eventsregistryapi;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Files;

import common.Tools;
import eventsregistryapi.model.Category;
import eventsregistryapi.model.Concept;
import eventsregistryapi.model.EventData;
import eventsregistryapi.model.EventRegistryEventDetailsResponse;
import eventsregistryapi.model.EventsRegistryEventDetailsQuery;
import eventsregistryapi.model.EventsRegistryEventStreamResponse;
import eventsregistryapi.model.EventsRegistryEventsQuery;
import eventsregistryapi.model.EventsRegistryEventsResponse;
import eventsregistryapi.model.IndexedEvent;
import eventsregistryapi.model.Info;
import eventsregistryapi.model.Result;
import nlp.EventCategorizer;
import solrapi.SolrClient;
import solrapi.SolrConstants;

public class EventRegistryClient {

	private RestTemplate restTemplate;
	private SolrClient solrClient;
	private final String minuteStreamEventsUrl = Tools.getProperty("eventRegistry.minuteStreamEventsUrl");
	private final String eventDetailsUrl = Tools.getProperty("eventRegistry.eventDetailsUrl");
	private final String apiKey = Tools.getProperty("eventRegistry.apiKey");
	private final String eventRegistryCategories = Tools.getProperty("eventRegistry.categories");
	private final String proxyUrl = Tools.getProperty("proxy");
	private final String solrUrl = Tools.getProperty("solr.url");

	public static void main(String args[]) {
		EventRegistryClient eventRegistryClient = new EventRegistryClient();
		//eventRegistryClient.QueryEvent("eng-3800037");
		//eventRegistryClient.QueryEventRegistryMinuteStream();
	}
	
	public EventRegistryClient() {
		solrClient = new SolrClient(solrUrl);
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

	    Proxy proxy = new Proxy(Type.HTTP, new InetSocketAddress(proxyUrl, 8080));
	    requestFactory.setProxy(proxy);
	    
		restTemplate = new RestTemplate(requestFactory);
	}
	
	private List<String> GetEventRegistryValidCategories() {
		String eventCategories = Tools.GetFileString(eventRegistryCategories).toLowerCase();
		List<String> eventCategoriesList = Arrays.stream(eventCategories.split(System.lineSeparator())).collect(Collectors.toList());
		return eventCategoriesList;
	}
	
	public void PipelineProcessEventStreamResponse(EventsRegistryEventStreamResponse response) {
		List<String> eventCategories = GetEventRegistryValidCategories();
		List<IndexedEvent> validEvents = new ArrayList<IndexedEvent>();
		List<IndexedEvent> readyToIndex = new ArrayList<IndexedEvent>();
		
		//Only care about English language events, so get set of English event URIs
		List<String> engEvents = Arrays.stream(response.getRecentActivityEvents().getActivity())
			.filter(p -> p.contains("eng")).collect(Collectors.toList());
		
		//Filter out the non-English events
		Map<String, EventData> engEventData = response.getRecentActivityEvents().getEventInfo().entrySet().stream()
			.filter(p -> engEvents.contains(p.getKey())).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
		
		//Filter out events that are outside the United States or that have fewer than 10 articles or are missing a summary/title
		engEventData = engEventData.entrySet().stream()
			.filter(p -> p.getValue().getLocation().getCountry().getLabel().getEng().compareTo("United States") == 0)
			.filter(p -> p.getValue().getTotalArticleCount() >= 10)
			.filter(p -> p.getValue().getTitle().getEng() != null)
			.filter(p -> p.getValue().getSummary().getEng() != null)
			.collect(Collectors.toMap(Entry::getKey, Entry::getValue));
		
		//Filter out events that do not have any of the required categories.  This filtration is performed as a first-pass using the
		//taxonomy provided by the event registry.
		engEventData.entrySet().stream().forEach(p -> {
			List<Category> validCategories = Arrays.stream(p.getValue().getCategories())
					.filter(cat -> eventCategories.contains(cat.getLabel().toLowerCase()))
					.collect(Collectors.toList());
			if (!validCategories.isEmpty()) {
				//This event data has at least one valid category
				validEvents.add(p.getValue().GetIndexedEvent());
			}
		});
		
		//Aggregate the valid events that have not yet been indexed
		List<IndexedEvent> indexableEvents = GetIndexableEvents(validEvents);
		
		//Perform NLP on the index-ready events.  This phase uses the NICC taxonomy to produce a PRELIMINARY category for each event.  
		//The preliminary category may then either be updated by the user or accepted as-is within the NICC landing page UI.
		EventCategorizer categorizer = new EventCategorizer();
		List<IndexedEvent> categorizedEvents = categorizer.DetectEventDataCategories(indexableEvents);

		//Iterate through the categorized events to query Event Registry for event details
		for (IndexedEvent event : categorizedEvents) {
			//Indicate that this event was categorized by the openNLP document categorizer 
			event.setCategorizationState(SolrConstants.Events.CATEGORIZATION_STATE_MACHINE);
			
			//Get specifics about the event details.  This includes the medoid article URL.
			EventRegistryEventDetailsResponse eventDetailsResponse = QueryEvent(event.getUri());
			if (eventDetailsResponse != null) {
				Info eventInfo = eventDetailsResponse.getEventDetails().get(event.getUri()).getInfo();
				readyToIndex.add(event.updateWithEventDetails(eventInfo));
			}
		}
		
		solrClient.IndexEvents(readyToIndex);
	}
	
	private List<IndexedEvent> GetIndexableEvents(List<IndexedEvent> events) {
		List<IndexedEvent> indexableEvents = events.stream()
				.filter(p -> !solrClient.IsEventAlreadyIndexed(p.getId()))
				.collect(Collectors.toList());
		
		return indexableEvents;
	}
	
	public EventsRegistryEventStreamResponse QueryEventRegistryMinuteStream() {
//		String responseStr = restTemplate.getForObject(minuteStreamEventsUrl + "?apiKey=" + apiKey, String.class);
//		try {
//			FileUtils.writeStringToFile(new File("data/EventsStreamResponse.json"), responseStr);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		
//		return null;
		
		EventsRegistryEventStreamResponse response = restTemplate.getForObject(minuteStreamEventsUrl + "?apiKey=" + apiKey, EventsRegistryEventStreamResponse.class);
		
//		//Temporary Simulation Stuff
//		EventsRegistryEventStreamResponse response = null;
//		try {
//			String responseJson = Tools.GetFileString("data/etc/EventRegistryEventStreamResponse.json");
//			ObjectMapper mapper = new ObjectMapper();
//			response = mapper.readValue(responseJson, EventsRegistryEventStreamResponse.class);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		return response;
	}
	
	public EventRegistryEventDetailsResponse QueryEvent(String eventUri) {
		EventsRegistryEventDetailsQuery query = new EventsRegistryEventDetailsQuery(eventUri, apiKey);
		String url = eventDetailsUrl + Tools.GetQueryString(query);
		String response = restTemplate.getForObject(url, String.class);
		response = "{ \"eventDetails\":" + response + "}";
		
		ObjectMapper mapper = new ObjectMapper();
		EventRegistryEventDetailsResponse eventDetailsResponse = null;
		try {
			eventDetailsResponse = mapper.readValue(response, EventRegistryEventDetailsResponse.class);
		} catch (IOException e) {
			// TODO Auto-generated catch block
		}
		
		return eventDetailsResponse;
	}
	
	public EventsRegistryEventsResponse QueryEvents(String conceptUri, List<String> subConcepts) {
		EventsRegistryEventsQuery query = new EventsRegistryEventsQuery(apiKey);
		String url = eventDetailsUrl + Tools.GetQueryString(query);
		String eventsRegistryQuery = query.getEventsRegistryQuery(conceptUri, subConcepts);
		EventsRegistryEventsResponse response = restTemplate.getForObject(url, EventsRegistryEventsResponse.class, eventsRegistryQuery);

		return response;
	}
	
	public void PipelineProcessEvents(EventsRegistryEventsResponse response, String conceptUri, List<String> subConcepts, int searchWeightCutoff) {
		if (response.getEvents() != null) {
			//Filter out events that have a search weighting threshold below searchWeightCutoff or that are missing a summary/title 
			//and convert to indexed event type.
			List<Result> weighedResults = Arrays.stream(response.getEvents().getResults())
					.filter(p -> p.getWgt() > searchWeightCutoff)
					.filter(p -> p.getTotalArticleCount() >= 0)
					.filter(p -> p.getTitle().getEng() != null)
					.filter(p -> p.getSummary().getEng() != null)
					.collect(Collectors.toList());
			
			//Gather the set of valid concepts for comparison against the concepts associated with each of the events
			subConcepts.add(conceptUri);
			
			//We only care about search results where the concept URI is above 50.
			List<IndexedEvent> conceptFilteredEvents = new ArrayList<IndexedEvent>();
			for (Result result : weighedResults) {
				for (Concept concept : result.getConcepts()) {
					String conceptLabel = concept.getLabel().getEng();
					if(subConcepts.stream().anyMatch(p -> p.equals(conceptLabel)) && concept.getScore() >= 50) {
						conceptFilteredEvents.add(result.GetIndexedEvent());
					}
				}
			}

			List<IndexedEvent> indexableEvents = GetIndexableEvents(conceptFilteredEvents);
			
			indexableEvents.stream().forEach(p -> {
				p.setCategorizationState(SolrConstants.Events.CATEGORIZATION_STATE_SEARCH_INFERRED);
				p.setCategory(conceptUri);
			});
			
			solrClient.IndexEvents(indexableEvents);
		}
	}
}
