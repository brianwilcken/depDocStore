package eventsregistryapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Location {
	private String type;
	private String wikiUri;
	private Label label;
	private double lat;
	@JsonProperty("long")
    private double _long;
	private Country country;

	public Location() {
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

	public Country getCountry() {
		return country;
	}

	public void setCountry(Country country) {
		this.country = country;
	}

	public String getWikiUri() {
		return wikiUri;
	}

	public void setWikiUri(String wikiUri) {
		this.wikiUri = wikiUri;
	}

	public double getLat() {
		return lat;
	}

	public void setLat(double lat) {
		this.lat = lat;
	}

	public double get_long() {
		return _long;
	}

	public void set_long(double _long) {
		this._long = _long;
	}
}