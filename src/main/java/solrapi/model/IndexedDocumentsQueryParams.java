package solrapi.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexedDocumentsQueryParams extends IndexedDocumentsQuery {
    private int[] numDaysPrevious;
    private String[] startDate;
    private String[] endDate;
    private int[] rows;
    private int[] page;
    private String[] docText;
    private String[] id;
    private String[] fields;

    public String getQuery() {
        return getTimeRangeQuery("created", startDate, endDate, numDaysPrevious);
    }

    public String[] getFilterQueries() {
        List<String> fqs = new ArrayList<String>();

        fqs.add(getFilterQuery("docText", docText));
        fqs.add(getFilterQuery("id", id));

        return fqs.toArray(new String[fqs.size()]);
    }

    public int getQueryRows() {
        if (getRows() != null && getRows().length > 0) {
            return getRows()[0];
        } else {
            return Integer.MAX_VALUE;
        }
    }

    public int getQueryStart() {
        if (getPageNum() != null && getPageNum().length > 0) {
            return (getPageNum()[0] - 1) * getQueryRows();
        } else {
            return 0;
        }
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

    public int[] getRows() {
        return rows;
    }

    public void setRows(int[] rows) {
        this.rows = rows;
    }


    public int[] getPageNum() {
        return page;
    }

    public void setPageNum(int[] page) {
        this.page = page;
    }

    public String[] getId() {
        return id;
    }

    public void setId(String[] id) {
        this.id = id;
    }

    public String[] getDocText() {
        return docText;
    }

    public void setDocText(String[] docText) {
        this.docText = docText;
    }

    public String[] getFields() {
        return fields;
    }

    public void setFields(String[] fields) {
        this.fields = fields;
    }
}

