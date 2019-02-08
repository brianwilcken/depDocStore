package nlp;

import neo4japi.domain.Dependency;
import org.apache.solr.common.SolrDocument;
import solrapi.model.IndexedObject;
import webapp.models.GeoNameWithFrequencyScore;

import java.util.List;

public class EntityRelation extends IndexedObject {
    private NamedEntity subjectEntity;
    private NamedEntity objectEntity;
    private String relation;
    private String relationState;
    private String dependentFacilityUUID;
    private String providingFacilityUUID;
    private String assetUUID;
    private int line;
    private String id;

    public EntityRelation(NamedEntity subjectEntity, NamedEntity objectEntity, String relation, int line) {
        this.subjectEntity = subjectEntity;
        this.objectEntity = objectEntity;
        this.relation = relation;
        this.line = line;
    }

    public EntityRelation(SolrDocument relDoc, List<NamedEntity> docEntities) {
        consumeSolr(relDoc);
        String subjectEntityId = relDoc.get("subjectEntityId").toString();
        String objectEntityId = relDoc.get("objectEntityId").toString();
        for (NamedEntity namedEntitiy : docEntities) {
            if (namedEntitiy.getId().equals(subjectEntityId)) {
                subjectEntity = namedEntitiy;
            } else if (namedEntitiy.getId().equals(objectEntityId)) {
                objectEntity = namedEntitiy;
            }
        }
    }

    public SolrDocument mutateForSolr(String docId) {
        SolrDocument doc = new SolrDocument();
        doc.addField("docId", docId);
        doc.addField("subjectEntityId", subjectEntity.getId());
        doc.addField("objectEntityId", objectEntity.getId());
        doc.addField("relation", relation);
        doc.addField("line", line);

        return doc;
    }

    public Dependency mutateForNeo4j(GeoNameWithFrequencyScore loc, List<GeoNameWithFrequencyScore> geoNames) {
        Dependency dep = new Dependency();
        dep.consume(this, loc, geoNames);

        return dep;
    }

    public NamedEntity getSubjectEntity() {
        return subjectEntity;
    }

    public void setSubjectEntity(NamedEntity subjectEntity) {
        this.subjectEntity = subjectEntity;
    }

    public NamedEntity getObjectEntity() {
        return objectEntity;
    }

    public void setObjectEntity(NamedEntity objectEntity) {
        this.objectEntity = objectEntity;
    }

    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRelationState() {
        return relationState;
    }

    public void setRelationState(String relationState) {
        this.relationState = relationState;
    }

    public String getDependentFacilityUUID() {
        return dependentFacilityUUID;
    }

    public void setDependentFacilityUUID(String dependentFacilityUUID) {
        this.dependentFacilityUUID = dependentFacilityUUID;
    }

    public String getProvidingFacilityUUID() {
        return providingFacilityUUID;
    }

    public void setProvidingFacilityUUID(String providingFacilityUUID) {
        this.providingFacilityUUID = providingFacilityUUID;
    }

    public String getAssetUUID() {
        return assetUUID;
    }

    public void setAssetUUID(String assetUUID) {
        this.assetUUID = assetUUID;
    }
}
