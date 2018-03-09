package eventsregistryapi.model;

public final class EventsRegistryEventDetailsQuery {
    private final String action = "getEvent";
    private final String infoConceptLang = "eng";
    private final String infoEventImageCount = "1";
    private final String infoIncludeEventDetails = "true";
    private final String infoIncludeEventInfoArticle = "true";
    private final String infoIncludeEventStories = "true";
    private final String infoIncludeLocationGeoLocation = "true";
    private final String infoIncludeLocationWikiUri = "true";
    private final String infoIncludeStoryAllDetails = "false";
    private final String infoIncludeStoryBasicStats = "false";
    private final String infoIncludeStoryDate = "false";
    private final String infoIncludeStoryFlags = "true";
    private final String infoIncludeStoryMedoidArticle = "true";
    private final String resultType = "info";
    private String eventUri;
    private String apiKey;

    public EventsRegistryEventDetailsQuery(String eventUri, String apiKey){
        this.eventUri = eventUri;
        this.apiKey = apiKey;
    }

	public String getEventUri() {
		return eventUri;
	}

	public void setEventUri(String eventUri) {
		this.eventUri = eventUri;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getAction() {
		return action;
	}

	public String getInfoConceptLang() {
		return infoConceptLang;
	}

	public String getInfoEventImageCount() {
		return infoEventImageCount;
	}

	public String getInfoIncludeEventDetails() {
		return infoIncludeEventDetails;
	}

	public String getInfoIncludeEventInfoArticle() {
		return infoIncludeEventInfoArticle;
	}

	public String getInfoIncludeEventStories() {
		return infoIncludeEventStories;
	}

	public String getInfoIncludeLocationGeoLocation() {
		return infoIncludeLocationGeoLocation;
	}

	public String getInfoIncludeLocationWikiUri() {
		return infoIncludeLocationWikiUri;
	}

	public String getInfoIncludeStoryAllDetails() {
		return infoIncludeStoryAllDetails;
	}

	public String getInfoIncludeStoryBasicStats() {
		return infoIncludeStoryBasicStats;
	}

	public String getInfoIncludeStoryDate() {
		return infoIncludeStoryDate;
	}

	public String getInfoIncludeStoryFlags() {
		return infoIncludeStoryFlags;
	}

	public String getInfoIncludeStoryMedoidArticle() {
		return infoIncludeStoryMedoidArticle;
	}

	public String getResultType() {
		return resultType;
	}
}
