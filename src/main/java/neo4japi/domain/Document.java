package neo4japi.domain;

import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import java.util.HashSet;
import java.util.Set;

@NodeEntity
public class Document extends Entity {

    private String UUID;
    private String solrId;
    private String title;

    @Relationship(type = "Refers_To")
    private Set<Facility> facilities;

    public Document(String solrId, String title) {
        this.UUID = java.util.UUID.randomUUID().toString();
        this.solrId = solrId;
        this.title = title;
        facilities = new HashSet<>();
    }

    public String getUUID() {
        return UUID;
    }

    public void setUUID(String UUID) {
        this.UUID = UUID;
    }

    public Document() {
        facilities = new HashSet<>();
    }

    public String getSolrId() {
        return solrId;
    }

    public void setSolrId(String solrId) {
        this.solrId = solrId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Set<Facility> getFacilities() {
        return facilities;
    }

    public void setFacilities(Set<Facility> facilities) {
        this.facilities = facilities;
    }
}
