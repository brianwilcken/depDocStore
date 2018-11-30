package webapp.controllers;

import com.google.common.base.Strings;
import com.mongodb.client.gridfs.model.GridFSFile;
import common.TextExtractor;
import common.Tools;
import geoparsing.LocationResolver;
import mongoapi.DocStoreMongoClient;
import neo4japi.Neo4jClient;
import neo4japi.domain.Document;
import nlp.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
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
import webapp.services.*;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/api/documents")
public class DocumentsController {
    private SolrClient solrClient;
    private DocStoreMongoClient mongoClient;
    private Neo4jClient neo4jClient;
    private DocumentCategorizer categorizer;
    private NamedEntityRecognizer recognizer;
    private final LocationResolver locationResolver;

    private static String temporaryFileRepo = Tools.getProperty("mongodb.temporaryFileRepo");

    final static Logger logger = LogManager.getLogger(DocumentsController.class);

    @Autowired
    private TemporaryRepoCleanupService cleanupService;

    @Autowired
    private NERModelTrainingService nerModelTrainingService;

    @Autowired
    private DoccatModelTrainingService doccatModelTrainingService;

    @Autowired
    private ResourceURLLookupService resourceURLLookupService;

    public DocumentsController() {
        solrClient = new SolrClient(Tools.getProperty("solr.url"));
        mongoClient = new DocStoreMongoClient(Tools.getProperty("mongodb.url"));
        neo4jClient = new Neo4jClient();
        categorizer = new DocumentCategorizer(solrClient);
        recognizer = new NamedEntityRecognizer(solrClient);
        locationResolver = new LocationResolver();
    }

    @RequestMapping(method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> getDocuments(IndexedDocumentsQueryParams params) {
        logger.info("In getDocuments method");
        try {
            SolrQuery.SortClause sort = new SolrQuery.SortClause("lastUpdated", "desc");
            SolrDocumentList docs = solrClient.QuerySolrDocuments(params.getQuery(), params.getQueryRows(), params.getQueryStart(), sort, params.getFilterQueries());
            JsonResponse response = Tools.formJsonResponse(docs, params.getQueryTimeStamp());
            logger.info("Returning documents");
            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            logger.error(e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null, params.getQueryTimeStamp()));
        }
    }

    @RequestMapping(value="/entities/{id}", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> getDocumentNamedEntities(@PathVariable(name="id") String id) {
        logger.info("In getDocumentNamedEntities method");
        try {
            SolrDocumentList docs = solrClient.QuerySolrDocuments("docId:" + id + " AND entity:*", 1000000, 0, null);
            JsonResponse response = Tools.formJsonResponse(docs);
            logger.info("Returning entities");
            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            logger.error(e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    @RequestMapping(value="/annotate/{id}", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> getAutoAnnotatedDocument(@PathVariable(name="id") String id, int threshold) {
        logger.info("In autoAnnotate method");
        try {
            SolrDocumentList docs = solrClient.QuerySolrDocuments("id:" + id, 1000, 0, null);
            if (!docs.isEmpty()) {
                SolrDocument doc = docs.get(0);

                double dblThreshold = (double)threshold / (double)100;
                List<NamedEntity> entities = recognizer.detectNamedEntities(doc.get("parsed").toString(), doc.get("category").toString(), dblThreshold);
                String annotated = recognizer.autoAnnotate(doc.get("parsed").toString(), entities);

                return ResponseEntity.ok().body(Tools.formJsonResponse(annotated));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    @RequestMapping(value="/trainNER", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> trainNERModel(String category) {
        logger.info("In trainNERModel method");
        try {
            nerModelTrainingService.process(this, category);
            return ResponseEntity.ok().body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    @RequestMapping(value="/trainDoccat", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> trainDoccatModel() {
        logger.info("In trainDoccatModel method");
        try {
            doccatModelTrainingService.process(this);
            return ResponseEntity.ok().body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    @RequestMapping(value="/url", method=RequestMethod.POST, consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> queueResourceURL(@RequestParam Map<String, Object> metadata, @RequestParam("url") String urlString) {
        try {
            URL url = new URL(urlString);
            logger.info("Queueing resource URL for retrieval");
            resourceURLLookupService.process(url, metadata);
            return ResponseEntity.ok().body(Tools.formJsonResponse(null));
        } catch (MalformedURLException e){
            return ResponseEntity.badRequest().body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    @RequestMapping(method=RequestMethod.POST, consumes=MediaType.MULTIPART_FORM_DATA_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> createDocument(@RequestPart("metadata") Map<String, Object> metadata, @RequestPart("file") MultipartFile document) {
        try {
            logger.info("Storing new document");
            String filename = document.getOriginalFilename();
            File uploadedFile = Tools.WriteFileToDisk(temporaryFileRepo + filename, document.getInputStream());

            logger.info("Document stored");

            return processNewDocument(filename, metadata, uploadedFile);
        } catch (Exception e) {
            logger.error(e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    public ResponseEntity<JsonResponse> processNewDocument(String filename, Map<String, Object> metadata, File uploadedFile) {
        try {
            SolrDocumentList docs = new SolrDocumentList();

            SolrDocument doc = new SolrDocument();
            String docId = UUID.randomUUID().toString();
            doc.addField("id", docId);
            doc.addField("filename", filename);
            metadata.entrySet().stream().forEach(p -> doc.addField(p.getKey(), p.getValue()));

            logger.info("Metadata added to document");
            String timestamp = Tools.getFormattedDateTimeString(Instant.now());
            doc.addField("created", timestamp);
            doc.addField("lastUpdated", timestamp);
            logger.info("timestamp added");

            String docText = TextExtractor.extractText(uploadedFile);
            doc.addField("docText", docText);

            if (Strings.isNullOrEmpty(docText)) {
                return ResponseEntity.unprocessableEntity().body(Tools.formJsonResponse(null));
            }

            docs = runNLPPipeline(docText, docId, doc);

            logger.info("storing file to MongoDB");
            ObjectId fileId = mongoClient.StoreFile(uploadedFile);
            doc.addField("docStoreId", fileId.toString());

            logger.info("storing data to Solr");
            docs.add(doc);
            solrClient.indexDocuments(docs);
            cleanupService.process();
            return ResponseEntity.ok().body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    @RequestMapping(value="/file/{id}", method=RequestMethod.PUT, consumes=MediaType.MULTIPART_FORM_DATA_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> updateDocument(@PathVariable(name="id") String id, @RequestPart("file") MultipartFile document) {
        try {
            SolrDocumentList docs = solrClient.QuerySolrDocuments("id:" + id, 1000, 0, null);
            solrClient.deleteDocuments("docId:" + id);
            if (!docs.isEmpty()) {
                SolrDocument doc = docs.get(0);

                if (!document.isEmpty() && doc.containsKey("docStoreId")) { //uploaded file is being replaced
                    String oldFileId = doc.get("docStoreId").toString();
                    mongoClient.DeleteFile(oldFileId);
                    String filename = document.getOriginalFilename();
                    File uploadedFile = Tools.WriteFileToDisk(temporaryFileRepo + filename, document.getInputStream());
                    ObjectId fileId = mongoClient.StoreFile(uploadedFile);
                    doc.replace("docStoreId", fileId.toString());
                    doc.replace("filename", filename);

                    String docText = TextExtractor.extractText(uploadedFile);
                    if (doc.containsKey("docText")) {
                        doc.replace("docText", docText);
                    } else {
                        doc.addField("docText", docText);
                    }

                    SolrDocumentList solrDocs = runNLPPipeline(docText, id, doc);
                    solrClient.indexDocuments(solrDocs);
                }

                String timestamp = Tools.getFormattedDateTimeString(Instant.now());
                doc.replace("lastUpdated", timestamp);

                logger.info("storing data to Solr");
                solrClient.indexDocument(doc);
                cleanupService.process();
                return ResponseEntity.ok().body(Tools.formJsonResponse(null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    private SolrDocumentList runNLPPipeline(String docText, String id, SolrDocument doc) throws SolrServerException, IOException {
        SolrDocumentList docs = new SolrDocumentList();

        String parsed = recognizer.deepCleanText(docText);
        if (doc.containsKey("parsed")) {
            doc.replace("parsed", parsed);
        } else {
            doc.addField("parsed", parsed);
        }
        String category = Tools.removeUTF8BOM(categorizer.detectCategory(parsed, 0));
        logger.info("category detected: " + category);
        if (doc.containsKey("category")) {
            doc.replace("category", category);
        } else {
            doc.addField("category", category);
        }
        List<NamedEntity> entities = recognizer.detectNamedEntities(parsed, category, 0.5);
        String annotated = recognizer.autoAnnotate(parsed, entities);
        if (doc.containsKey("annotated")) {
            doc.replace("annotated", annotated);
        } else {
            doc.addField("annotated", annotated);
        }

        List<SolrDocument> entityDocs = entities.stream()
                .map(p -> p.mutate(id))
                .collect(Collectors.toList());
        docs.addAll(entityDocs);

        resolveDocumentRelations(id, doc, docs, parsed, entities);

        return docs;
    }

    private void resolveDocumentRelations(String id, SolrDocument doc, SolrDocumentList docs, String parsed, List<NamedEntity> entities) {
        logger.info("resolving locations");
        List<GeoNameWithFrequencyScore> geoNames = locationResolver.getLocationsFromDocument(parsed, id);
        List<SolrDocument> locDocs = geoNames.stream()
                .map(p -> p.mutate(id))
                .collect(Collectors.toList());
        docs.addAll(locDocs);

        if (entities.size() > 0 && geoNames.size() > 0) {
            List<Coreference> coreferences = processCoreferences(docs, parsed, id, entities);

            logger.info("resolving entity relations");
            InformationExtractor informationExtractor = new InformationExtractor();
            List<EntityRelation> entityRelations = informationExtractor.getEntityRelations(parsed, id, entities, coreferences);
            List<SolrDocument> relDocs = entityRelations.stream()
                    .map(p -> p.mutateForSolr(id))
                    .collect(Collectors.toList());
            docs.addAll(relDocs);

            if (geoNames.size() > 0) {
                Document n4jdoc = neo4jClient.addDocument(doc);
                neo4jClient.addDataModelDocumentRelation(doc, n4jdoc);
                neo4jClient.addDependencies(n4jdoc, geoNames, entityRelations);
            }
        }
    }

    private List<Coreference> processCoreferences(SolrDocumentList docs, String parsed, String id, List<NamedEntity> entities) {
        logger.info("resolving coreferences");
        CoreferenceResolver coreferenceResolver = new CoreferenceResolver();
        List<Coreference> coreferences = coreferenceResolver.getCoreferencesFromDocument(parsed, id, entities);
        List<SolrDocument> corefDocs = coreferences.stream()
                .map(p -> p.mutate())
                .collect(Collectors.toList());
        docs.addAll(corefDocs);

        return coreferences;
    }

    @RequestMapping(value="/metadata/{id}", method=RequestMethod.PUT, consumes=MediaType.MULTIPART_FORM_DATA_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> updateDocument(@PathVariable(name="id") String id, @RequestPart("metadata") Map<String, Object> metadata) {
        try {
            SolrDocumentList docs = solrClient.QuerySolrDocuments("id:" + id, 1000, 0, null);
            if (!docs.isEmpty()) {
                SolrDocument doc = docs.get(0);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US);
                ZonedDateTime created = ZonedDateTime.parse(doc.get("created").toString(), formatter);

                metadata.entrySet().stream().forEach(p -> {
                    if (doc.containsKey(p.getKey())) {
                        doc.replace(p.getKey(), p.getValue());
                    } else {
                        doc.addField(p.getKey(), p.getValue());
                    }
                });

                doc.replace("created", Tools.getFormattedDateTimeString(created.toInstant()));

                String timestamp = Tools.getFormattedDateTimeString(Instant.now());
                doc.replace("lastUpdated", timestamp);

                doc.remove("_version_");
                solrClient.indexDocument(doc);

                //any user-entered changes to the annotated document must initiate an overhaul of the underlying dependency data
                if (metadata.keySet().contains("annotated")) {
                    String annotated = metadata.get("annotated").toString();
                    List<NamedEntity> entities = recognizer.extractNamedEntities(annotated);

                    //must reprocess document
                    solrClient.deleteDocuments("docId:" + id);
                    List<SolrDocument> entityDocs = entities.stream()
                            .map(p -> p.mutate(id))
                            .collect(Collectors.toList());
                    SolrDocumentList solrDocs = new SolrDocumentList();
                    solrDocs.addAll(entityDocs);
                    resolveDocumentRelations(id, doc, solrDocs, doc.get("parsed").toString(), entities);
                    solrClient.indexDocuments(solrDocs);
                }

//                if (metadata.keySet().contains("annotated")) {
//                    nerModelTrainingService.process(this, (String)doc.get("category"));
//                }
                return ResponseEntity.ok().body(Tools.formJsonResponse(null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    @RequestMapping(value="/reprocess/{id}", method=RequestMethod.PUT, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> reprocessDocument(@PathVariable(name="id") String id) {
        try {
            SolrDocumentList docs = solrClient.QuerySolrDocuments("id:" + id, 1000, 0, null);
            if (!docs.isEmpty()) {
                SolrDocument doc = docs.get(0);
                String filename = doc.get("filename").toString();
                String ext = FilenameUtils.getExtension(filename);

                if (ext.equalsIgnoreCase("pdf")) {
                    solrClient.deleteDocuments("docId:" + id);
                    String docText = doc.get("docText").toString();
                    SolrDocumentList solrDocs = runNLPPipeline(docText, id, doc);
                    logger.info("storing data to Solr");
                    solrClient.indexDocuments(solrDocs);
                    solrClient.indexDocument(doc);
                }

                return ResponseEntity.ok().body(Tools.formJsonResponse(null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    @RequestMapping(value="/relations/{id}", method=RequestMethod.PUT, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> reprocessDocumentRelations(@PathVariable(name="id") String id) {
        try {
            SolrDocumentList docs = solrClient.QuerySolrDocuments("id:" + id, 1000, 0, null);
            if (!docs.isEmpty()) {
                SolrDocument doc = docs.get(0);
                String filename = doc.get("filename").toString();
                String ext = FilenameUtils.getExtension(filename);

                if (ext.equalsIgnoreCase("pdf")) {
                    //remove all Solr entries for corefs and relations
                    solrClient.deleteDocuments("docId:" + id + " AND -entity:*");

                    //Pull in the Solr documents for named entities and for locations
                    List<NamedEntity> entities = solrClient.QueryIndexedDocuments(NamedEntity.class, "docId: " + id + " AND entity:*", 100000, 0, null);

                    String parsed = doc.get("parsed").toString();

                    SolrDocumentList relDocs = new SolrDocumentList();
                    resolveDocumentRelations(id, doc, relDocs, parsed, entities);

                    logger.info("storing data to Solr");
                    solrClient.indexDocuments(relDocs);
                }

                return ResponseEntity.ok().body(Tools.formJsonResponse(null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    public void initiateNERModelTraining(String category) {
        recognizer.trainNERModel(category);
    }

    public void initiateDoccatModelTraining() {
        categorizer.trainDoccatModel();
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
            logger.error(e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    @RequestMapping(value="/{fileId}", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable(name="fileId") String fileId) {
        logger.info("In downloadDocument method");

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
