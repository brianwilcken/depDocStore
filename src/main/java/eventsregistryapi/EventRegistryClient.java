package eventsregistryapi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import eventsregistryapi.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import common.Tools;
import solrapi.model.IndexedEventSource;
import solrapi.model.IndexedEvent;
import nlp.EventCategorizer;
import solrapi.SolrClient;
import solrapi.SolrConstants;

public class EventRegistryClient {

	final static Logger logger = LogManager.getLogger(EventRegistryClient.class);

	private RestTemplate restTemplate;
	private SolrClient solrClient;
	private EventCategorizer categorizer;
	private final String minuteStreamEventsUrl = Tools.getProperty("eventRegistry.minuteStreamEventsUrl");
	private final String eventDetailsUrl = Tools.getProperty("eventRegistry.eventDetailsUrl");
	private final String apiKey = Tools.getProperty("eventRegistry.apiKey");
	private final String eventRegistryCategories = Tools.getProperty("eventRegistry.categories");
	private final String proxyUrl = Tools.getProperty("proxy");
	private final int proxyPort = Integer.parseInt(Tools.getProperty("proxyPort"));
	private final Boolean useProxy = Boolean.parseBoolean(Tools.getProperty("use.proxy"));
	private final String solrUrl = Tools.getProperty("solr.url");
	private final String TOTAL_TOKENS_KEY = "X-RateLimit-Limit";
    private final String REMAINING_TOKENS_KEY = "X-RateLimit-Remaining";

	
	public EventRegistryClient() {
		solrClient = new SolrClient(solrUrl);
		categorizer = new EventCategorizer(solrClient);
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

		if (useProxy) {
			Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyUrl, proxyPort));
			requestFactory.setProxy(proxy);
		}

		restTemplate = new RestTemplate(requestFactory);
	}
	
	private List<String> GetEventRegistryValidCategories() {
		String eventCategories = Tools.getResource(eventRegistryCategories).toLowerCase();
		List<String> eventCategoriesList = Arrays.stream(eventCategories.split(System.lineSeparator())).collect(Collectors.toList());
		return eventCategoriesList;
	}
	
	public List<IndexedEvent> PipelineProcessEventStreamResponse(EventsRegistryEventStreamResponse response) throws SolrServerException, IOException {
		List<String> eventCategories = GetEventRegistryValidCategories();
		List<IndexedEvent> validEvents = new ArrayList<IndexedEvent>();
		List<IndexedEvent> readyToIndexEvents = new ArrayList<IndexedEvent>();
		List<IndexedEventSource> readyToIndexEventSources = new ArrayList<IndexedEventSource>();
		
		//Only care about English language events, so get set of English event URIs
		List<String> engEvents = Arrays.stream(response.getRecentActivityEvents().getActivity())
			.filter(p -> p.contains("eng")).collect(Collectors.toList());
		
		//Filter out the non-English events
		Map<String, EventData> engEventData = response.getRecentActivityEvents().getEventInfo().entrySet().stream()
			.filter(p -> engEvents.contains(p.getKey())).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
		
		//Filter out events that are outside the United States or that have fewer than 10 articles or are missing a summary/title
		engEventData = engEventData.entrySet().stream()
			.filter(p -> p.getValue().getLocation().getCountry().getLabel().getEng().compareTo("United States") == 0)
			//.filter(p -> p.getValue().getTotalArticleCount() >= 10)
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
		List<IndexedEvent> indexableEvents = solrClient.GetIndexableEvents(validEvents);
		
		//Perform NLP on the index-ready events.  This phase uses the NICC taxonomy to produce a PRELIMINARY category for each event.  
		//The preliminary category may then either be updated by the user or accepted as-is within the NICC landing page UI.
		List<IndexedEvent> categorizedEvents = categorizer.detectEventDataCategories(indexableEvents);

		//Iterate through the categorized events to query Event Registry for event details
		for (IndexedEvent event : categorizedEvents) {
			//Indicate that this event was categorized by the openNLP document categorizer 
			event.setCategorizationState(SolrConstants.Events.CATEGORIZATION_STATE_MACHINE);

			//Accessing event details is expensive, so we limit this to only non-deleted events
			if (event.getEventState().compareTo(SolrConstants.Events.EVENT_STATE_DELETED) != 0) {
				//Get specifics about the event details.  This includes the medoid article URL.
				EventRegistryEventDetailsResponse eventDetailsResponse = QueryEvent(event.getUri());
				if (eventDetailsResponse != null) {
					Info eventInfo = eventDetailsResponse.getEventDetails().get(event.getUri()).getInfo();
					readyToIndexEvents.add(event.updateWithEventDetails(eventInfo));

					//Index each of the sources contained within the stories collection
					for (Story story : eventInfo.getStories()) {
						if (story.getMedoidArticle().getLang().equalsIgnoreCase("eng")) {
							readyToIndexEventSources.add(story.getMedoidArticle().getIndexedEventSource(event.getId()));
						}
					}
				}
			}
		}

		//Index all events and all event sources
		solrClient.indexDocuments(readyToIndexEvents);
		solrClient.indexDocuments(readyToIndexEventSources);

		return readyToIndexEvents;
	}
	
	public EventsRegistryEventStreamResponse QueryEventRegistryMinuteStream() throws RestClientException {
//		String responseStr = restTemplate.getForEntity(minuteStreamEventsUrl + "?apiKey=" + apiKey, String.class);
//		try {
//			FileUtils.writeStringToFile(new File("data/EventsStreamResponse.json"), responseStr);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		
//		return null;

		ResponseEntity<EventsRegistryEventStreamResponse> response = restTemplate.getForEntity(minuteStreamEventsUrl + "?apiKey=" + apiKey, EventsRegistryEventStreamResponse.class);

		AssessRemainingTokens(response);

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
		
		return response.getBody();
	}

	private <T> void AssessRemainingTokens(ResponseEntity<T> response) {
		//requirements regarding service throttling, due to token depletion TBD
		if (response.getHeaders().containsKey(REMAINING_TOKENS_KEY) && response.getHeaders().containsKey(TOTAL_TOKENS_KEY)) {
			int remainingTokens = Integer.parseInt(response.getHeaders().getFirst(REMAINING_TOKENS_KEY));
			int totalTokens = Integer.parseInt(response.getHeaders().getFirst(TOTAL_TOKENS_KEY));
			if ((double)remainingTokens/(double)totalTokens < 0.25) { //fewer than 125,000 tokens
				logger.warn("Event Registry tokens at 25%");
			} else if ((double)remainingTokens/(double)totalTokens < 0.1) { //fewer than 50,000 tokens
				logger.warn("Event Registry tokens at 10%");
			} else if ((double)remainingTokens/(double)totalTokens < 0.01) { //fewer than 5,000 tokens
				logger.warn("Event Registry tokens at 1%");
			}
			logger.info("Remaining Event Registry Tokens: " + remainingTokens);
		}
	}
	
	public EventRegistryEventDetailsResponse QueryEvent(String eventUri) {
		EventsRegistryEventDetailsQuery query = new EventsRegistryEventDetailsQuery(eventUri, apiKey);
		String url = eventDetailsUrl + Tools.GetQueryString(query);
		ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
		AssessRemainingTokens(response);
		String responseBody = "{ \"eventDetails\":" + response.getBody() + "}";
		
		ObjectMapper mapper = new ObjectMapper();
		EventRegistryEventDetailsResponse eventDetailsResponse = null;
		try {
			eventDetailsResponse = mapper.readValue(responseBody, EventRegistryEventDetailsResponse.class);
		} catch (IOException e) {
			// TODO Auto-generated catch block
		}
		
		return eventDetailsResponse;
	}
	
	public EventsRegistryEventsResponse QueryEvents(String conceptUri, List<String> subConcepts) {
		logger.info("Now querying Event Registry concepts search with the following list of concepts: " + conceptUri + ", " + String.join(", ", subConcepts));
		EventsRegistryEventsQuery query = new EventsRegistryEventsQuery(apiKey);
		String url = eventDetailsUrl + Tools.GetQueryString(query);
		String eventsRegistryQuery = query.getEventsRegistryConceptsQuery(conceptUri, subConcepts);
		ResponseEntity<EventsRegistryEventsResponse> response = restTemplate.getForEntity(url, EventsRegistryEventsResponse.class, eventsRegistryQuery);
		AssessRemainingTokens(response);

		return response.getBody();
	}
	
	public List<IndexedEvent> PipelineProcessEvents(EventsRegistryEventsResponse response, String conceptUri, List<String> subConcepts) throws SolrServerException, IOException {
		if (response.getEvents() != null) {
			//Filter out events that are missing a summary/title 
			List<IndexedEvent> validEvents = Arrays.stream(response.getEvents().getResults())
					.filter(p -> p.getTitle().getEng() != null)
					.filter(p -> p.getSummary().getEng() != null)
					.map(p -> p.GetIndexedEvent())
					.collect(Collectors.toList());

			//Only index events that have not yet been indexed
			List<IndexedEvent> indexableEvents = solrClient.GetIndexableEvents(validEvents);
			
			//Categorize the events
			List<IndexedEvent> categorizedEvents = categorizer.detectEventDataCategories(indexableEvents);
			
			categorizedEvents.stream().forEach(p -> {
				p.setCategorizationState(SolrConstants.Events.CATEGORIZATION_STATE_MACHINE);
			});
			
			solrClient.indexDocuments(categorizedEvents);

			logger.info("Successfully indexed " + categorizedEvents.size() + " events.");

			return categorizedEvents;
		}
		logger.info("No events found.");
		return new ArrayList<IndexedEvent>();
	}
	
	public EventRegistryEventArticlesResponse QueryEventSources(String eventUri) {
		EventsRegistryEventArticlesQuery query = new EventsRegistryEventArticlesQuery(eventUri, apiKey);
		String url = eventDetailsUrl + Tools.GetQueryString(query);
		ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
		AssessRemainingTokens(response);
		
//		try {
//		FileUtils.writeStringToFile(new File("data/EventArticlesResponse.json"), response);
//	} catch (IOException e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//	}
		
		String responseBody = "{ \"eventArticles\":" + response.getBody() + "}";
		
		ObjectMapper mapper = new ObjectMapper();
		EventRegistryEventArticlesResponse eventArticlesResponse = null;
		try {
			eventArticlesResponse = mapper.readValue(responseBody, EventRegistryEventArticlesResponse.class);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			String details = e.toString();
			System.out.println(details);
		}
		
		return eventArticlesResponse;
	}
	
	public List<IndexedEventSource> PipelineProcessEventSources(String eventId, EventRegistryEventArticlesResponse response) throws SolrServerException {
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
					return !solrClient.DocumentExistsByURI(p.getUri());
				} catch (SolrServerException e) {
					exs.add(e);
					return false;
				}
			}).map(p -> p.getIndexedEventSource(eventId))
					.collect(Collectors.toList());
			
			if (!exs.isEmpty()) {
				throw exs.get(0);
			}
			
			solrClient.indexDocuments(indexableArticles);
			
			return indexableArticles;
		}
		return null;
	}
}
