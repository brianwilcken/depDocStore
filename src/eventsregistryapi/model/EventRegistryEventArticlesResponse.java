package eventsregistryapi.model;

import java.util.LinkedHashMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EventRegistryEventArticlesResponse {
    private LinkedHashMap<String, EventArticles> eventArticles;

	public LinkedHashMap<String, EventArticles> getEventArticles() {
		return eventArticles;
	}

	public void setEventArticles(LinkedHashMap<String, EventArticles> eventArticles) {
		this.eventArticles = eventArticles;
	}
}
