package crawler;

import edu.uci.ics.crawler4j.crawler.CrawlController;
import edu.uci.ics.crawler4j.crawler.WebCrawler;
import webapp.services.WebCrawlerService;

public class DocumentsWebCrawlerFactory implements CrawlController.WebCrawlerFactory {
    private String urlSeed;
    private StringBuilder stringBuilder;
    private WebCrawlerService webCrawlerService;

    public DocumentsWebCrawlerFactory(String urlSeed, WebCrawlerService webCrawlerService, StringBuilder stringBuilder) {
        this.urlSeed = urlSeed;
        this.webCrawlerService = webCrawlerService;
        this.stringBuilder = stringBuilder;
    }

    @Override
    public WebCrawler newInstance() {
        return new DocumentsWebCrawler(urlSeed, webCrawlerService, stringBuilder);
    }
}
