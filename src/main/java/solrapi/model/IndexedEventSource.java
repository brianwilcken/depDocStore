package solrapi.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.google.common.base.Strings;
import org.apache.commons.codec.binary.Hex;
import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.common.SolrDocument;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import solrapi.SolrConstants;

public class IndexedEventSource extends IndexedObject {
	@Field
	private String id;
	@Field
	private String uri;
	@Field
	private String eventId;
	@Field
	private String articleDate;
	@Field
	private String url;
	@Field
	private String title;
	@Field
	private String summary;
	@Field
	private String sourceUri;
	@Field
	private String sourceName;
	@Field
	private String sourceLocation;
	
	private static ObjectMapper mapper = new ObjectMapper();
	
	public IndexedEventSource() {
	}
	
	public IndexedEventSource(SolrDocument doc) {
		ConsumeSolr(doc);
	}
	
	public void initId() {
		try {
			if (Strings.isNullOrEmpty(this.getEventId())) {
				id = Hex.encodeHexString(MessageDigest.getInstance("SHA-1").digest(mapper.writeValueAsBytes(
						this.getSourceName() +
								this.getTitle() +
								this.getSummary() +
								this.getUrl())));
			} else {
				if (!Strings.isNullOrEmpty(this.getUri())) {
					id = Hex.encodeHexString(MessageDigest.getInstance("SHA-1").digest(mapper.writeValueAsBytes(
							this.getEventId() +
									this.getTitle() +
									this.getSummary() +
									this.getUri())));
				} else {
					id = Hex.encodeHexString(MessageDigest.getInstance("SHA-1").digest(mapper.writeValueAsBytes(
							this.getEventId() +
									this.getSourceName() +
									this.getUrl() +
									this.getArticleDate())));
				}
			}
		} catch (JsonProcessingException | NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public IndexedEvent getIndexedEvent() {
		IndexedEvent event = new IndexedEvent();
		event.setCategorizationState(SolrConstants.Events.CATEGORIZATION_STATE_MACHINE);
		event.setEventState(SolrConstants.Events.EVENT_STATE_NEW);
		event.setFeedType(SolrConstants.Events.FEED_TYPE_MEDIA);
		event.setUri(sourceName + "_" + url);
		event.setConcepts("{\"Web_Scraped\":100}");
		event.setEventDate(articleDate);
		event.updateLastUpdatedDate();
		event.setTitle(title);
		event.setSummary(summary);
		event.setUrl(url);
		event.setTotalArticleCount(1);
		event.setUserCreated(false);
		event.initId();

		eventId = event.getId();

		return event;
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

	public String getEventId() {
		return eventId;
	}
	public void setEventId(String eventId) {
		this.eventId = eventId;
	}
	public String getArticleDate() {
		return articleDate;
	}
	public void setArticleDate(String articleDate) {
		this.articleDate = articleDate;
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
	public String getSummary() {
		return summary;
	}
	public void setSummary(String summary) {
		this.summary = summary;
	}
	public String getSourceUri() {
		return sourceUri;
	}
	public void setSourceUri(String sourceUri) {
		this.sourceUri = sourceUri;
	}
	public String getSourceName() {
		return sourceName;
	}
	public void setSourceName(String sourceName) {
		this.sourceName = sourceName;
	}
	public String getSourceLocation() {
		return sourceLocation;
	}
	public void setSourceLocation(String sourceLocation) {
		this.sourceLocation = sourceLocation;
	}
}
