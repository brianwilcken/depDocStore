package eventsregistryapi.model;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

import common.Tools;

public abstract class IndexedDocumentsQuery {
	protected final String queryTimeStamp = Tools.getFormattedDateTimeString(LocalDateTime.now());
	
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
			String unixEpoch = Tools.getFormattedDateTimeString(LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC));
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
		return Tools.getFormattedDateTimeString(LocalDateTime.now().minusDays(numDaysPrevious[0]));
	}
	
	public String getQueryTimeStamp() {
		return queryTimeStamp;
	}
}
