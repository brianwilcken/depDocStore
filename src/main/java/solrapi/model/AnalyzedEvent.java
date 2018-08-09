package solrapi.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "index", "id", "title", "cluster", "distance", "category" })
public class AnalyzedEvent {
    public String index;
    public String id;
    public String title;
    public String cluster;
    public double distance;
    public String category;
}
