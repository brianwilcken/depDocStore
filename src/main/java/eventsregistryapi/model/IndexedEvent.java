package eventsregistryapi.model;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.common.SolrDocument;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import common.Tools;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

public class IndexedEvent extends IndexedObject {
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

	private Map<String, Long> conceptsMap;
	
	private static ObjectMapper mapper = new ObjectMapper();
	
	public IndexedEvent() {
		conceptsMap = new HashMap<String, Long>();
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
	
	public String[] GetDocCatTokens(TokenizerModel model, PorterStemmer stemmer) {
		String normalized = getNormalizedDocCatString(stemmer);
		
		//Tokenize
		TokenizerME tokenDetector = new TokenizerME(model);
		String[] tokens = tokenDetector.tokenize(normalized);

		return tokens;
	}
	
	private String getNormalizedDocCatString(PorterStemmer stemmer) {
		String docCatStr = title + " " + summary + getConceptsString();
		docCatStr = docCatStr.replace("\r", " ").replace("\n", " ");
		
		//Normalize, lemmatize and remove stop words
		StandardAnalyzer analyzer = new StandardAnalyzer();
		String normalized = analyzer.normalize("", docCatStr).utf8ToString();
		TokenStream stream = analyzer.tokenStream("", normalized);
		StopFilter filter = new StopFilter(stream, analyzer.getStopwordSet());
		StringBuilder str = new StringBuilder();
		try {
			stream.reset();
			while(filter.incrementToken()) {
				CharTermAttribute attr = filter.getAttribute(CharTermAttribute.class);
				str.append(stemmer.stem(attr.toString()) + " ");
			}
			analyzer.close();
			filter.end();
			filter.close();
			stream.end();
			stream.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return str.toString();
	}
	
	private String getConceptsString() {
		if (this.getConcepts() != null) {
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
		this.setLastUpdated(Tools.getFormattedDateTimeString(LocalDateTime.now()));
	}
	
	public Map<String, Long> GetConceptsMap() {
		return conceptsMap;
	}
	
	public String GetModelTrainingForm() {
		return category + "\t" + getNormalizedDocCatString(new PorterStemmer());
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
}
