package eventsregistryapi.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import solrapi.SolrConstants;
import solrapi.model.IndexedEvent;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Result {
    private long id;
    private String uri;
    private Concept concepts[];
    private String eventDate;
    private Title title;
    private Summary summary;
    private Location location;
    private Category categories[];
    private String[] images;
    private long totalArticleCount;
    private ArticleCounts articleCounts;
    private long wgt;
    
    private static ObjectMapper mapper = new ObjectMapper();
    
    public IndexedEvent GetIndexedEvent() {
    	IndexedEvent event = new IndexedEvent();
    	event.setUri(uri);
    	try {
    		Map<String, Long> conceptsMap = new HashMap<String, Long>();
    		for (Concept concept : concepts) {
    			conceptsMap.putIfAbsent(concept.getConceptLabelString(), concept.getScore());
    		}
			event.setConcepts(mapper.writeValueAsString(conceptsMap));
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	event.setEventDate(eventDate);
    	event.updateLastUpdatedDate();
    	event.setTitle(title.getEng());
    	event.setSummary(summary.getEng());
    	if (location != null) {
    		event.setLatitude(Double.toString(location.getLat()));
        	event.setLongitude(Double.toString(location.get_long()));
        	event.setLocation(location.getLabel().getEng());
    	}
    	else {
    		event.setLatitude("0.0");
        	event.setLongitude("0.0");
        	event.setLocation("Unknown");
    	}
    	event.setTotalArticleCount(totalArticleCount);
    	event.setImages(images);
    	event.setEventState(SolrConstants.Events.EVENT_STATE_NEW);
    	event.setFeedType(SolrConstants.Events.FEED_TYPE_MEDIA);
    	event.initId();
    	
    	return event;
    }
    
	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	public String getUri() {
		return uri;
	}
	public void setUri(String uri) {
		this.uri = uri;
	}
	public Concept[] getConcepts() {
		return concepts;
	}
	public void setConcepts(Concept[] concepts) {
		this.concepts = concepts;
	}
	public String getEventDate() {
		return eventDate;
	}
	public void setEventDate(String eventDate) {
		this.eventDate = eventDate;
	}
	public Title getTitle() {
		return title;
	}
	public void setTitle(Title title) {
		this.title = title;
	}
	public Summary getSummary() {
		return summary;
	}
	public void setSummary(Summary summary) {
		this.summary = summary;
	}
	public Location getLocation() {
		return location;
	}
	public void setLocation(Location location) {
		this.location = location;
	}
	public Category[] getCategories() {
		return categories;
	}
	public void setCategories(Category[] categories) {
		this.categories = categories;
	}
	public String[] getImages() {
		return images;
	}
	public void setImages(String[] images) {
		this.images = images;
	}
	public long getTotalArticleCount() {
		return totalArticleCount;
	}
	public void setTotalArticleCount(long totalArticleCount) {
		this.totalArticleCount = totalArticleCount;
	}
	public ArticleCounts getArticleCounts() {
		return articleCounts;
	}
	public void setArticleCounts(ArticleCounts articleCounts) {
		this.articleCounts = articleCounts;
	}
	public long getWgt() {
		return wgt;
	}
	public void setWgt(long wgt) {
		this.wgt = wgt;
	}
}
