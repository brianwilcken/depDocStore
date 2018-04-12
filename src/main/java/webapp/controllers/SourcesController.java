package webapp.controllers;

import common.Tools;
import eventsregistryapi.model.IndexedEventSource;
import eventsregistryapi.model.IndexedEventSourcesQueryParams;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import solrapi.SolrClient;
import webapp.models.JsonResponse;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("/api/sources")
public class SourcesController {

    final static Logger logger = LogManager.getLogger(SourcesController.class);

    private SolrClient solrClient;

    @Autowired
    private HttpServletRequest context;

    public SourcesController() {
        solrClient = new SolrClient(Tools.getProperty("solr.url"));
    }

    @RequestMapping(method=RequestMethod.GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> getSources(IndexedEventSourcesQueryParams params) {
        logger.info(context.getRemoteAddr() + " -> " + "Querying Solr for event sources based on query parameters");
        try {
            List<IndexedEventSource> articles = solrClient.QueryIndexedDocuments(IndexedEventSource.class, params.getQuery(), params.getQueryRows(), null, params.getFilterQueries());
            logger.info(context.getRemoteAddr() + " -> " + "Total number of sources found: " + articles.size());
            return ResponseEntity.ok().body(Tools.formJsonResponse(articles, params.getQueryTimeStamp()));
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null, params.getQueryTimeStamp()));
        }
    }
}
