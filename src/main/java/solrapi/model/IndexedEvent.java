package solrapi.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

import eventsregistryapi.model.Info;
import eventsregistryapi.model.Story;
import nlp.NLPTools;
import opennlp.tools.stemmer.Stemmer;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.common.SolrDocument;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import common.Tools;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.tokenize.TokenizerModel;

public class IndexedEvent extends IndexedObject implements Comparable<IndexedEvent> {
	@Field
	private String id;
	@Field
	private String parentId;
	@Field
	private String categorizationState;
	@Field
	private String eventState;
	@Field
	private String feedType;
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
	@Field
	private Boolean userCreated;
	@Field
	private String dashboard;
	@Field
	private String[] featureIds;

	private boolean conditionalUpdate;

	private List<IndexedEventSource> sources;

	private Map<String, Long> conceptsMap;

	private static ObjectMapper mapper = new ObjectMapper();

	public IndexedEvent() {
		conceptsMap = new HashMap<>();
	}

	public IndexedEvent(SolrDocument doc) {
		ConsumeSolr(doc);
	}

	public void initId() {
		try {
			if (uri != null) {
				id = Hex.encodeHexString(MessageDigest.getInstance("SHA-1").digest(mapper.writeValueAsBytes(this.getUri())));
			} else {
				id = UUID.randomUUID().toString();
			}
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

	//A previously indexed copy of this event may have fields that are not
	//communicated by the source from which this event arises (Data Capable, etc..).
	//Ensure this event is updated with such fields before re-indexing it to Solr.
	//updEvent is a previously indexed copy of this event.
	public void updateForDynamicFields(IndexedEvent updEvent) {
		this.setDashboard(updEvent.getDashboard());
		this.setFeatureIds(updEvent.getFeatureIds());
		this.setCategorizationState(updEvent.getCategorizationState());
		this.setEventState(updEvent.getEventState());
		this.setCategory(updEvent.getCategory());
	}

	public String[] GetDocCatTokens(TokenizerModel model, Stemmer stemmer) {
		String normalized = getNormalizedDocCatString(stemmer);
		String[] tokens = NLPTools.detectTokens(model, normalized);

		return tokens;
	}

	private String getNormalizedDocCatString(Stemmer stemmer) {
		String docCatStr = title + " " + summary + getConceptsString();
		docCatStr = docCatStr.replace("\r", " ").replace("\n", " ");

		return NLPTools.normalizeText(stemmer, docCatStr);
	}

    private String getNormalizedDocCatStringNoConcepts(Stemmer stemmer) {
        String docCatStr = title + " " + summary;
        docCatStr = docCatStr.replace("\r", " ")
                .replace("\n", " ")
                .replace(",", "");

        return NLPTools.normalizeText(stemmer, docCatStr);
    }

	private String getConceptsString() {
		if (this.getConcepts() != null && !this.getConcepts().isEmpty()) {
			HashMap<String,Long> conceptMap = new Gson().fromJson(this.getConcepts(), new TypeToken<HashMap<String, Long>>(){}.getType());
			StringBuilder str = new StringBuilder();

			List<String> conceptsForCat = conceptMap.entrySet().stream()
					.map(p -> p.getKey())
					.collect(Collectors.toList());

			return  " " + StringUtils.join(conceptsForCat, " ");
		}
		return "";
	}

	public void updateLastUpdatedDate() {
		this.setLastUpdated(Tools.getFormattedDateTimeString(Instant.now()));
	}

	public Map<String, Long> GetConceptsMap() {
		return conceptsMap;
	}

	public String GetModelTrainingForm() {
		return category + "\t" + getNormalizedDocCatString(new PorterStemmer());
	}

	public String GetLDAClusteringForm() {
		return id + "," + getNormalizedDocCatStringNoConcepts(new PorterStemmer());
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

	public Boolean getUserCreated() {
		return userCreated;
	}

	public void setUserCreated(Boolean userCreated) {
		this.userCreated = userCreated;
	}

	public String getFeedType() {
		return feedType;
	}

	public void setFeedType(String sourceType) {
		this.feedType = sourceType;
	}

	public String getDashboard() {
		return dashboard;
	}

	public void setDashboard(String dashboard) {
		this.dashboard = dashboard;
	}

	public String[] getFeatureIds() {
		return featureIds;
	}

	public void setFeatureIds(String[] featureIds) {
		this.featureIds = featureIds;
	}

	public List<IndexedEventSource> getSources() {
		return sources;
	}

	public void setSources(List<IndexedEventSource> sources) {
		this.sources = sources;
	}

	public boolean getConditionalUpdate() {
		return conditionalUpdate;
	}

	public void setConditionalUpdate(boolean conditionalUpdate) {
		this.conditionalUpdate = conditionalUpdate;
	}

	@Override
	public int compareTo(IndexedEvent o) {
		if (o.getSummary() == null && this.getSummary() == null) {
			return 0;
		} else if ((o.getSummary() != null && this.getSummary() == null) || (o.getSummary() == null && this.getSummary() != null)) {
			return -1;
		} else {
			return o.getSummary().compareTo(this.getSummary());
		}
	}
}
