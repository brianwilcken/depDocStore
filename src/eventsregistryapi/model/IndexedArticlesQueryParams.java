package eventsregistryapi.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexedArticlesQueryParams extends IndexedDocumentsQuery {
	private String[] eventUris;
	private int startDateRange;
	private int endDateRange;
	private int rows;
	
	public String getQuery() {
		String query = "articleDate:[NOW-" + Integer.toString(startDateRange) +  "DAY/DAY TO NOW";
		if (endDateRange > 0) {
			query += "-" + Integer.toString(endDateRange) + "DAY/DAY";
		}
		query += "]";
		
		return query;
	}
	
	public String[] getFacetedQueries() {
		List<String> fqs = new ArrayList<String>();
		
		fqs.add(getFacetedQuery("eventUri", eventUris));
		
		return fqs.toArray(new String[fqs.size()]);
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

	public int getRows() {
		return rows;
	}

	public void setRows(int rows) {
		this.rows = rows;
	}

	public String[] getEventUris() {
		return eventUris;
	}

	public void setEventUris(String[] eventUris) {
		this.eventUris = eventUris;
	}
}
