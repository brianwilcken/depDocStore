package webscraper.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eventsregistryapi.model.IndexedObject;
import org.apache.commons.codec.binary.Hex;
import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.common.SolrDocument;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class IndexedEventSourceLocation extends IndexedObject {
    @Field
    private String id;
    @Field
    private String sourceId;
    @Field
    private String latitude;
    @Field
    private String longitude;
    @Field
    private String location;

    private static ObjectMapper mapper = new ObjectMapper();

    public IndexedEventSourceLocation() {
    }

    public IndexedEventSourceLocation(SolrDocument doc) {
        ConsumeSolr(doc);
    }

    public void initId() {
        try {
            id = Hex.encodeHexString(MessageDigest.getInstance("SHA-1").digest(mapper.writeValueAsBytes(
                    this.getSourceId() +
                            this.getLatitude() +
                            this.getLongitude() +
                            this.getLocation())));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
