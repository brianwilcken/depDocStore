package eventsregistryapi.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.common.SolrDocument;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class IndexedEvent {
	@Field
	private String id;
	@Field
	private String parentId;
	@Field
	private String categorizationState; 
	@Field
	private String eventState;
	@Field
	private String uri;
	@Field
	private String concepts;
	@Field
	private String eventDate;
	@Field 
	private String lastUpdated;
	@Field
	private String title;
	@Field
	private String summary;
	@Field
	private String category;
	@Field
	private String latitude;
	@Field
	private String longitude;
	@Field
	private String location;
	@Field
	private String url;
	@Field
	private String[] images;
	@Field
	private long totalArticleCount;
	
	private Map<String, Long> conceptsMap;
	
	private static ObjectMapper mapper = new ObjectMapper();
	
	public IndexedEvent() {
		conceptsMap = new HashMap<String, Long>();
	}
	
	public IndexedEvent(SolrDocument doc) {
		Arrays.stream(this.getClass().getDeclaredFields()).forEach(p -> {
			if(doc.containsKey(p.getName())) {
				Object value = doc.get(p.getName());
				try {
					if (value instanceof String || value instanceof Long) {
						p.set(this, value);
					} else if (value instanceof List) {
						List<String> lst = (List<String>)value;
						String[] arr = lst.toArray(new String[lst.size()]);
						p.set(this, arr);
					} else {
						String date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format((Date)value);
						p.set(this, date);
					}
				} catch (IllegalArgumentException | IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}
	
	public void initId() {
		try {
			id = Hex.encodeHexString(MessageDigest.getInstance("SHA-1").digest(mapper.writeValueAsBytes(
				this.getUri() + 
				this.getTitle() + 
				this.getSummary() + 
				this.getLocation())));
		} catch (JsonProcessingException | NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public IndexedEvent updateWithEventDetails(Info eventInfo) {
		try {
			Story story = Arrays.stream(eventInfo.getStories())
				.filter(p -> p.getMedoidArticle().getLang().compareTo("eng") == 0)
				.findFirst()
				.get();
			this.setUrl(story.getMedoidArticle().getUrl());
			this.setImages(eventInfo.getImages());
			this.setLatitude(Double.toString(eventInfo.getLocation().getLat()));
			this.setLongitude(Double.toString(eventInfo.getLocation().get_long()));
		} catch (NoSuchElementException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return this;
	}
	
	public String GetDocCatForm() {
		HashMap<String,Long> conceptMap = new Gson().fromJson(this.getConcepts(), new TypeToken<HashMap<String, Long>>(){}.getType());
		
		List<String> conceptsForCat = conceptMap.entrySet().stream()
				.map(p -> p.getKey())
				.collect(Collectors.toList());
		
		String docCatStr = title + ": " + summary + " (" + StringUtils.join(conceptsForCat, " ") + ")";
		
		return docCatStr.replace("\r", " ").replace("\n", " ");
	}
	
	public void updateLastUpdatedDate() {
		this.setLastUpdated(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z");
	}
	
	public Map<String, Long> GetConceptsMap() {
		return conceptsMap;
	}
	
	public String GetModelTrainingForm() {
		return category + "\t" + GetDocCatForm();
	}
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getParentId() {
		return parentId;
	}

	public void setParentId(String parentId) {
		this.parentId = parentId;
	}

	public String getEventState() {
		return eventState;
	}

	public void setEventState(String eventState) {
		this.eventState = eventState;
	}

	public String getCategorizationState() {
		return categorizationState;
	}

	public void setCategorizationState(String state) {
		this.categorizationState = state;
	}

	public String getUri() {
		return uri;
	}
	public void setUri(String uri) {
		this.uri = uri;
	}
	public String getConcepts() {
		return concepts;
	}

	public void setConcepts(String concepts) {
		this.concepts = concepts;
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
	public long getTotalArticleCount() {
		return totalArticleCount;
	}
	public void setTotalArticleCount(long totalArticleCount) {
		this.totalArticleCount = totalArticleCount;
	}
	public String getCategory() {
		return category;
	}
	public void setCategory(String category) {
		this.category = category;
	}
	public String getLatitude() {
		return latitude;
	}
	public void setLatitude(String latitude) {
		this.latitude = latitude;
	}
	public String getLongitude() {
		return longitude;
	}
	public void setLongitude(String longitude) {
		this.longitude = longitude;
	}
	public String getLocation() {
		return location;
	}
	public void setLocation(String location) {
		this.location = location;
	}
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public String[] getImages() {
		return images;
	}
	public void setImages(String[] images) {
		this.images = images;
	}

	public String getEventDate() {
		return eventDate;
	}

	public void setEventDate(String eventDate) {
		this.eventDate = eventDate;
	}

	public String getLastUpdated() {
		return lastUpdated;
	}

	public void setLastUpdated(String lastUpdated) {
		this.lastUpdated = lastUpdated;
	}
}
