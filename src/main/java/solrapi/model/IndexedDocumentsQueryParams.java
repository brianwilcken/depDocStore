package solrapi.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Strings;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexedDocumentsQueryParams extends IndexedDocumentsQuery {
    private int[] numDaysPrevious;
    private String[] startDate;
    private String[] endDate;
    private int[] rows;
    private int[] page;
    private String[] filename;
    private String[] docText;
    private String[] url;
    private String[] category;
    private String[] project;
    private String[] organization;
    private String[] id;
    private String[] fields;
    private String[] sortColumn;
    private String[] sortDirection;

    public String getQuery() {
        StringBuilder query = new StringBuilder();

        String timeRangeQuery = getTimeRangeQuery("created", startDate, endDate, numDaysPrevious);
        query.append(timeRangeQuery);

        String[] filterQueries = getFilterQueries();
        for (String filterQuery : filterQueries) {
            if (!Strings.isNullOrEmpty(filterQuery)) {
                query.append(" AND " + filterQuery);
            }
        }

        return query.toString();
    }

    public String[] getFilterQueries() {
        List<String> fqs = new ArrayList<String>();

        fqs.add(getFilterQuery("filename", filename));
        fqs.add(getFilterQuery("docText", docText));
        fqs.add(getFilterQuery("url", url));
        fqs.add(getFilterQuery("category", category));
        fqs.add(getFilterQuery("project", project));
        fqs.add(getFilterQuery("organization", organization));
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
        if (getPage() != null && getPage().length > 0) {
            return (getPage()[0] - 1) * getQueryRows();
        } else {
            return 0;
        }
    }

    public String getSortColumn() {
        if (sortColumn != null && sortColumn.length > 0) {
            return sortColumn[0];
        } else {
            return "lastUpdated";
        }
    }

    public String getSortDirection() {
        if (sortDirection != null && sortDirection.length > 0) {
            return sortDirection[0];
        } else {
            return "desc";
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

    public int[] getPage() {
        return page;
    }

    public void setPage(int[] page) {
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

    public String[] getFilename() {
        return filename;
    }

    public void setFilename(String[] filename) {
        this.filename = filename;
    }

    public String[] getUrl() {
        return url;
    }

    public void setUrl(String[] url) {
        this.url = url;
    }

    public String[] getCategory() {
        return category;
    }

    public void setCategory(String[] category) {
        this.category = category;
    }

    public String[] getProject() {
        return project;
    }

    public void setProject(String[] project) {
        this.project = project;
    }

    public String[] getOrganization() {
        return organization;
    }

    public void setOrganization(String[] organization) {
        this.organization = organization;
    }

    public void setSortColumn(String[] sortColumn) {
        this.sortColumn = sortColumn;
    }

    public void setSortDirection(String[] sortDirection) {
        this.sortDirection = sortDirection;
    }
}

