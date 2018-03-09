package eventsregistryapi.model;

import java.util.ArrayList;
import java.util.List;

public class EventsRegistryEventsQuery {
	private final String query = "{eventsRegistryQuery}";
    private final String action = "getEvents";
    private final String resultType = "events";
    private final String eventsSortBy = "date";
    private final String eventsCount = "100";
    private final String eventsEventImageCount = "1";
    private final String eventsStoryImageCount = "1";
    private final String apiKey;

    
    public EventsRegistryEventsQuery(String apiKey){
        this.apiKey = apiKey;
    }

	public String getQuery() {
		return query;
	}
	
	public String getEventsRegistryQuery(String concept, List<String> subConcepts) {
		String wikiPrefix = "http://en.wikipedia.org/wiki/";
		List<String> searchConcepts = new ArrayList<String>();
		searchConcepts.add("\"" + wikiPrefix + concept + "\"");
		for (String subConcept : subConcepts) {
			searchConcepts.add("\"" + wikiPrefix + subConcept + "\"");
		}
		
		String searchStr = String.join(",", searchConcepts);
		
		String eventsRegistryQuery = "{\"$query\":{\"$and\":[{\"conceptUri\":{\"$and\":[" + searchStr + "]}},{\"locationUri\":{\"$and\":[\"http://en.wikipedia.org/wiki/United_States\"]}}]}}";
		
		return eventsRegistryQuery;
	}


	public String getAction() {
		return action;
	}


	public String getResultType() {
		return resultType;
	}


	public String getEventsSortBy() {
		return eventsSortBy;
	}


	public String getEventsCount() {
		return eventsCount;
	}


	public String getEventsEventImageCount() {
		return eventsEventImageCount;
	}


	public String getEventsStoryImageCount() {
		return eventsStoryImageCount;
	}
	
	public String getApiKey() {
		return apiKey;
	}
    
}