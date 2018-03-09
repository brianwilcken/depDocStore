package eventsregistryapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Country {
	private String type;
	private Label label;
	private String wikiUri;
	private long lat;
	private long _long;

	public Country() {
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Label getLabel() {
		return label;
	}

	public void setLabel(Label label) {
		this.label = label;
	}

	public String getWikiUri() {
		return wikiUri;
	}

	public void setWikiUri(String wikiUri) {
		this.wikiUri = wikiUri;
	}

	public long getLat() {
		return lat;
	}

	public void setLat(long lat) {
		this.lat = lat;
	}

	public long get_long() {
		return _long;
	}

	public void set_long(long _long) {
		this._long = _long;
	}
}