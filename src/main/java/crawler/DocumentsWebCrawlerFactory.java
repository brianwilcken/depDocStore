package crawler;

import edu.uci.ics.crawler4j.crawler.CrawlController;
import edu.uci.ics.crawler4j.crawler.WebCrawler;
import webapp.services.WebCrawlerService;

import java.util.Map;

public class DocumentsWebCrawlerFactory implements CrawlController.WebCrawlerFactory {
    private String urlSeed;
    private WebCrawlerService webCrawlerService;
    private Map<String, Object> searchData;

    public DocumentsWebCrawlerFactory(String urlSeed, Map<String, Object> searchData, WebCrawlerService webCrawlerService) {
        this.urlSeed = urlSeed;
        this.webCrawlerService = webCrawlerService;
        this.searchData = searchData;
    }

    @Override
    public WebCrawler newInstance() {
        return new DocumentsWebCrawler(urlSeed, searchData, webCrawlerService);
    }
}
