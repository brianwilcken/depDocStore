package neo4japi.domain;

import com.bericotech.clavin.gazetteer.GeoName;
import geoparsing.LocationResolver;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import webapp.models.GeoNameWithFrequencyScore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@NodeEntity
public class Facility extends Entity implements Clusterable {

    private String UUID;
    private String name;
    private String city;
    private String county;
    private String state;

    private double latitude;
    private double longitude;

    @Relationship(type = "Dependent_On", direction = Relationship.INCOMING)
    private Set<Facility> downstreamDependencies; //facilities that depend on this facility

    @Relationship(type = "Dependent_On")
    private Set<Facility> upstreamDependencies; //facilities that this facility depends on

    @Relationship(type = "Refers_To", direction = Relationship.INCOMING)
    private Set<Document> referringDocuments;

    public Facility(String name, GeoNameWithFrequencyScore optimalGeoName, List<GeoNameWithFrequencyScore> geoNames) {
        this.UUID = java.util.UUID.randomUUID().toString();
        this.name = name;

        GeoNameWithFrequencyScore geoName = optimalGeoName;

        List<GeoName> exactGeoNames = LocationResolver.getGeoNames(name);
        GeoName exactGeoName = null;
        if (exactGeoNames.size() > 0) {
            //cluster to see if this specifically found location even makes sense based on the document
            exactGeoName = exactGeoNames.get(0);
            GeoNameWithFrequencyScore exactGeoNameWithFreq = new GeoNameWithFrequencyScore(exactGeoName, 0, 0);
            List<Clusterable> clusterable = new ArrayList<>();
            clusterable.addAll(geoNames);
            clusterable.add(exactGeoNameWithFreq);

            List<Clusterable> clustered = LocationResolver.getValidGeoCoordinatesByClustering(clusterable, 20, 2, 1, 4, 0);
            if (clustered.contains(exactGeoNameWithFreq)) {
                geoName = exactGeoNameWithFreq;
            } else {
                exactGeoName = null;
            }
        }

        GeoName cityGeoName = geoName.getCity(geoName.getGeoName());
        GeoName countyGeoName = geoName.getCounty(geoName.getGeoName());
        GeoName stateGeoName = geoName.getState(geoName.getGeoName());

        if (stateGeoName != null) {
            state = stateGeoName.getName();
            latitude = stateGeoName.getLatitude();
            longitude = stateGeoName.getLongitude();
        }

        if (countyGeoName != null) {
            county = countyGeoName.getName();
            latitude = countyGeoName.getLatitude();
            longitude = countyGeoName.getLongitude();
        }

        if (cityGeoName != null) {
            city = cityGeoName.getName();
            latitude = cityGeoName.getLatitude();
            longitude = cityGeoName.getLongitude();
        }

        if (exactGeoName != null) {
            latitude = exactGeoName.getLatitude();
            longitude = exactGeoName.getLongitude();
        }

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

    public String getUUID() {
        return UUID;
    }

    public void setUUID(String UUID) {
        this.UUID = UUID;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCounty() {
        return county;
    }

    public void setCounty(String county) {
        this.county = county;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
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
