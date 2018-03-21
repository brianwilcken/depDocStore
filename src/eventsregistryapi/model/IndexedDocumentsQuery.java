package eventsregistryapi.model;

import java.util.Arrays;

public abstract class IndexedDocumentsQuery {
	protected String getFacetedQuery(String type, String[] params) {
		if (params != null) {
			String fq = Arrays.stream(params).map(p -> type + ":" + p).reduce((c, n) -> c + " OR " + n).get();
			return fq;
		}
		else {
			return "";
		}
	}
}
