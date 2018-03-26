package eventsregistryapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EventsRegistryEventStreamResponse {
    private RecentActivityEvents recentActivityEvents;

	public RecentActivityEvents getRecentActivityEvents() {
		return recentActivityEvents;
	}

	public void setRecentActivityEvents(RecentActivityEvents recentActivityEvents) {
		this.recentActivityEvents = recentActivityEvents;
	}
}