package eventsregistryapi.model;

import java.util.LinkedHashMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EventRegistryEventDetailsResponse {
    private LinkedHashMap<String, EventDetails> eventDetails;

	public LinkedHashMap<String, EventDetails> getEventDetails() {
		return eventDetails;
	}

	public void setEventDetails(LinkedHashMap<String, EventDetails> eventDetails) {
		this.eventDetails = eventDetails;
	}
}
