package crawler;

import edu.uci.ics.crawler4j.crawler.CrawlController;
import edu.uci.ics.crawler4j.crawler.WebCrawler;
import webapp.services.WebCrawlerService;

public class DocumentsWebCrawlerFactory implements CrawlController.WebCrawlerFactory {
    private String urlSeed;
    private WebCrawlerService webCrawlerService;

    public DocumentsWebCrawlerFactory(String urlSeed, WebCrawlerService webCrawlerService) {
        this.urlSeed = urlSeed;
        this.webCrawlerService = webCrawlerService;
    }

    @Override
    public WebCrawler newInstance() {
        return new DocumentsWebCrawler(urlSeed, webCrawlerService);
    }
}
