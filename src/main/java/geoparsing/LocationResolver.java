package geoparsing;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import com.bericotech.clavin.ClavinException;
import com.bericotech.clavin.GeoParser;
import com.bericotech.clavin.GeoParserFactory;
import com.bericotech.clavin.gazetteer.FeatureClass;
import com.bericotech.clavin.gazetteer.GeoName;
import com.bericotech.clavin.nerd.StanfordExtractor;
import com.bericotech.clavin.resolver.ResolvedLocation;
import common.Tools;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.common.SolrDocument;
import webapp.models.GeoNameWithFrequencyScore;

public class LocationResolver {
    private final static Logger logger = LogManager.getLogger(LocationResolver.class);
    private GeoParser parser;

    public LocationResolver() {
        try {
            parser = GeoParserFactory.getDefault(Tools.getProperty("geonamesIndex.location"),
                    new StanfordExtractor("english.all.3class.caseless.distsim.crf.ser.gz", "english.all.3class.caseless.distsim.prop"),
                    1, 1, false);
        } catch (ClavinException e) {
            parser = null;
        } catch (IOException e) {
            parser = null;
        } catch (ClassNotFoundException e) {
            parser = null;
        }
    }

    public List<SolrDocument> getLocationsFromDocument(String docText, String docId) {
        List<SolrDocument> locDocs = new ArrayList<>();
        try {
            //Geoparse the document to extract a list of geolocations
            List<ResolvedLocation> resolvedLocations = parser.parse(docText);

            List<GeoNameWithFrequencyScore> geoNames = getValidGeoNames(resolvedLocations);

            for (GeoNameWithFrequencyScore geoName : geoNames) {
                SolrDocument locDoc = new SolrDocument();
                locDoc.addField("docId", docId);
                locDoc.addField("name", composeLocationName(geoName.getGeoName()));
                locDoc.addField("latitude", geoName.getGeoName().getLatitude());
                locDoc.addField("longitude", geoName.getGeoName().getLongitude());

                locDocs.add(locDoc);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return locDocs;
    }

    private String composeLocationName(GeoName geoName) {
        GeoName parent = geoName.getParent();
        if (parent == null) {
            return geoName.getName();
        } else {
            return geoName.getName() + ", " + composeLocationName(parent);
        }
    }

    private List<GeoNameWithFrequencyScore> getValidGeoNames(List<ResolvedLocation> locations) {
        //Remove all geonames that do not belong to the USA
        List<ResolvedLocation> usaLocations = locations.stream()
                .filter(p -> p.getGeoname().getPrimaryCountryCode().name().compareTo("US") == 0)
                .collect(Collectors.toList());

        //Group the set of geonames according to geoname ID.  This will effectively produce
        //the set of unique geonames.
        Map<Integer, List<GeoName>> groupedGeoNames = usaLocations.stream()
                .map(p -> p.getGeoname())
                .collect(Collectors.groupingBy(GeoName::getGeonameID));

        //Group the sets of unique geonames according to the administrative division.
        //First prepare a collection of objects having the geoname object, frequency and administrative type.
        List<GeoNameWithFrequencyScore> adminDivisions = groupedGeoNames.values().stream()
                .map(p -> new GeoNameWithFrequencyScore(p.get(0), p.size(), p.get(0).getFeatureClass().ordinal()))
                .collect(Collectors.toList());

        //now group all objects together according to administrative division for running statistics against
        //each division
        Map<Integer, List<GeoNameWithFrequencyScore>> groupedByAdminDiv = adminDivisions.stream()
                .collect(Collectors.groupingBy(GeoNameWithFrequencyScore::getAdminDiv));

        //run statistics against each division to produce sets of valid geonames according to statistics
        List<GeoNameWithFrequencyScore> validForAdminDiv = new ArrayList<>();
        if (groupedByAdminDiv.size() > 1) {
            for (Integer adminDiv : groupedByAdminDiv.keySet()) {
                List<GeoNameWithFrequencyScore> geoNames = groupedByAdminDiv.get(adminDiv);
                List<GeoNameWithFrequencyScore> validGeoNames = filterByStatistics(geoNames);
                groupedByAdminDiv.replace(adminDiv, validGeoNames);
                validForAdminDiv.addAll(validGeoNames);
            }
        } else {
            for (Integer adminDiv : groupedByAdminDiv.keySet()) {
                List<GeoNameWithFrequencyScore> geoNames = groupedByAdminDiv.get(adminDiv);
                validForAdminDiv.addAll(geoNames);
            }
        }

        //Having filtered down to a set of statistically significant geoNames, run a clustering
        //method to ensure against outliers.

        //There should be two levels of clustering happen: top-level clustering of division 0 and 1 locations, and lower-level for everything else
        List<GeoNameWithFrequencyScore> topLevel = validForAdminDiv.stream().filter(p -> p.getAdminDiv() <= FeatureClass.P.ordinal()).collect(Collectors.toList());
        double clusterRadius = getClusterRadius(topLevel);
        int minClusterSize = getMinClusterSize(topLevel);
        List<GeoNameWithFrequencyScore> validTopLevel = getValidGeoNamesByClustering(topLevel, clusterRadius, minClusterSize, 1, 2, 0);
        List<GeoNameWithFrequencyScore> lowerLevel = validForAdminDiv.stream().filter(p -> p.getAdminDiv() > FeatureClass.P.ordinal()).collect(Collectors.toList());
        clusterRadius = getClusterRadius(lowerLevel);
        minClusterSize = getMinClusterSize(lowerLevel);
        List<GeoNameWithFrequencyScore> validTopLevelWithLowerLevel = new ArrayList<>();
        validTopLevelWithLowerLevel.addAll(validTopLevel);
        validTopLevelWithLowerLevel.addAll(lowerLevel);
        List<GeoNameWithFrequencyScore> validLowerLevel = getValidGeoNamesByClustering(validTopLevelWithLowerLevel, clusterRadius, minClusterSize, 0.25, 5, 0);
        validLowerLevel = validLowerLevel.stream().filter(p -> p.getAdminDiv() > FeatureClass.P.ordinal()).collect(Collectors.toList());

        List<GeoNameWithFrequencyScore> validOverall = new ArrayList<>();
        validOverall.addAll(validTopLevel);
        validOverall.addAll(validLowerLevel);
        //validOverall = filterByStatistics(validOverall);

        //Sort the valid set by geoname class ordinal to find the minimum administrative level.  This is assumed to
        //be the most precise location.
        Collections.sort(validOverall, new Comparator<GeoNameWithFrequencyScore>() {

            @Override
            public int compare(GeoNameWithFrequencyScore t1, GeoNameWithFrequencyScore t2) {
                return Integer.compare(t2.getGeoName().getFeatureClass().ordinal(), t1.getGeoName().getFeatureClass().ordinal());
            }
        });

        return validOverall;
    }

    private List<GeoNameWithFrequencyScore> getValidGeoNamesByClustering(List<GeoNameWithFrequencyScore> geoNames, double clusterRadius, int minClusterSize, double radiusIncrement, int maxIncrements, int incrementNum) {
        List<GeoNameWithFrequencyScore> validOverall = new ArrayList<>();
        if (geoNames.size() > 1) {
            //A cluster is defined as a group of at least minClusterSize locations all lying within at most clusterRadius degree(s) of measure
            //from each other.
            DBSCANClusterer clusterer = new DBSCANClusterer(clusterRadius, minClusterSize);
            List<Cluster<GeoNameWithFrequencyScore>> geoNameClusters = clusterer.cluster(geoNames);

            if (geoNameClusters.size() > 0) {
                //Now within each cluster filter again by statistics to get the final set of overall valid locations.
                for (Cluster<GeoNameWithFrequencyScore> cluster : geoNameClusters) {
                    validOverall.addAll(cluster.getPoints());
                }
            } else {
                //The points must be too spread out to form clusters with at least minClusterSize points.
                //Slightly increase the search radius and try again...
                clusterRadius += radiusIncrement;
                minClusterSize = minClusterSize / 2 < 1 ? 1 : minClusterSize / 2;
                if (incrementNum <= maxIncrements) {
                    return getValidGeoNamesByClustering(geoNames, clusterRadius, minClusterSize, radiusIncrement, maxIncrements, ++incrementNum);
                }
            }
        } else {
            validOverall.addAll(geoNames);
        }
        return validOverall;
    }

    private double getClusterRadius(List<GeoNameWithFrequencyScore> validForAdminDiv) {
        GeoNameWithFrequencyScore maxFreqGeoName = validForAdminDiv.stream().max(new Comparator<GeoNameWithFrequencyScore>() {
            @Override
            public int compare(GeoNameWithFrequencyScore t1, GeoNameWithFrequencyScore t2) {
                return Integer.compare(t1.getFreqScore(), t2.getFreqScore());
            }
        }).get();

        //The assumption is that the search radius needs to expand if the most frequently mentioned location
        //is an administrative region like a state or a county.
        if (maxFreqGeoName.getAdminDiv() == FeatureClass.A.ordinal()) {
            return 3; //corresponds to a radius of about 210 miles
        } else if (maxFreqGeoName.getAdminDiv() == FeatureClass.P.ordinal()) {
            return 1; //radius of about 70 miles
        } else {
            return 0.25; //radius of about 17 miles
        }
    }

    private int getMinClusterSize(List<GeoNameWithFrequencyScore> validForAdminDiv) {
        return validForAdminDiv.size() / 2 < 1 ? 1 : validForAdminDiv.size() / 2;
    }

    private List<GeoNameWithFrequencyScore> filterByStatistics(List<GeoNameWithFrequencyScore> geoNames) {
        if (geoNames.size() > 2) {
            //prepare an array of frequencies for the set of geoname objects for use in calculating statistics
            List<Integer> freqs = geoNames.stream().map(p -> p.getFreqScore()).collect(Collectors.toList());
            int[] freqsArray = ArrayUtils.toPrimitive(freqs.toArray(new Integer[freqs.size()]));
            double[] frequencies = Arrays.stream(freqsArray).asDoubleStream().toArray();
            Arrays.sort(frequencies);

            //calculate index of dispersion for use in filtering the set of geonames to just those that fall within
            //a valid range of frequency.
            StandardDeviation standardDev = new StandardDeviation();
            Mean mean = new Mean();
            double mn = mean.evaluate(frequencies, 0, frequencies.length);
            double stddev = standardDev.evaluate(frequencies);
            double idxOfDisp = stddev*stddev/mn;
            int minimumFrequency = (int) Math.ceil(idxOfDisp);

            //filter the set of geonames using the dispersion index to obtain the valid set according to statistics
            List<GeoNameWithFrequencyScore> validGeonames = geoNames.stream()
                    .filter(p -> p.getFreqScore() >= minimumFrequency)
                    .collect(Collectors.toList());

            return validGeonames;
        }

        return geoNames;
    }
}
