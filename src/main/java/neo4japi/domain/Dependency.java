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

    private String relation;

    public Dependency(Facility dependentFacility, Facility providingFacility) {
        this.dependentFacility = dependentFacility;
        this.providingFacility = providingFacility;
    }

    public Dependency() {}

    public void consume(EntityRelation relation, GeoNameWithFrequencyScore loc, List<GeoNameWithFrequencyScore> geoNames) {
        dependentFacility = new Facility(relation.getSubjectEntity().getEntity(), loc, geoNames);
        providingFacility = new Facility(relation.getObjectEntity().getEntity(), loc, geoNames);
        this.relation = relation.getRelation();
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

    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }
}
