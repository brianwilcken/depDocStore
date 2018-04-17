package eventsregistryapi.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAmount;
import java.util.Arrays;

import common.Tools;

public abstract class IndexedDocumentsQuery {
	protected final String queryTimeStamp = Tools.getFormattedDateTimeString(Instant.now());
	
	protected String getFacetedQuery(String type, String[] params) {
		if (params != null) {
			String fq = Arrays.stream(params).map(p -> type + ":" + p).reduce((c, n) -> c + " OR " + n).get();
			return fq;
		}
		else {
			return "";
		}
	}
	
	protected String getTimeRangeQuery(String[] startDate, String[] endDate, int[] numDaysPrevious) {
		String query;
		
		if (startDate == null && endDate == null && numDaysPrevious != null) {
			query = "lastUpdated:[" + getPreviousDate(numDaysPrevious) + " TO NOW]";
			return query;
		}
		
		if (startDate != null && startDate.length > 0) {
			query = "lastUpdated:[" + startDate[0] +  " TO ";
		} else {
			String unixEpoch = Tools.getFormattedDateTimeString(Instant.ofEpochSecond(0, 0));
			query = "lastUpdated:[" + unixEpoch +  " TO " + queryTimeStamp + "]";
			return query;
		}
		
		if (endDate != null && endDate.length > 0) {
			query += endDate[0];
		} else {
			query += queryTimeStamp;
		}
		
		query += "]";
		
		return query;
	}
	
	private String getPreviousDate(int[] numDaysPrevious) {
		return Tools.getFormattedDateTimeString(Instant.now().minus(Period.ofDays(numDaysPrevious[0])));
	}
	
	public String getQueryTimeStamp() {
		return queryTimeStamp;
	}
}
