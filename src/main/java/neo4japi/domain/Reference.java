package neo4japi.domain;

import org.neo4j.ogm.annotation.*;

@RelationshipEntity(type = "Refers_To")
public class Reference {
    @Id
    @GeneratedValue
    private Long id;

    @StartNode
    private Document document;

    @EndNode
    private Facility facility;

    public Reference(Document document, Facility facility) {
        this.document = document;
        this.facility = facility;
    }

    public Reference() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public Facility getFacility() {
        return facility;
    }

    public void setFacility(Facility facility) {
        this.facility = facility;
    }
}
