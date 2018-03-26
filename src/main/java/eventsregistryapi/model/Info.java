package eventsregistryapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Info {
    private long id;
    private String uri;
    private Concept concepts[];
    private Story stories[];
    private String eventDate;
    private Title title;
    private Summary summary;
    private Location location;
    private Category categories[];
    private String[] images;
    private long totalArticleCount;
    private ArticleCounts articleCounts;
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
	public Story[] getStories() {
		return stories;
	}
	public void setStories(Story[] stories) {
		this.stories = stories;
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
}
