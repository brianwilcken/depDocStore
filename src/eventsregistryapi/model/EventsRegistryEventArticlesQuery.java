package eventsregistryapi.model;

public final class EventsRegistryEventArticlesQuery {
    private final String action = "getEvent";
    private final String resultType = "articles";
    private final String articlesCount = "100";
    private final String articlesIncludeArticleSocialScore = "true";
    private final String articlesIncludeArticleLocation = "true";
    private final String articlesIncludeSourceLocation = "true";
    private final String articlesArticleBodyLen = "-1";
    private String eventUri;
    private String apiKey;

    public EventsRegistryEventArticlesQuery(String eventUri, String apiKey){
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

	public String getResultType() {
		return resultType;
	}

	public String getArticlesCount() {
		return articlesCount;
	}

	public String getArticlesIncludeArticleSocialScore() {
		return articlesIncludeArticleSocialScore;
	}

	public String getArticlesIncludeArticleLocation() {
		return articlesIncludeArticleLocation;
	}

	public String getArticlesArticleBodyLen() {
		return articlesArticleBodyLen;
	}

	public String getArticlesIncludeSourceLocation() {
		return articlesIncludeSourceLocation;
	}
}
