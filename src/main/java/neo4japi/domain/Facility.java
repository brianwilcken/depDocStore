package neo4japi.domain;

import org.apache.commons.math3.ml.clustering.Clusterable;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

import java.util.HashSet;
import java.util.Set;

@NodeEntity
public class Facility extends Entity implements Clusterable {

    private String name;
    private String location;
    private double latitude;
    private double longitude;

    @Relationship(type = "DEPENDS_ON", direction = Relationship.INCOMING)
    private Set<Facility> downstreamDependencies; //facilities that depend on this facility

    @Relationship(type = "DEPENDS_ON")
    private Set<Facility> upstreamDependencies; //facilities that this facility depends on

    @Relationship(type = "REFERS_TO", direction = Relationship.INCOMING)
    private Set<Document> referringDocuments;

    public Facility(String name, String location, double latitude, double longitude) {
        this.name = name;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
        downstreamDependencies = new HashSet<>();
        upstreamDependencies = new HashSet<>();
        referringDocuments = new HashSet<>();
    }

    public Facility() {
//        downstreamDependencies = new HashSet<>();
//        upstreamDependencies = new HashSet<>();
//        referringDocuments = new HashSet<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public Set<Facility> getDownstreamDependencies() {
        return downstreamDependencies;
    }

    public void setDownstreamDependencies(Set<Facility> downstreamDependencies) {
        this.downstreamDependencies = downstreamDependencies;
    }

    public Set<Facility> getUpstreamDependencies() {
        return upstreamDependencies;
    }

    public void setUpstreamDependencies(Set<Facility> upstreamDependencies) {
        this.upstreamDependencies = upstreamDependencies;
    }

    public Set<Document> getReferringDocuments() {
        return referringDocuments;
    }

    public void setReferringDocuments(Set<Document> referringDocuments) {
        this.referringDocuments = referringDocuments;
    }

    @Override
    public double[] getPoint() {
        return new double[] { getLatitude(), getLongitude() };
    }

}
