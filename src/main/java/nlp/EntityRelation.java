package nlp;

import neo4japi.domain.Dependency;
import org.apache.solr.common.SolrDocument;
import webapp.models.GeoNameWithFrequencyScore;

public class EntityRelation {
    private NamedEntity subjectEntity;
    private NamedEntity objectEntity;
    private String relation;
    private int lineNum;

    public EntityRelation(NamedEntity subjectEntity, NamedEntity objectEntity, String relation, int lineNum) {
        this.subjectEntity = subjectEntity;
        this.objectEntity = objectEntity;
        this.relation = relation;
        this.lineNum = lineNum;
    }

    public SolrDocument mutateForSolr(String docId) {
        SolrDocument doc = new SolrDocument();
        doc.addField("docId", docId);
        doc.addField("subjectEntityId", subjectEntity.getId());
        doc.addField("objectEntityId", objectEntity.getId());
        doc.addField("relation", relation);
        doc.addField("line", lineNum);

        return doc;
    }

    public Dependency mutateForNeo4j(GeoNameWithFrequencyScore loc) {
        Dependency dep = new Dependency();
        dep.consume(this, loc);

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

    public int getLineNum() {
        return lineNum;
    }

    public void setLineNum(int lineNum) {
        this.lineNum = lineNum;
    }
}
