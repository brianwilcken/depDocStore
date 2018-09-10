package webapp.controllers;

import com.mongodb.client.gridfs.model.GridFSFile;
import common.Tools;
import mongoapi.DocStoreMongoClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
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
import webapp.services.TemporaryRepoCleanupService;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/api/documents")
public class DocumentsController {
    private SolrClient solrClient;
    private DocStoreMongoClient mongoClient;

    private static String temporaryFileRepo = Tools.getProperty("mongodb.temporaryFileRepo");

    final static Logger logger = LogManager.getLogger(DocumentsController.class);

    @Autowired
    private TemporaryRepoCleanupService cleanupService;

    @Autowired
    private HttpServletRequest context;

    public DocumentsController() {
        solrClient = new SolrClient(Tools.getProperty("solr.url"));
        mongoClient = new DocStoreMongoClient(Tools.getProperty("mongodb.url"));
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

            SolrInputDocument solrDocument = new SolrInputDocument();
            metadata.entrySet().stream().forEach(p -> solrDocument.addField(p.getKey(), p.getValue()));
            solrDocument.addField("docStoreId", fileId.toString());

            String timestamp = Tools.getFormattedDateTimeString(Instant.now());
            solrDocument.addField("created", timestamp);
            solrDocument.addField("lastUpdated", timestamp);

            String contentType = Files.probeContentType(uploadedFile.toPath());
            if (contentType.compareTo("application/pdf") == 0) {
                String docText = Tools.extractPDFText(uploadedFile);
                solrDocument.addField("docText", docText);
            }

            solrClient.indexDocument(solrDocument);
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
                    }
                }

                String timestamp = Tools.getFormattedDateTimeString(Instant.now());
                doc.replace("lastUpdated", timestamp);

                SolrInputDocument solrInputDocument = new SolrInputDocument();

                for (String name : doc.getFieldNames()) {
                    solrInputDocument.addField(name, doc.getFieldValue(name));
                }
                solrClient.indexDocument(solrInputDocument);
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

                SolrInputDocument solrInputDocument = new SolrInputDocument();

                for (String name : doc.getFieldNames()) {
                    solrInputDocument.addField(name, doc.getFieldValue(name));
                }
                solrClient.indexDocument(solrInputDocument);
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
