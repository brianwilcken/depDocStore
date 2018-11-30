package neo4japi.domain;

import org.neo4j.ogm.annotation.*;

@RelationshipEntity(type = "IsDataModelNode")
public class DataModelRelation {
    @Id
    @GeneratedValue
    private Long id;

    @StartNode
    private Entity entity;

    @EndNode
    private DataModelNode dataModelNode;

    public DataModelRelation(Entity entity, DataModelNode dataModelNode) {
        this.entity = entity;
        this.dataModelNode = dataModelNode;
    }

    public DataModelRelation() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DataModelNode getDataModelNode() {
        return dataModelNode;
    }

    public void setDataModelNode(DataModelNode dataModelNode) {
        this.dataModelNode = dataModelNode;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }
}
