package eventsregistryapi.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexedEventsQueryParams extends IndexedDocumentsQuery {
	private String[] uris;
	private int startDateRange;
	private int endDateRange;
	private String[] categories;
	private String[] eventStates;
	private String[] categorizationStates;
	private int minArticleCount;
	private int rows;
	
	public String getQuery() {
		String query = "eventDate:[NOW-" + Integer.toString(startDateRange) +  "DAY/DAY TO NOW";
		if (endDateRange > 0) {
			query += "-" + Integer.toString(endDateRange) + "DAY/DAY";
		}
		query += "]";
		
		return query;
	}
	
	public String[] getFacetedQueries() {
		List<String> fqs = new ArrayList<String>();
		
		fqs.add(getFacetedQuery("uri", uris));
		fqs.add(getFacetedQuery("category", categories));
		fqs.add(getFacetedQuery("eventState", eventStates));
		fqs.add(getFacetedQuery("categorizationState", categorizationStates));
		fqs.add("totalArticleCount:[" + Integer.toString(minArticleCount) + " TO *]");
		
		return fqs.toArray(new String[fqs.size()]);
	}
	
	public String[] getUris() {
		return uris;
	}
	public void setUris(String[] uris) {
		this.uris = uris;
	}
	public int getStartDateRange() {
		return startDateRange;
	}
	public void setStartDateRange(int startDateRange) {
		this.startDateRange = startDateRange;
	}
	public int getEndDateRange() {
		return endDateRange;
	}
	public void setEndDateRange(int endDateRange) {
		this.endDateRange = endDateRange;
	}
	public String[] getCategories() {
		return categories;
	}
	public void setCategories(String[] categories) {
		this.categories = categories;
	}
	public String[] getEventStates() {
		return eventStates;
	}
	public void setEventStates(String[] eventStates) {
		this.eventStates = eventStates;
	}
	public String[] getCategorizationStates() {
		return categorizationStates;
	}
	public void setCategorizationStates(String[] categorizationStates) {
		this.categorizationStates = categorizationStates;
	}
	public int getMinArticleCount() {
		return minArticleCount;
	}
	public void setMinArticleCount(int minArticleCount) {
		this.minArticleCount = minArticleCount;
	}

	public int getRows() {
		return rows;
	}

	public void setRows(int rows) {
		this.rows = rows;
	}
}
