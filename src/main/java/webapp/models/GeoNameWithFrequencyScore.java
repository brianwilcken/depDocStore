package webapp.models;

import com.bericotech.clavin.gazetteer.GeoName;
import org.apache.commons.math3.ml.clustering.Clusterable;

public class GeoNameWithFrequencyScore implements Clusterable {
    private GeoName geoName;
    private int freqScore;
    private int adminDiv;

    public GeoNameWithFrequencyScore(GeoName geoName, int freqScore, int adminDiv) {
        this.geoName = geoName;
        this.freqScore = freqScore;
        this.adminDiv = adminDiv;
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
