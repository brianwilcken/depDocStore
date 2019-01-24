package webapp.services;

import com.google.common.base.Strings;
import common.DetectHtml;
import common.TextExtractor;
import common.Tools;
import de.l3s.boilerpipe.BoilerpipeProcessingException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import webapp.controllers.DocumentsController;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import de.l3s.boilerpipe.extractors.ArticleExtractor;

@Service
public class ResourceURLLookupService {
    final static Logger logger = LogManager.getLogger(ResourceURLLookupService.class);

    private static final String temporaryFileRepo = Tools.getProperty("mongodb.temporaryFileRepo");
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.181 Safari/537.36";
    private final RestTemplate restTemplate;
    private final ArticleExtractor articleExtractor;

    public ResourceURLLookupService() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        restTemplate = new RestTemplate(requestFactory);
        articleExtractor = new ArticleExtractor();
    }

    @Autowired
    private DocumentsController controller;

    @Async("processExecutor")
    public void processAsync(URL url, Map<String, Object> metadata) {
        process(url, metadata);
    }

    public void process(URL url, Map<String, Object> metadata) {
        try {
            String filename = FilenameUtils.getName(url.getFile()).replaceAll("[\\\\/:*?\"<>|]", "");
            if (!Strings.isNullOrEmpty(filename) && TextExtractor.canExtractText(new File(filename))) {
                RequestCallback requestCallback = request -> request.getHeaders()
                        .setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL));

                ResponseExtractor<Void> responseExtractor = response -> {
                    Path path = Paths.get(temporaryFileRepo + filename);
                    Files.deleteIfExists(path);
                    Files.copy(response.getBody(), path);
                    File uploadedFile = path.toFile();
                    controller.processNewDocument(filename, metadata, uploadedFile);
                    return null;
                };

                URI uri = url.toURI();

                restTemplate.execute(uri, HttpMethod.GET, requestCallback, responseExtractor);
            } else {
                try {
                    logger.info("Scraping data from: " + url.toString());
                    Document article = Jsoup.connect(url.toString()).userAgent(USER_AGENT).get();
                    String docText = getArticleBody(article);
                    String title = article.title();
                    String articleFilename = title.replace(" ", "_") + ".txt";
                    articleFilename = articleFilename.replaceAll("[\\\\/:*?\"<>|]", "");
                    String filepath = temporaryFileRepo + articleFilename;
                    InputStream in = IOUtils.toInputStream(docText, "UTF-8");
                    File uploadedFile = Tools.WriteFileToDisk(filepath, in);
                    controller.processNewDocument(articleFilename, metadata, uploadedFile);
                    logger.info("Successfully scraped data from: " + url.toString());
                }
                catch (Exception e) {
                    logger.info("Failed to scrape data from: " + url.toString());
                    logger.error(e.getMessage(), e);
                }
            }
        } catch (URISyntaxException e) {

        }
    }

    private String getArticleBody(Document article) {
        String body = null;
        try {
            body = articleExtractor.getText(article.body().html());
            if (DetectHtml.isHtml(body)) {
                body = articleExtractor.getText(body);
            }
        } catch (BoilerpipeProcessingException e) { }
        return body;
    }
}
