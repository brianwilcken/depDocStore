package webapp.controllers;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.mongodb.client.gridfs.model.GridFSFile;
import common.Tools;
import geoparsing.LocationResolver;
import mongoapi.DocStoreMongoClient;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.util.PdfBoxUtilities;
import nlp.DocumentCategorizer;
import nlp.NamedEntityRecognizer;
import nlp.gibberish.GibberishDetector;
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
import webapp.models.JsonResponse;
import webapp.services.NERModelTrainingService;
import webapp.services.PDFProcessingService;
import webapp.services.TemporaryRepoCleanupService;
import webapp.services.TesseractOCRService;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@CrossOrigin
@RestController
@RequestMapping("/api/documents")
public class DocumentsController {
    private SolrClient solrClient;
    private DocStoreMongoClient mongoClient;
    private DocumentCategorizer categorizer;
    private NamedEntityRecognizer recognizer;
    private final LocationResolver locationResolver;
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
    private HttpServletRequest context;

    public DocumentsController() {
        solrClient = new SolrClient(Tools.getProperty("solr.url"));
        mongoClient = new DocStoreMongoClient(Tools.getProperty("mongodb.url"));
        categorizer = new DocumentCategorizer();
        recognizer = new NamedEntityRecognizer(solrClient);
        locationResolver = new LocationResolver();
        detector = new GibberishDetector(recognizer);

        //This setting speeds up Tesseract OCR
        System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider");
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

    @RequestMapping(value="/annotate/{id}", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> getAutoAnnotatedDocument(@PathVariable(name="id") String id, int threshold) {
        logger.info(context.getRemoteAddr() + " -> " + "In autoAnnotate method");
        try {
            SolrDocumentList docs = solrClient.QuerySolrDocuments("id:" + id, 1000, 0, null);
            if (!docs.isEmpty()) {
                SolrDocument doc = docs.get(0);

                double dblThreshold = (double)threshold / (double)100;
                String annotated = recognizer.autoAnnotate(doc.get("docText").toString(), doc.get("category").toString(), dblThreshold);

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
            List<SolrDocument> docs = new ArrayList<>();

            SolrDocument solrDocument = new SolrDocument();
            String docId = UUID.randomUUID().toString();
            solrDocument.addField("id", docId);
            solrDocument.addField("filename", filename);
            metadata.entrySet().stream().forEach(p -> solrDocument.addField(p.getKey(), p.getValue()));

            logger.info(context.getRemoteAddr() + " -> " + "Metadata added to document");
            String timestamp = Tools.getFormattedDateTimeString(Instant.now());
            solrDocument.addField("created", timestamp);
            solrDocument.addField("lastUpdated", timestamp);

            logger.info(context.getRemoteAddr() + " -> " + "timestamp added");
            String contentType = Files.probeContentType(uploadedFile.toPath());
            logger.info(context.getRemoteAddr() + " -> " + "uploaded file type: " + contentType);
            if (contentType.compareTo("application/pdf") == 0) {
                logger.info(context.getRemoteAddr() + " -> " + "pdf document detected");
                String docText = Tools.extractPDFText(uploadedFile, detector, pdfProcessingService, tesseractOCRService);
                solrDocument.addField("docText", docText);

                if (Strings.isNullOrEmpty(docText)) {
                    return ResponseEntity.unprocessableEntity().body(Tools.formJsonResponse(null));
                }

                String category = Tools.removeUTF8BOM(categorizer.detectCategory(docText));
                solrDocument.addField("category", category);
                logger.info(context.getRemoteAddr() + " -> " + "category detected: " + category);
                String parsed = recognizer.prepForAnnotation(docText);
                solrDocument.addField("parsed", parsed);
                String annotated = recognizer.autoAnnotate(docText, category, 0.5);
                //solrDocument.addField("annotated", annotated);
                //final Map<String, Double> entities = recognizer.detectNamedEntities(docText, category, 0.5);

                List<SolrDocument> locDocs = locationResolver.getLocationsFromDocument(docText, docId);

                docs.addAll(locDocs);
            }

            ObjectId fileId = mongoClient.StoreFile(uploadedFile);
            solrDocument.addField("docStoreId", fileId.toString());

            docs.add(solrDocument);
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
                        String category = Tools.removeUTF8BOM(categorizer.detectCategory(docText));
                        if (doc.containsKey("category")) {
                            doc.replace("category", category);
                        } else {
                            doc.addField("category", category);
                        }
                        String parsed = recognizer.prepForAnnotation(docText);
                        if (doc.containsKey("parsed")) {
                            doc.replace("parsed", parsed);
                        } else {
                            doc.addField("parsed", parsed);
                        }
//                        String annotated = recognizer.autoAnnotate(docText, category, 0.5);
//                        if (doc.containsKey("annotated")) {
//                            doc.replace("annotated", annotated);
//                        } else {
//                            doc.addField("annotated", annotated);
//                        }

                        //final Map<String, Double> entities = recognizer.detectNamedEntities(docText, category, 0.5);

                        List<SolrDocument> locDocs = locationResolver.getLocationsFromDocument(docText, id);
                        solrClient.indexDocuments(locDocs);
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
