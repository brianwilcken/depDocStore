package webapp.controllers;

import com.bericotech.clavin.gazetteer.FeatureClass;
import com.bericotech.clavin.gazetteer.GeoName;
import com.bericotech.clavin.resolver.ResolvedLocation;
import com.mongodb.client.gridfs.model.GridFSFile;
import common.Tools;
import geoparsing.LocationResolver;
import mongoapi.DocStoreMongoClient;
import nlp.DocumentCategorizer;
import nlp.NamedEntityRecognizer;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Min;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import solrapi.SolrClient;
import solrapi.model.IndexedDocumentsQueryParams;
import webapp.models.GeoNameWithFrequencyScore;
import webapp.models.JsonResponse;
import webapp.services.TemporaryRepoCleanupService;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/api/documents")
public class DocumentsController {
    private SolrClient solrClient;
    private DocStoreMongoClient mongoClient;
    private DocumentCategorizer categorizer;
    private NamedEntityRecognizer recognizer;
    private final LocationResolver locationResolver;

    private static String temporaryFileRepo = Tools.getProperty("mongodb.temporaryFileRepo");

    final static Logger logger = LogManager.getLogger(DocumentsController.class);

    @Autowired
    private TemporaryRepoCleanupService cleanupService;

    @Autowired
    private HttpServletRequest context;

    public DocumentsController() {
        solrClient = new SolrClient(Tools.getProperty("solr.url"));
        mongoClient = new DocStoreMongoClient(Tools.getProperty("mongodb.url"));
        categorizer = new DocumentCategorizer();
        recognizer = new NamedEntityRecognizer(solrClient);
        locationResolver = new LocationResolver();
    }

    @RequestMapping(method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> getDocuments(IndexedDocumentsQueryParams params) {
        logger.info(context.getRemoteAddr() + " -> " + "In getDocuments method");
        try {
            SolrQuery.SortClause sort = new SolrQuery.SortClause("lastUpdated", "desc");
            SolrDocumentList docs = solrClient.QuerySolrDocuments(params.getQuery(), params.getQueryRows(), params.getQueryStart(), sort, params.getFilterQueries());
            JsonResponse response = Tools.formJsonResponse(docs, params.getQueryTimeStamp());
            logger.info(context.getRemoteAddr() + " -> " + "Returning documents");
            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null, params.getQueryTimeStamp()));
        }
    }

    @RequestMapping(method=RequestMethod.POST, consumes=MediaType.MULTIPART_FORM_DATA_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> createDocument(@RequestPart("metadata") Map<String, Object> metadata, @RequestPart("file") MultipartFile document) {
        try {
            logger.info(context.getRemoteAddr() + " -> " + "Storing new document");
            String filename = document.getOriginalFilename();
            File uploadedFile = Tools.WriteFileToDisk(temporaryFileRepo + filename, document.getInputStream());

            ObjectId fileId = mongoClient.StoreFile(uploadedFile);

            SolrDocument solrDocument = new SolrDocument();
            metadata.entrySet().stream().forEach(p -> solrDocument.addField(p.getKey(), p.getValue()));
            solrDocument.addField("docStoreId", fileId.toString());

            String timestamp = Tools.getFormattedDateTimeString(Instant.now());
            solrDocument.addField("created", timestamp);
            solrDocument.addField("lastUpdated", timestamp);

            String contentType = Files.probeContentType(uploadedFile.toPath());
            if (contentType.compareTo("application/pdf") == 0) {
                String docText = Tools.extractPDFText(uploadedFile);
                solrDocument.addField("docText", docText);
                String category = Tools.removeUTF8BOM(categorizer.detectCategory(docText));
                solrDocument.addField("category", category);
                String annotated = recognizer.autoAnnotate(docText, category, 0.5);
                solrDocument.addField("annotated", annotated);
                //final Map<String, Double> entities = recognizer.detectNamedEntities(docText, category, 0.5);

                //Geoparse the document to extract a list of geolocations
                List<ResolvedLocation> locations = locationResolver.resolveLocations(docText);

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
                double clusterRadius = getClusterRadius(validForAdminDiv);
                int minClusterSize = getMinClusterSize(validForAdminDiv);
                List<GeoNameWithFrequencyScore> validOverall = getValidGeoNamesByClustering(validForAdminDiv, clusterRadius, minClusterSize);

                //Sort the valid set by geoname class ordinal to find the minimum administrative level.  This is assumed to
                //be the most precise location.
                Collections.sort(validOverall, new Comparator<GeoNameWithFrequencyScore>() {

                    @Override
                    public int compare(GeoNameWithFrequencyScore t1, GeoNameWithFrequencyScore t2) {
                        return Integer.compare(t2.getGeoName().getFeatureClass().ordinal(), t1.getGeoName().getFeatureClass().ordinal());
                    }
                });


                for (GeoNameWithFrequencyScore geoName : validOverall) {

                }
            }

            //solrClient.indexDocument(solrDocument);
            cleanupService.process();
            return ResponseEntity.ok().body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    private List<GeoNameWithFrequencyScore> getValidGeoNamesByClustering(List<GeoNameWithFrequencyScore> validForAdminDiv, double clusterRadius, int minClusterSize) {
        List<GeoNameWithFrequencyScore> validOverall = new ArrayList<>();
        if (validForAdminDiv.size() > 3) {
            //A cluster is defined as a group of at least minClusterSize locations all lying within at most clusterRadius degree(s) of measure
            //from each other.
            DBSCANClusterer clusterer = new DBSCANClusterer(clusterRadius, minClusterSize);
            List<Cluster<GeoNameWithFrequencyScore>> geoNameClusters = clusterer.cluster(validForAdminDiv);

            if (geoNameClusters.size() > 0) {
                //Now within each cluster filter again by statistics to get the final set of overall valid locations.
                for (Cluster<GeoNameWithFrequencyScore> cluster : geoNameClusters) {
                    validOverall.addAll(filterByStatistics(cluster.getPoints()));
                }
            } else {
                //The points must be too spread out to form clusters with at least 3 points.
                //Slightly increase the search radius and try again...
                clusterRadius += 0.25;
                minClusterSize = minClusterSize / 2 < 2 ? 2 : minClusterSize / 2;
                return getValidGeoNamesByClustering(validForAdminDiv, clusterRadius, minClusterSize);
            }
        } else {
            validOverall.addAll(filterByStatistics(validForAdminDiv));
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
        return validForAdminDiv.size() / 3;
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

    @RequestMapping(value="/file/{id}", method=RequestMethod.PUT, consumes=MediaType.MULTIPART_FORM_DATA_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> updateDocument(@PathVariable(name="id") String id, @RequestPart("file") MultipartFile document) {
        try {
            SolrDocumentList docs = solrClient.QuerySolrDocuments("id:" + id, 1000, 0, null);
            if (!docs.isEmpty()) {
                SolrDocument doc = docs.get(0);

                if (!document.isEmpty() && doc.containsKey("docStoreId")) { //uploaded file is being replaced
                    String oldFileId = doc.get("docStoreId").toString();
                    mongoClient.DeleteFile(oldFileId);
                    String filename = document.getOriginalFilename();
                    File uploadedFile = Tools.WriteFileToDisk(temporaryFileRepo + filename, document.getInputStream());
                    ObjectId fileId = mongoClient.StoreFile(uploadedFile);
                    doc.replace("docStoreId", fileId.toString());

                    String contentType = Files.probeContentType(uploadedFile.toPath());
                    if (contentType.compareTo("application/pdf") == 0) {
                        String docText = Tools.extractPDFText(uploadedFile);
                        if (doc.containsKey("docText")) {
                            doc.replace("docText", docText);
                        } else {
                            doc.addField("docText", docText);
                        }
                        String category = Tools.removeUTF8BOM(categorizer.detectCategory(docText));
                        if (doc.containsKey("category")) {
                            doc.replace("category", category);
                        } else {
                            doc.addField("category", category);
                        }
                        String annotated = recognizer.autoAnnotate(docText, category, 0.5);
                        if (doc.containsKey("annotated")) {
                            doc.replace("annotated", annotated);
                        } else {
                            doc.addField("annotated", annotated);
                        }

                        //final Map<String, Double> entities = recognizer.detectNamedEntities(docText, category, 0.5);
                    }
                }

                String timestamp = Tools.getFormattedDateTimeString(Instant.now());
                doc.replace("lastUpdated", timestamp);

                solrClient.indexDocument(doc);
                cleanupService.process();
                return ResponseEntity.ok().body(Tools.formJsonResponse(null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    @RequestMapping(value="/metadata/{id}", method=RequestMethod.PUT, consumes=MediaType.MULTIPART_FORM_DATA_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> updateDocument(@PathVariable(name="id") String id, @RequestPart("metadata") Map<String, Object> metadata) {
        try {
            SolrDocumentList docs = solrClient.QuerySolrDocuments("id:" + id, 1000, 0, null);
            if (!docs.isEmpty()) {
                SolrDocument doc = docs.get(0);

                metadata.entrySet().stream().forEach(p -> {
                    if (doc.containsKey(p.getKey())) {
                        doc.replace(p.getKey(), p.getValue());
                    } else {
                        doc.addField(p.getKey(), p.getValue());
                    }
                });

                String timestamp = Tools.getFormattedDateTimeString(Instant.now());
                doc.replace("lastUpdated", timestamp);

                solrClient.indexDocument(doc);
                return ResponseEntity.ok().body(Tools.formJsonResponse(null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    @RequestMapping(value="/{id}", method=RequestMethod.DELETE, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> deleteDocument(@PathVariable(name="id") String id) {
        try {
            SolrDocumentList docs = solrClient.QuerySolrDocuments("id:" + id, 1000, 0, null);
            if (!docs.isEmpty()) {
                SolrDocument doc = docs.get(0);
                if (doc.containsKey("docStoreId")) {
                    String fileId = doc.get("docStoreId").toString();
                    mongoClient.DeleteFile(fileId);
                }
                solrClient.deleteDocuments("id:" + id);
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body(Tools.formJsonResponse(null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    @RequestMapping(value="/{fileId}", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable(name="fileId") String fileId) {
        logger.info(context.getRemoteAddr() + " -> " + "In downloadDocument method");

        GridFSFile fsFile = mongoClient.GetFileMetadata(fileId);
        logger.info("downloading file: " + fsFile.getFilename());
        logger.info("file size: " + fsFile.getLength());
        FileInputStream stream = mongoClient.DownloadFileToStream(fsFile);

        if (stream != null) {
            HttpHeaders respHeaders = new HttpHeaders();
            if (fsFile.getMetadata().containsKey("type")) {
                MediaType type = MediaType.parseMediaType((String)fsFile.getMetadata().get("type"));
                respHeaders.setContentType(type);
            } else {
                respHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            }
            respHeaders.setContentLength(fsFile.getLength());
            respHeaders.setContentDispositionFormData("attachment", fsFile.getFilename());

            InputStreamResource isr = new InputStreamResource(stream);
            cleanupService.process();
            return new ResponseEntity<>(isr, respHeaders, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(null, null, HttpStatus.NOT_FOUND);
        }
    }
}
