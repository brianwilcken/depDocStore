package eventsregistryapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import solrapi.model.IndexedEventSource;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MedoidArticle {
	private String id;
    private String uri;
    private String lang;
    private boolean isDuplicate;
    private String date;
    private String time;
    private String dateTime;
    private double sim;
    private String url;
    private String title;
    private String body;
    private Source source;
    private String eventUri;

	public IndexedEventSource getIndexedEventSource(String eventId) {
		IndexedEventSource source = new IndexedEventSource();

		source.setUri(uri);
		source.setEventId(eventId);
		source.setArticleDate(dateTime);
		source.setUrl(url);
		source.setTitle(title);
		source.setSummary(body);
		if (this.source != null) {
			source.setSourceUri(this.source.getUri());
			source.setSourceName(this.source.getTitle());
			if (this.source.getLocation() != null) {
				source.setSourceLocation(this.source.getLocation().getLabel().getEng());
			}
		}
		source.initId();

		return source;
	}

	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getUri() {
		return uri;
	}
	public void setUri(String uri) {
		this.uri = uri;
	}
	public String getLang() {
		return lang;
	}
	public void setLang(String lang) {
		this.lang = lang;
	}
	public boolean isDuplicate() {
		return isDuplicate;
	}
	public void setDuplicate(boolean isDuplicate) {
		this.isDuplicate = isDuplicate;
	}
	public String getDate() {
		return date;
	}
	public void setDate(String date) {
		this.date = date;
	}
	public String getTime() {
		return time;
	}
	public void setTime(String time) {
		this.time = time;
	}
	public String getDateTime() {
		return dateTime;
	}
	public void setDateTime(String dateTime) {
		this.dateTime = dateTime;
	}
	public double getSim() {
		return sim;
	}
	public void setSim(double sim) {
		this.sim = sim;
	}
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getBody() {
		return body;
	}
	public void setBody(String body) {
		this.body = body;
	}
	public Source getSource() {
		return source;
	}
	public void setSource(Source source) {
		this.source = source;
	}
	public String getEventUri() {
		return eventUri;
	}
	public void setEventUri(String eventUri) {
		this.eventUri = eventUri;
	}
}
