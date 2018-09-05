package webapp.controllers;

import com.mongodb.client.gridfs.model.GridFSFile;
import common.Tools;
import mongoapi.DocStoreMongoClient;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import solrapi.SolrClient;
import webapp.services.TemporaryRepoCleanupService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;

@CrossOrigin
@RestController
@RequestMapping("/api/documents")
public class DocumentsController {
    private SolrClient solrClient;
    private DocStoreMongoClient mongoClient;

    final static Logger logger = LogManager.getLogger(DocumentsController.class);

    @Autowired
    private TemporaryRepoCleanupService cleanupService;

    @Autowired
    private HttpServletRequest context;

    public DocumentsController() {
        solrClient = new SolrClient(Tools.getProperty("solr.url"));
        mongoClient = new DocStoreMongoClient(Tools.getProperty("mongodb.url"));
    }

    @RequestMapping(value="/{id}", method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable(name="id") String id) {
        logger.info(context.getRemoteAddr() + " -> " + "In downloadDocument method");

        GridFSFile fsFile = mongoClient.GetFileMetadata(id);
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
            return new ResponseEntity<>(null, null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
