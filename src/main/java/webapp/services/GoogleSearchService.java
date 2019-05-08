package webapp.services;

import common.Tools;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

@Service
public class GoogleSearchService {

    final static Logger logger = LogManager.getLogger(GoogleSearchService.class);
    private static String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.181 Safari/537.36";
    private final ArticleExtractor articleExtractor;
    private static final int REQUEST_DELAY = 30000;

    @Autowired
    private WebCrawlerService webCrawlerService;

    @Autowired
    private ResourceURLLookupService resourceURLLookupService;

    public GoogleSearchService() {
        articleExtractor = new ArticleExtractor();
    }

    public int queryGoogle(String searchTerm, int resultLimit) {
        int totalArticles = 0;
        int start = 0;

        logger.info("Searching Google for: " + searchTerm);
        Elements results = getGoogleSearchResults(searchTerm, start);
        if (results != null && results.eachText() != null) {
            while (results.eachText().size() > 0) {
                int resultsSize = results.eachText().size();
                totalArticles += resultsSize;
                for (Element result : results){
                    String href = result.attr("href");
                    try {
                        URL url = new URL(href);
                        String filename = Tools.removeFilenameSpecialCharacters(FilenameUtils.getName(url.getFile()));
                        if(resourceURLLookupService.verifyURLFilenameIsFile(filename)) {
                            logger.info("Downloading a file: " + filename);
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("url", href);
                            resourceURLLookupService.process(url, metadata);
                            logger.info("File download and processing complete: " + filename);
                        } else {
                            logger.info("Crawling this address: " + href);
                            webCrawlerService.setMaxCrawlDepth(2);
                            webCrawlerService.process(href);
                            logger.info("Successfully crawled: " + href);
                        }

                    }
                    catch (Exception e) {
                        logger.info("Failed to crawl: " + href);
                        logger.error(e.getMessage(), e);
                    }
                }
                start += 10;
                if (start > resultLimit) {
                    break;
                }
                logger.info("Getting next page of Google results for: " + searchTerm);
                results = getGoogleSearchResults(searchTerm, start);
            }
        }
        return totalArticles;
    }

    private Elements getGoogleSearchResults(String queryTerm, int start) {
        Document doc;
        try {
            if (!queryTerm.startsWith("#")) {
                doc = Jsoup.connect("https://www.google.com/search?q=" + queryTerm + "&cr=countryUS&lr=lang_en&start=" + start)
                        .userAgent(USER_AGENT)
                        .get();
            } else {
                return null;
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return null;
        }

        Elements results = doc.select("h3").parents();
        Elements links = new Elements();
        for (Element result : results) {
            if (result.tagName().equals("a")) {
                links.add(result);
            }
        }

        return links;
    }
}
