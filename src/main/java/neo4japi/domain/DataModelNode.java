package neo4japi.domain;

import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import java.util.HashSet;
import java.util.Set;

@NodeEntity
public class DataModelNode extends Entity {

    private String UUID;
    private Boolean isBaseNode;
    private String name;

    public DataModelNode() {
        this.UUID = java.util.UUID.randomUUID().toString();
    }

    public String getUUID() {
        return UUID;
    }

    public void setUUID(String UUID) {
        this.UUID = UUID;
    }

    public Boolean getBaseNode() {
        return isBaseNode;
    }

    public void setBaseNode(Boolean baseNode) {
        isBaseNode = baseNode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
