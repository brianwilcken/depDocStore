package eventsregistryapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Story {
	private String uri;
    private String id;
    private Location location;
    private boolean isUserSetLocation;
    private MedoidArticle medoidArticle;
    private long flags;
    private long wgt;
	public String getUri() {
		return uri;
	}
	public void setUri(String uri) {
		this.uri = uri;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public Location getLocation() {
		return location;
	}
	public void setLocation(Location location) {
		this.location = location;
	}
	public boolean isUserSetLocation() {
		return isUserSetLocation;
	}
	public void setUserSetLocation(boolean isUserSetLocation) {
		this.isUserSetLocation = isUserSetLocation;
	}
	public MedoidArticle getMedoidArticle() {
		return medoidArticle;
	}
	public void setMedoidArticle(MedoidArticle medoidArticle) {
		this.medoidArticle = medoidArticle;
	}
	public long getFlags() {
		return flags;
	}
	public void setFlags(long flags) {
		this.flags = flags;
	}
	public long getWgt() {
		return wgt;
	}
	public void setWgt(long wgt) {
		this.wgt = wgt;
	}
}
