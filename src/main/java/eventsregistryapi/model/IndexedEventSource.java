package eventsregistryapi.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import com.google.common.base.Strings;
import org.apache.commons.codec.binary.Hex;
import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.common.SolrDocument;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
		} catch (JsonProcessingException | NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
