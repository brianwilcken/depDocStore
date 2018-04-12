package eventsregistryapi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import common.Tools;
import eventsregistryapi.model.ArticleResult;
import eventsregistryapi.model.Category;
import eventsregistryapi.model.Concept;
import eventsregistryapi.model.EventArticles;
import eventsregistryapi.model.EventData;
import eventsregistryapi.model.EventRegistryEventArticlesResponse;
import eventsregistryapi.model.EventRegistryEventDetailsResponse;
import eventsregistryapi.model.EventsRegistryEventArticlesQuery;
import eventsregistryapi.model.EventsRegistryEventDetailsQuery;
import eventsregistryapi.model.EventsRegistryEventStreamResponse;
import eventsregistryapi.model.EventsRegistryEventsQuery;
import eventsregistryapi.model.EventsRegistryEventsResponse;
import eventsregistryapi.model.IndexedEventSource;
import eventsregistryapi.model.IndexedEvent;
import eventsregistryapi.model.Info;
import eventsregistryapi.model.Location;
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
	
	public EventRegistryClient() {
		solrClient = new SolrClient(solrUrl);
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

//	    Proxy proxy = new Proxy(Type.HTTP, new InetSocketAddress(proxyUrl, 8080));
//	    requestFactory.setProxy(proxy);
	    
		restTemplate = new RestTemplate(requestFactory);
	}
	
	private List<String> GetEventRegistryValidCategories() {
		String eventCategories = Tools.GetFileString(eventRegistryCategories).toLowerCase();
		List<String> eventCategoriesList = Arrays.stream(eventCategories.split(System.lineSeparator())).collect(Collectors.toList());
		return eventCategoriesList;
	}
	
	public List<IndexedEvent> PipelineProcessEventStreamResponse(EventsRegistryEventStreamResponse response) throws SolrServerException {
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
		
		solrClient.IndexDocuments(readyToIndex);
		
		return readyToIndex;
	}
	
	private List<IndexedEvent> GetIndexableEvents(List<IndexedEvent> events) throws SolrServerException {
		List<SolrServerException> exs = new ArrayList<SolrServerException>();
		List<IndexedEvent> indexableEvents = events.stream()
				.filter(p -> {
					try {
						return !solrClient.IsDocumentAlreadyIndexed(p.getUri());
					} catch (SolrServerException e) {
						exs.add(e);
						return false;
					}
				})
				.collect(Collectors.toList());
		
		if (!exs.isEmpty()) {
			throw exs.get(0);
		}
		
		return indexableEvents;
	}
	
	public EventsRegistryEventStreamResponse QueryEventRegistryMinuteStream() throws RestClientException {
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
	
	public List<IndexedEvent> PipelineProcessEvents(EventsRegistryEventsResponse response, String conceptUri, List<String> subConcepts) throws SolrServerException {
		if (response.getEvents() != null) {
			//Filter out events that are missing a summary/title 
			List<Result> weighedResults = Arrays.stream(response.getEvents().getResults())
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

			//Only index events that have not yet been indexed
			List<IndexedEvent> indexableEvents = GetIndexableEvents(conceptFilteredEvents);
			
			//Categorize the events
			EventCategorizer categorizer = new EventCategorizer();
			List<IndexedEvent> categorizedEvents = categorizer.DetectEventDataCategories(indexableEvents);
			
			categorizedEvents.stream().forEach(p -> {
				p.setCategorizationState(SolrConstants.Events.CATEGORIZATION_STATE_MACHINE);
			});
			
			solrClient.IndexDocuments(categorizedEvents);
			
			return categorizedEvents;
		}
		return new ArrayList<IndexedEvent>();
	}
	
	public EventRegistryEventArticlesResponse QueryEventSources(String eventUri) {
		EventsRegistryEventArticlesQuery query = new EventsRegistryEventArticlesQuery(eventUri, apiKey);
		String url = eventDetailsUrl + Tools.GetQueryString(query);
		String response = restTemplate.getForObject(url, String.class);
		
//		try {
//		FileUtils.writeStringToFile(new File("data/EventArticlesResponse.json"), response);
//	} catch (IOException e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//	}
		
		response = "{ \"eventArticles\":" + response + "}";
		
		ObjectMapper mapper = new ObjectMapper();
		EventRegistryEventArticlesResponse eventArticlesResponse = null;
		try {
			eventArticlesResponse = mapper.readValue(response, EventRegistryEventArticlesResponse.class);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			String details = e.toString();
			System.out.println(details);
		}
		
		return eventArticlesResponse;
	}
	
	public List<IndexedEventSource> PipelineProcessEventSources(EventRegistryEventArticlesResponse response) throws SolrServerException {
		if (response != null && !response.getEventArticles().isEmpty()) {
			//Extract the only event articles object that exists in the response
			EventArticles eventArticles = response.getEventArticles().entrySet().stream().findFirst().get().getValue();
			
			//Only consider English language articles
			List<ArticleResult> engArticles = eventArticles.getArticles().getResults().stream()
				.filter(p -> p.getLang().equalsIgnoreCase("eng"))
				.collect(Collectors.toList());
			
			//Only consider articles that originate within the United States
			List<ArticleResult> usArticles = engArticles.stream().filter(p -> {
				Location loc = p.getSource().getLocation();
				if (loc != null && loc.getCountry() != null) {
					return loc.getCountry().getLabel().getEng().equals("United States");
				}
				return false;
			}).collect(Collectors.toList());
			
			//Only index articles that have not yet been indexed
			List<SolrServerException> exs = new ArrayList<SolrServerException>();
			List<IndexedEventSource> indexableArticles = usArticles.stream().filter(p -> {
				try {
					return !solrClient.IsDocumentAlreadyIndexed(p.getUri());
				} catch (SolrServerException e) {
					exs.add(e);
					return false;
				}
			}).map(p -> p.GetIndexedArticle())
					.collect(Collectors.toList());
			
			if (!exs.isEmpty()) {
				throw exs.get(0);
			}
			
			solrClient.IndexDocuments(indexableArticles);
			
			return indexableArticles;
		}
		return null;
	}
}
