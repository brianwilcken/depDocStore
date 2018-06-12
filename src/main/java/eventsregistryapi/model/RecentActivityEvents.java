package eventsregistryapi.model;

import java.util.LinkedHashMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class RecentActivityEvents {
    private String newestUpdate;
    private String oldestUpdate;
    private String[] activity;
    private LinkedHashMap<String, EventData> eventInfo;
	public String getNewestUpdate() {
		return newestUpdate;
	}
	public void setNewestUpdate(String newestUpdate) {
		this.newestUpdate = newestUpdate;
	}
	public String getOldestUpdate() {
		return oldestUpdate;
	}
	public void setOldestUpdate(String oldestUpdate) {
		this.oldestUpdate = oldestUpdate;
	}
	public String[] getActivity() {
		return activity;
	}
	public void setActivity(String[] activity) {
		this.activity = activity;
	}
	public LinkedHashMap<String, EventData> getEventInfo() {
		return eventInfo;
	}
	public void setEventInfo(LinkedHashMap<String, EventData> eventInfo) {
		this.eventInfo = eventInfo;
	}
}
