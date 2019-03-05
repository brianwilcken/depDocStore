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

    final static Logger logger = LogManager.getLogger(WebCrawlerService.class);

    public WebCrawlerService() {
        config = new CrawlConfig();
        config.setCrawlStorageFolder(crawlStorageFolder);
        config.setIncludeHttpsPages(true);
        config.setMaxDepthOfCrawling(1);
        config.setIncludeBinaryContentInCrawling(true);
        config.setMaxDownloadSize(104857600); //100MB
    }

    @Async("processExecutor")
    public void processAsync(String seedURL) throws Exception {
        process(seedURL);
    }

    public void process(String seedURL) throws Exception {
        PageFetcher pageFetcher = new PageFetcher(config);
        RobotstxtConfig robotstxtConfig = new RobotstxtConfig();
        RobotstxtServer robotstxtServer = new RobotstxtServer(robotstxtConfig, pageFetcher);
        CrawlController crawlController = new CrawlController(config, pageFetcher, robotstxtServer);

        DocumentsWebCrawlerFactory factory = new DocumentsWebCrawlerFactory(seedURL, this);
        crawlController.addSeed(seedURL);
        crawlController.start(factory, 1);
        crawlController.shutdown();
    }

    @Async("processExecutor")
    public void processNewDocument(String filename, File uploadedFile, String url) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("url", url);
        controller.processNewDocument(filename, metadata, uploadedFile);
    }

    public static void main(String[] args) {
        try {
            WebCrawlerService crawler = new WebCrawlerService();
            crawler.process("https://www.cctexas.com/departments/water-department");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
