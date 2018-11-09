package webapp.models;

import com.bericotech.clavin.gazetteer.GeoName;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.solr.common.SolrDocument;

public class GeoNameWithFrequencyScore implements Clusterable {
    private GeoName geoName;
    private int freqScore;
    private int adminDiv;

    public GeoNameWithFrequencyScore(GeoName geoName, int freqScore, int adminDiv) {
        this.geoName = geoName;
        this.freqScore = freqScore;
        this.adminDiv = adminDiv;
    }

    public SolrDocument mutate(String docId) {
        SolrDocument locDoc = new SolrDocument();
        locDoc.addField("docId", docId);
        locDoc.addField("name", composeLocationName(geoName));
        locDoc.addField("latitude", geoName.getLatitude());
        locDoc.addField("longitude", geoName.getLongitude());

        return locDoc;
    }

    public String composeLocationName(GeoName geoName) {
        GeoName parent = geoName.getParent();
        if (parent == null) {
            return geoName.getName();
        } else {
            return geoName.getName() + ", " + composeLocationName(parent);
        }
    }

    public GeoName getGeoName() {
        return geoName;
    }

    public void setGeoName(GeoName geoName) {
        this.geoName = geoName;
    }

    public int getFreqScore() {
        return freqScore;
    }

    public void setFreqScore(int freqScore) {
        this.freqScore = freqScore;
    }

    public int getAdminDiv() {
        return adminDiv;
    }

    public void setAdminDiv(int adminDiv) {
        this.adminDiv = adminDiv;
    }

    @Override
    public double[] getPoint() {
        return new double[] { getGeoName().getLatitude(), getGeoName().getLongitude() };
    }
}
