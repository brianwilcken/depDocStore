package webapp.services;

import common.Tools;
import crawler.DocumentsWebCrawlerFactory;
import edu.uci.ics.crawler4j.crawler.CrawlConfig;
import edu.uci.ics.crawler4j.crawler.CrawlController;
import edu.uci.ics.crawler4j.fetcher.PageFetcher;
import edu.uci.ics.crawler4j.robotstxt.RobotstxtConfig;
import edu.uci.ics.crawler4j.robotstxt.RobotstxtServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import webapp.controllers.DocumentsController;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Service
public class WebCrawlerService {

    private static final String crawlStorageFolder = Tools.getProperty("webCrawler.crawlStorageFolder");

    @Autowired
    private DocumentsController controller;

    private CrawlConfig config;

    final static Logger logger = LogManager.getLogger(WebCrawlerService.class);

    public WebCrawlerService() {
        config = new CrawlConfig();
        config.setCrawlStorageFolder(crawlStorageFolder);
        config.setIncludeHttpsPages(true);
        config.setMaxDepthOfCrawling(1);
        config.setMaxPagesToFetch(1000);
        config.setIncludeBinaryContentInCrawling(true);
        config.setMaxDownloadSize(104857600); //100MB
    }

    public void setMaxCrawlDepth(int depth) {
        config.setMaxDepthOfCrawling(depth);
    }

    @Async("processExecutor")
    public void processAsync(String seedURL, Map<String, Object> searchData) throws Exception {
        process(seedURL, searchData);
    }

    public void process(String seedURL, Map<String, Object> searchData) throws Exception {
        PageFetcher pageFetcher = new PageFetcher(config);
        RobotstxtConfig robotstxtConfig = new RobotstxtConfig();
        RobotstxtServer robotstxtServer = new RobotstxtServer(robotstxtConfig, pageFetcher);
        CrawlController crawlController = new CrawlController(config, pageFetcher, robotstxtServer);

        DocumentsWebCrawlerFactory factory = new DocumentsWebCrawlerFactory(seedURL, searchData, this);
        crawlController.addSeed(seedURL);
        crawlController.start(factory, 4);
        crawlController.shutdown();
    }

    @Async("processExecutor")
    public void processNewDocument(String filename, File uploadedFile, String url, Map<String, Object> searchData) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("url", url);
        metadata.putAll(searchData);
        controller.processNewDocument(filename, metadata, uploadedFile);
    }
}
