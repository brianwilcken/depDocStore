package crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.DetectHtml;
import common.TextExtractor;
import common.Tools;
import de.l3s.boilerpipe.BoilerpipeProcessingException;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import edu.uci.ics.crawler4j.crawler.Page;
import edu.uci.ics.crawler4j.crawler.WebCrawler;
import edu.uci.ics.crawler4j.parser.HtmlParseData;
import edu.uci.ics.crawler4j.url.WebURL;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.scheduling.annotation.Async;
import solrapi.SolrClient;
import webapp.controllers.DocumentsController;
import webapp.services.WebCrawlerService;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class DocumentsWebCrawler extends WebCrawler {

    final static Logger logger = LogManager.getLogger(DocumentsWebCrawler.class);

    private static final String temporaryFileRepo = Tools.getProperty("mongodb.temporaryFileRepo");
    private static final Pattern FILTERS = Pattern.compile(".*(\\.(css|js|gif|jpg|png|mp3|mp4|zip|gz))$");

    private static final ArticleExtractor articleExtractor = new ArticleExtractor();
    private static final SolrClient solrClient = new SolrClient(Tools.getProperty("solr.url"));
    private static final ObjectMapper mapper = new ObjectMapper();

    private String urlSeed;
    private StringBuilder stringBuilder;
    private WebCrawlerService webCrawlerService;

    public DocumentsWebCrawler(String urlSeed, WebCrawlerService webCrawlerService, StringBuilder stringBuilder) {
        this.urlSeed = urlSeed;
        this.webCrawlerService = webCrawlerService;
        this.stringBuilder = stringBuilder;
    }

    /**
     * This method receives two parameters. The first parameter is the page
     * in which we have discovered this new url and the second parameter is
     * the new url. You should implement this function to specify whether
     * the given url should be crawled or not (based on your crawling logic).
     * In this example, we are instructing the crawler to ignore urls that
     * have css, js, git, ... extensions and to only accept urls that start
     * with "https://www.ics.uci.edu/". In this case, we didn't need the
     * referringPage parameter to make the decision.
     */
    @Override
    public boolean shouldVisit(Page referringPage, WebURL url) {
        String href = url.getURL().toLowerCase();
        return !FILTERS.matcher(href).matches()
                && href.startsWith(urlSeed);
    }

    /**
     * This function is called when a page is fetched and ready
     * to be processed by your program.
     */
    @Override
    public void visit(Page page) {
        String url = page.getWebURL().getURL();

        if (page.getParseData() instanceof HtmlParseData) {
            HtmlParseData htmlParseData = (HtmlParseData) page.getParseData();
            String html = htmlParseData.getHtml();
            String docText = getArticleBody(html);

            String title = htmlParseData.getTitle();
            stringBuilder.append(title);
            stringBuilder.append(docText);
        } else if (TextExtractor.extractors.containsKey(page.getContentType())) {
            String filename = Tools.removeSpecialCharacters(urlSeed).replace(" ", "_") + "_" + FilenameUtils.getName(page.getWebURL().getURL());
            String filepath = temporaryFileRepo + filename;
            try {
                File file = new File(filepath);
                FileUtils.writeByteArrayToFile(file, page.getContentData());
                webCrawlerService.processNewDocument(filename, file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static String getArticleBody(String html) {
        String body = null;
        try {
            body = articleExtractor.getText(html);
            if (DetectHtml.isHtml(body)) {
                body = articleExtractor.getText(body);
            }
        } catch (BoilerpipeProcessingException e) { }
        return body;
    }
}
