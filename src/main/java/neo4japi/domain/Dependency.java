package neo4japi.domain;

import nlp.EntityRelation;
import org.neo4j.ogm.annotation.*;
import webapp.models.GeoNameWithFrequencyScore;

import java.util.List;

@RelationshipEntity(type = "Dependent_On")
public class Dependency {
    @Id
    @GeneratedValue
    private Long id;

    @StartNode
    private Facility dependentFacility;

    @EndNode
    private Facility providingFacility;

    private String dependencyTypeId;

    private String documentUUID;

    private String relationId;

    private String relation;

    private Boolean isCommitted;

    private Boolean isIgnored;

    private String committedDependentUUID;

    private String committedProvidingUUID;

    private Integer lineNumber;

    public Dependency(Facility dependentFacility, Facility providingFacility) {
        this.dependentFacility = dependentFacility;
        this.providingFacility = providingFacility;
    }

    public Dependency() {}

    public void consume(EntityRelation relation, GeoNameWithFrequencyScore loc, List<GeoNameWithFrequencyScore> geoNames) {
        dependentFacility = new Facility(relation.getSubjectEntity(), loc, geoNames);
        providingFacility = new Facility(relation.getObjectEntity(), loc, geoNames);
        this.relation = relation.getRelation();
        this.isCommitted = relation.getRelationState() != null && !relation.getRelationState().equals("IGNORED");
        this.isIgnored = relation.getRelationState() != null && relation.getRelationState().equals("IGNORED");
        this.dependencyTypeId = relation.getAssetUUID();
        this.committedDependentUUID = relation.getDependentFacilityUUID();
        this.committedProvidingUUID = relation.getProvidingFacilityUUID();
        this.lineNumber = relation.getLine();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Facility getDependentFacility() {
        return dependentFacility;
    }

    public void setDependentFacility(Facility dependentFacility) {
        this.dependentFacility = dependentFacility;
    }

    public Facility getProvidingFacility() {
        return providingFacility;
    }

    public void setProvidingFacility(Facility providingFacility) {
        this.providingFacility = providingFacility;
    }

    public String getDependencyTypeId() {
        return dependencyTypeId;
    }

    public void setDependencyTypeId(String dependencyTypeId) {
        this.dependencyTypeId = dependencyTypeId;
    }

    public String getDocumentUUID() {
        return documentUUID;
    }

    public void setDocumentUUID(String documentUUID) {
        this.documentUUID = documentUUID;
    }

    public String getRelationId() {
        return relationId;
    }

    public void setRelationId(String relationId) {
        this.relationId = relationId;
    }

    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }

    public Boolean getCommitted() {
        return isCommitted;
    }

    public void setCommitted(Boolean committed) {
        isCommitted = committed;
    }

    public Boolean getIgnored() {
        return isIgnored;
    }

    public void setIgnored(Boolean ignored) {
        isIgnored = ignored;
    }

    public String getCommittedDependentUUID() {
        return committedDependentUUID;
    }

    public void setCommittedDependentUUID(String committedDependentUUID) {
        this.committedDependentUUID = committedDependentUUID;
    }

    public String getCommittedProvidingUUID() {
        return committedProvidingUUID;
    }

    public void setCommittedProvidingUUID(String committedProvidingUUID) {
        this.committedProvidingUUID = committedProvidingUUID;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }
}
