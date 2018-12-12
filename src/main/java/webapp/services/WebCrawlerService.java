package webapp.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.Tools;
import crawler.DocumentsWebCrawlerFactory;
import edu.uci.ics.crawler4j.crawler.CrawlConfig;
import edu.uci.ics.crawler4j.crawler.CrawlController;
import edu.uci.ics.crawler4j.fetcher.PageFetcher;
import edu.uci.ics.crawler4j.robotstxt.RobotstxtConfig;
import edu.uci.ics.crawler4j.robotstxt.RobotstxtServer;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import solrapi.SolrClient;
import webapp.controllers.DocumentsController;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

@Service
public class WebCrawlerService {

    private static final String crawlStorageFolder = Tools.getProperty("webCrawler.crawlStorageFolder");
    private static final String temporaryFileRepo = Tools.getProperty("mongodb.temporaryFileRepo");

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final SolrClient solrClient = new SolrClient(Tools.getProperty("solr.url"));

    @Autowired
    private DocumentsController controller;

    private CrawlConfig config;
    private PageFetcher pageFetcher;
    private RobotstxtConfig robotstxtConfig;
    private RobotstxtServer robotstxtServer;

    final static Logger logger = LogManager.getLogger(WebCrawlerService.class);

    public WebCrawlerService() {
        config = new CrawlConfig();
        config.setCrawlStorageFolder(crawlStorageFolder);
        config.setIncludeHttpsPages(true);
        config.setIncludeBinaryContentInCrawling(true);
        config.setMaxDownloadSize(104857600); //100MB

        pageFetcher = new PageFetcher(config);
        robotstxtConfig = new RobotstxtConfig();
        robotstxtServer = new RobotstxtServer(robotstxtConfig, pageFetcher);
    }

    @Async("processExecutor")
    public void processAsync(String seedURL) throws Exception {
        process(seedURL);
    }

    public void process(String seedURL) throws Exception {
        CrawlController crawlController = new CrawlController(config, pageFetcher, robotstxtServer);

        StringBuilder stringBuilder = new StringBuilder();
        DocumentsWebCrawlerFactory factory = new DocumentsWebCrawlerFactory(seedURL, this, stringBuilder);

        String articleFilename = Tools.removeSpecialCharacters(seedURL).replace(" ", "_") + ".txt";
        String id = initId(articleFilename);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", id);
        String query = "id:" + id;

        if (!solrClient.DocumentExists(query)) {
            crawlController.addSeed(seedURL);
            crawlController.start(factory, 1);

            String docText = stringBuilder.toString();
            String filepath = temporaryFileRepo + articleFilename;
            try {
                InputStream in = IOUtils.toInputStream(docText, "UTF-8");
                File uploadedFile = Tools.WriteFileToDisk(filepath, in);
                controller.processNewDocument(articleFilename, metadata, uploadedFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Async("processExecutor")
    public void processNewDocument(String filename, File uploadedFile) {
        String id = initId(filename);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", id);
        String query = "id:" + id;
        try {
            if (!solrClient.DocumentExists(query)) {
                controller.processNewDocument(filename, metadata, uploadedFile);
            }
        } catch (SolrServerException e) { }
    }

    public static String initId(String articleFilename) {
        try {
            String id = Hex.encodeHexString(MessageDigest.getInstance("SHA-1").digest(mapper.writeValueAsBytes(articleFilename)));
            return id;
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    public static void main(String[] args) {
        try {
            WebCrawlerService crawler = new WebCrawlerService();
            crawler.process("http://fortworthtexas.gov/water/");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
