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
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.utils.URIUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.scheduling.annotation.Async;
import solrapi.SolrClient;
import webapp.controllers.DocumentsController;
import webapp.services.WebCrawlerService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class DocumentsWebCrawler extends WebCrawler {

    final static Logger logger = LogManager.getLogger(DocumentsWebCrawler.class);

    private static final String temporaryFileRepo = Tools.getProperty("mongodb.temporaryFileRepo");
    private static final Pattern FILTERS = Pattern.compile(".*(\\.(css|js|gif|jpg|png|mp3|mp4|zip|gz))$");

    private static final ArticleExtractor articleExtractor = new ArticleExtractor();

    private String urlSeed;
    private WebURL hostURL;
    private WebCrawlerService webCrawlerService;

    public DocumentsWebCrawler(String urlSeed, WebCrawlerService webCrawlerService) {
        this.urlSeed = urlSeed;
        this.webCrawlerService = webCrawlerService;

        hostURL = new WebURL();
        hostURL.setURL(urlSeed);
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
        try {
            String href = url.getURL().toLowerCase();
            HttpHost target = URIUtils.extractHost(new URI(urlSeed));
            boolean willVisit = !FILTERS.matcher(href).matches()
                    && href.startsWith(target.toString());

            return willVisit;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * This function is called when a page is fetched and ready
     * to be processed by your program.
     */
    @Override
    public void visit(Page page) {
        String url = page.getWebURL().getURL();

        try {
            if (page.getParseData() instanceof HtmlParseData) {
                HtmlParseData htmlParseData = (HtmlParseData) page.getParseData();
                String html = htmlParseData.getHtml();
                String docText = getArticleBody(html);
                String title = htmlParseData.getTitle();
                String filename = title.replace(" ", "_") + ".txt";
                filename = filename.replaceAll("[\\\\/:*?\"<>|]", "");
                String filepath = temporaryFileRepo + filename;
                InputStream in = IOUtils.toInputStream(docText, "UTF-8");
                File file = Tools.WriteFileToDisk(filepath, in);
                webCrawlerService.processNewDocument(filename, file, url);
            } else if (TextExtractor.extractors.containsKey(page.getContentType())) {
                String filename = FilenameUtils.getName(page.getWebURL().getURL());
                String filepath = temporaryFileRepo + filename;
                File file = new File(filepath);
                FileUtils.writeByteArrayToFile(file, page.getContentData());
                webCrawlerService.processNewDocument(filename, file, url);
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
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
