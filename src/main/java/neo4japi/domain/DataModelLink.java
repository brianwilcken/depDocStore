package neo4japi.domain;

import org.neo4j.ogm.annotation.NodeEntity;

@NodeEntity
public class DataModelLink extends Entity {

    private String UUID;
    private Boolean isBaseLink;
    private String name;
    private Boolean dependencyProduct;

    public DataModelLink() {
        this.UUID = java.util.UUID.randomUUID().toString();
    }

    public String getUUID() {
        return UUID;
    }

    public void setUUID(String UUID) {
        this.UUID = UUID;
    }

    public Boolean getBaseLink() {
        return isBaseLink;
    }

    public void setBaseLink(Boolean baseLink) {
        isBaseLink = baseLink;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getDependencyProduct() {
        return dependencyProduct;
    }

    public void setDependencyProduct(Boolean dependencyProduct) {
        this.dependencyProduct = dependencyProduct;
    }
}
