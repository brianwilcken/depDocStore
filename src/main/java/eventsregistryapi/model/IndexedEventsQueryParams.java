package eventsregistryapi.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexedEventsQueryParams extends IndexedDocumentsQuery {
	private String[] uris;
	private int[] numDaysPrevious;
	private String[] startDate;
	private String[] endDate;
	private String[] categories;
	private String[] eventStates;
	private String[] categorizationStates;
	private int[] minArticleCount;
	private int[] rows;
	
	public String getQuery() {
		return getTimeRangeQuery(startDate, endDate, numDaysPrevious);
	}

	public String[] getFacetedQueries() {
		List<String> fqs = new ArrayList<String>();
		
		fqs.add(getFacetedQuery("uri", uris));
		fqs.add(getFacetedQuery("category", categories));
		fqs.add(getFacetedQuery("eventState", eventStates));
		fqs.add(getFacetedQuery("categorizationState", categorizationStates));
		if (minArticleCount !=  null && minArticleCount.length > 0) {
			fqs.add("totalArticleCount:[" + Integer.toString(minArticleCount[0]) + " TO *]");
		}
		
		return fqs.toArray(new String[fqs.size()]);
	}
	
	public int getQueryRows() {
		int rows;
		if (getRows() != null && getRows().length > 0) {
			rows = getRows()[0];
		} else {
			rows = Integer.MAX_VALUE;
		}
		return rows;
	}
	
	public String[] getUris() {
		return uris;
	}
	public void setUris(String[] uris) {
		this.uris = uris;
	}
	public int[] getNumDaysPrevious() {
		return numDaysPrevious;
	}
	public void setNumDaysPrevious(int[] numDaysPrevious) {
		this.numDaysPrevious = numDaysPrevious;
	}
	public String[] getStartDate() {
		return startDate;
	}
	public void setStartDate(String[] startDate) {
		this.startDate = startDate;
	}
	public String[] getEndDate() {
		return endDate;
	}
	public void setEndDate(String[] endDate) {
		this.endDate = endDate;
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
	public int[] getMinArticleCount() {
		return minArticleCount;
	}
	public void setMinArticleCount(int[] minArticleCount) {
		this.minArticleCount = minArticleCount;
	}

	public int[] getRows() {
		return rows;
	}

	public void setRows(int[] rows) {
		this.rows = rows;
	}
}
