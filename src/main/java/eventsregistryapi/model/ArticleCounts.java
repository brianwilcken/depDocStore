package eventsregistryapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ArticleCounts {
	private long eng;

	public ArticleCounts() {
	}

	public long getEng() {
		return eng;
	}

	public void setEng(long eng) {
		this.eng = eng;
	}
}