package webapp.controllers;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.mongodb.client.gridfs.model.GridFSFile;
import common.Tools;
import geoparsing.LocationResolver;
import mongoapi.DocStoreMongoClient;
import neo4japi.Neo4jClient;
import neo4japi.domain.Dependency;
import neo4japi.domain.Document;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.util.PdfBoxUtilities;
import nlp.*;
import nlp.gibberish.GibberishDetector;
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

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
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
    private final CoreferenceResolver coreferenceResolver;
    private final InformationExtractor informationExtractor;
    private GibberishDetector detector;

    private static String temporaryFileRepo = Tools.getProperty("mongodb.temporaryFileRepo");
    private static String tessdata = Tools.getProperty("tess4j.path");

    final static Logger logger = LogManager.getLogger(DocumentsController.class);

    @Autowired
    private TemporaryRepoCleanupService cleanupService;

    @Autowired
    private NERModelTrainingService nerModelTrainingService;

    @Autowired
    private TesseractOCRService tesseractOCRService;

    @Autowired
    private PDFProcessingService pdfProcessingService;

    @Autowired
    private WorkExecutorHeartbeatService workExecutorHeartbeatService;

    @Autowired
    private HttpServletRequest context;

    public DocumentsController() {
        solrClient = new SolrClient(Tools.getProperty("solr.url"));
        mongoClient = new DocStoreMongoClient(Tools.getProperty("mongodb.url"));
        neo4jClient = new Neo4jClient();
        categorizer = new DocumentCategorizer();
        recognizer = new NamedEntityRecognizer(solrClient);
        locationResolver = new LocationResolver();
        detector = new GibberishDetector();
        coreferenceResolver = new CoreferenceResolver();
        informationExtractor = new InformationExtractor();

        //This setting speeds up Tesseract OCR
        System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider");
    }

    @PostConstruct
    public void startHeartbeatMonitor() {
        workExecutorHeartbeatService.process("pdfProcessExecutor", 1000, 4);
        workExecutorHeartbeatService.process("tesseractProcessExecutor", 1000, 32);
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

    @RequestMapping(value="/entities/{id}", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> getDocumentNamedEntities(@PathVariable(name="id") String id) {
        logger.info(context.getRemoteAddr() + " -> " + "In getDocumentNamedEntities method");
        try {
            SolrDocumentList docs = solrClient.QuerySolrDocuments("docId:" + id, 1000000, 0, null);
            JsonResponse response = Tools.formJsonResponse(docs);
            logger.info(context.getRemoteAddr() + " -> " + "Returning entities");
            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    @RequestMapping(value="/annotate/{id}", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> getAutoAnnotatedDocument(@PathVariable(name="id") String id, int threshold) {
        logger.info(context.getRemoteAddr() + " -> " + "In autoAnnotate method");
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
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    @RequestMapping(value="/trainNER", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> trainNERModel(String category) {
        logger.info(context.getRemoteAddr() + " -> " + "In trainNERModel method");
        try {
            nerModelTrainingService.process(this, category);
            return ResponseEntity.ok().body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    @RequestMapping(method=RequestMethod.POST, consumes=MediaType.MULTIPART_FORM_DATA_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> createDocument(@RequestPart("metadata") Map<String, Object> metadata, @RequestPart("file") MultipartFile document) {
        try {
            logger.info(context.getRemoteAddr() + " -> " + "Storing new document");
            String filename = document.getOriginalFilename();
            File uploadedFile = Tools.WriteFileToDisk(temporaryFileRepo + filename, document.getInputStream());

            logger.info(context.getRemoteAddr() + " -> " + "Document stored");
            SolrDocumentList docs = new SolrDocumentList();

            SolrDocument doc = new SolrDocument();
            String docId = UUID.randomUUID().toString();
            doc.addField("id", docId);
            doc.addField("filename", filename);
            metadata.entrySet().stream().forEach(p -> doc.addField(p.getKey(), p.getValue()));

            logger.info(context.getRemoteAddr() + " -> " + "Metadata added to document");
            String timestamp = Tools.getFormattedDateTimeString(Instant.now());
            doc.addField("created", timestamp);
            doc.addField("lastUpdated", timestamp);

            logger.info(context.getRemoteAddr() + " -> " + "timestamp added");
            String contentType = Files.probeContentType(uploadedFile.toPath());
            logger.info(context.getRemoteAddr() + " -> " + "uploaded file type: " + contentType);
            if (contentType.compareTo("application/pdf") == 0) {
                logger.info(context.getRemoteAddr() + " -> " + "pdf document detected");
                String docText = Tools.extractPDFText(uploadedFile, detector, pdfProcessingService, tesseractOCRService);
                doc.addField("docText", docText);

                if (Strings.isNullOrEmpty(docText)) {
                    return ResponseEntity.unprocessableEntity().body(Tools.formJsonResponse(null));
                }

                docs = runPDFNLPPipeline(docText, docId, doc);
            }

            logger.info(context.getRemoteAddr() + " -> " + "storing file to MongoDB");
            ObjectId fileId = mongoClient.StoreFile(uploadedFile);
            doc.addField("docStoreId", fileId.toString());

            logger.info(context.getRemoteAddr() + " -> " + "storing data to Solr");
            docs.add(doc);
            solrClient.indexDocuments(docs);
            cleanupService.process();
            return ResponseEntity.ok().body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
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

                    String contentType = Files.probeContentType(uploadedFile.toPath());
                    if (contentType.compareTo("application/pdf") == 0) {
                        String docText = Tools.extractPDFText(uploadedFile, detector, pdfProcessingService, tesseractOCRService);
                        if (doc.containsKey("docText")) {
                            doc.replace("docText", docText);
                        } else {
                            doc.addField("docText", docText);
                        }

                        SolrDocumentList solrDocs = runPDFNLPPipeline(docText, id, doc);
                        solrClient.indexDocuments(solrDocs);
                    }
                }

                String timestamp = Tools.getFormattedDateTimeString(Instant.now());
                doc.replace("lastUpdated", timestamp);

                logger.info(context.getRemoteAddr() + " -> " + "storing data to Solr");
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

    private SolrDocumentList runPDFNLPPipeline(String docText, String id, SolrDocument doc) throws SolrServerException, IOException {
        SolrDocumentList docs = new SolrDocumentList();

        String parsed = recognizer.deepCleanText(docText);
        if (doc.containsKey("parsed")) {
            doc.replace("parsed", parsed);
        } else {
            doc.addField("parsed", parsed);
        }
        String category = Tools.removeUTF8BOM(categorizer.detectCategory(parsed));
        logger.info(context.getRemoteAddr() + " -> " + "category detected: " + category);
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

        logger.info(context.getRemoteAddr() + " -> " + "resolving locations");
        List<GeoNameWithFrequencyScore> geoNames = locationResolver.getLocationsFromDocument(parsed, id);
        List<SolrDocument> locDocs = geoNames.stream()
                .map(p -> p.mutate(id))
                .collect(Collectors.toList());
        docs.addAll(locDocs);

        if (entities.size() > 0 && geoNames.size() > 0) {
            logger.info(context.getRemoteAddr() + " -> " + "resolving coreferences");
            List<Coreference> coreferences = coreferenceResolver.getCoreferencesFromDocument(parsed, id, entities);
            List<SolrDocument> corefDocs = coreferences.stream()
                    .map(p -> p.mutate())
                    .collect(Collectors.toList());
            docs.addAll(corefDocs);

            logger.info(context.getRemoteAddr() + " -> " + "resolving entity relations");
            List<EntityRelation> entityRelations = informationExtractor.getEntityRelations(parsed, id, entities, coreferences);
            List<SolrDocument> relDocs = entityRelations.stream()
                    .map(p -> p.mutateForSolr(id))
                    .collect(Collectors.toList());
            docs.addAll(relDocs);

            if (geoNames.size() > 0) {
                Document n4jdoc = neo4jClient.addDocument(doc);
                neo4jClient.addDependencies(n4jdoc, geoNames, entityRelations);
            }
        }

        return docs;
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
//                if (metadata.keySet().contains("annotated")) {
//                    nerModelTrainingService.process(this, (String)doc.get("category"));
//                }
                return ResponseEntity.ok().body(Tools.formJsonResponse(null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
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
                    SolrDocumentList solrDocs = runPDFNLPPipeline(docText, id, doc);
                    logger.info(context.getRemoteAddr() + " -> " + "storing data to Solr");
                    solrClient.indexDocuments(solrDocs);
                    solrClient.indexDocument(doc);
                }

                return ResponseEntity.ok().body(Tools.formJsonResponse(null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Tools.formJsonResponse(null));
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null));
        }
    }

    public void initiateNERModelTraining(String category) {
        recognizer.trainNERModel(category);
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
