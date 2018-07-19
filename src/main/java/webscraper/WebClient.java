package webscraper;

import com.bericotech.clavin.gazetteer.GeoName;
import com.bericotech.clavin.resolver.ResolvedLocation;
import nlp.EventCategorizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import solrapi.model.IndexedEvent;
import solrapi.model.IndexedEventSource;
import geoparsing.LocationResolver;

import common.DetectHtml;
import common.Tools;
import de.l3s.boilerpipe.BoilerpipeProcessingException;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import nlp.NLPTools;
import nlp.NamedEntityRecognizer;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.stemmer.PorterStemmer;
//import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
//import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
//import org.deeplearning4j.models.word2vec.VocabWord;
//import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
//import org.deeplearning4j.text.documentiterator.LabelsSource;
//import org.deeplearning4j.text.sentenceiterator.BasicLineIterator;
//import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
//import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
//import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
//import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.core.io.ClassPathResource;
import solrapi.SolrClient;
import solrapi.model.IndexedEventSourceLocation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WebClient {

    final static Logger logger = LogManager.getLogger(WebClient.class);

    private String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.181 Safari/537.36";

    private final ArticleExtractor articleExtractor;
    private final NamedEntityRecognizer ner;
    private final PorterStemmer stemmer;
    private final SentenceModel sentModel;
    private final LocationResolver locationResolver;
    private final SolrClient solrClient;
    private final EventCategorizer categorizer;

    private static final int REQUEST_DELAY = 300000;

    public static final String QUERY_TIMEFRAME_LAST_HOUR = "qdr:h";
    public static final String QUERY_TIMEFRAME_ALL_ARCHIVED = "ar:1";

    public static void main(String[] args) {
        WebClient client = new WebClient();
        //past hour
        client.queryGoogle(QUERY_TIMEFRAME_LAST_HOUR, client::processSearchResult);
        //archives
        //client.queryGoogle(QUERY_TIMEFRAME_ALL_ARCHIVED, client::gatherData);
        //client.queryGoogle(QUERY_TIMEFRAME_LAST_HOUR, client::gatherData);
    }

    public WebClient() {
        articleExtractor = new ArticleExtractor();
        ner = new NamedEntityRecognizer();
        stemmer = new PorterStemmer();
        categorizer = new EventCategorizer();
        sentModel = NLPTools.getModel(SentenceModel.class, new ClassPathResource(Tools.getProperty("nlp.sentenceDetectorModel")));
        locationResolver = new LocationResolver();
        solrClient = new SolrClient(Tools.getProperty("solr.url"));
    }

    public int queryGoogle(String timeFrameSelector, BiConsumer<Document, Element> action) {
        String[] queryCategories = getQueryCategories();
        int totalArticles = 0;
        for (String category : queryCategories) {
            try {
                int start = 0;

                logger.info("Searching Google for: " + category);
                Elements results = getGoogleSearchResults(category, timeFrameSelector, start);
                if (results != null && results.eachText() != null) {
                    while (results.eachText().size() > 0) {
                        int resultsSize = results.eachText().size();
                        totalArticles += resultsSize;
                        for (Element result : results){
                            String href = result.attr("href");
                            try {
                                Document article = Jsoup.connect(href).userAgent(USER_AGENT).get();
                                action.accept(article, result);
                                logger.info("Scraped data from: " + href);
                            }
                            catch (HttpStatusException e) {}
                        }
                        if (resultsSize >= 10) {
                            start += resultsSize;
                            Thread.sleep(REQUEST_DELAY);
                            results = getGoogleSearchResults(category, timeFrameSelector, start);
                        } else {
                            break;
                        }
                    }
                }
                Thread.sleep(REQUEST_DELAY);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return totalArticles;
    }

    private File gatherData(Document article, Element result) {
        String body = getArticleBody(article);
        String[] sentences = NLPTools.detectSentences(sentModel, body);

        List<String> lsSentences = Arrays.stream(sentences)
                .map(p -> p.replace("\n", " "))
                .filter(p -> p.endsWith("."))
                .collect(Collectors.toList());

        if (lsSentences.size() > 0) {
            String data = String.join(" ", lsSentences) + System.lineSeparator();
            try {
                File file = Files.write(Paths.get("data/p2v-training-data.txt"), data.getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE).toFile();
                return file;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

//    private void generateParagraphVectors() {
//        File file = new File("data/p2v-training-data.txt");
//        SentenceIterator iter = new BasicLineIterator(file);
//        AbstractCache<VocabWord> cache = new AbstractCache<>();
//
//        TokenizerFactory t = new DefaultTokenizerFactory();
//        t.setTokenPreProcessor(new CommonPreprocessor());
//
//        LabelsSource source = new LabelsSource("DOC_");
//
//        ParagraphVectors vec = new ParagraphVectors.Builder()
//                .minWordFrequency(1)
//                .iterations(5)
//                .epochs(1)
//                .layerSize(100)
//                .learningRate(0.025)
//                .labelsSource(source)
//                .windowSize(5)
//                .iterate(iter)
//                .trainWordVectors(false)
//                .vocabCache(cache)
//                .tokenizerFactory(t)
//                .sampling(0)
//                .build();
//
//        vec.fit();
//
//        WordVectorSerializer.writeParagraphVectors(vec, "paragraphVectorsModel.zip");
//    }

    public void processSearchResult(Document article, Element result) {
        String body = getArticleBody(article);

        IndexedEventSource source = extractArticleMetadata(article, result);
        if (source != null) {
            source.setSummary(body);
            IndexedEvent event = source.getIndexedEvent();
            List<IndexedEventSourceLocation> indexedLocations = new ArrayList<>();
            List<ResolvedLocation> locations = locationResolver.resolveLocations(body);
            for (ResolvedLocation location : locations) {
                GeoName geoname = location.getGeoname();
                if (geoname.getPrimaryCountryCode().name().compareTo("US") == 0 &&
                        !geoname.isTopLevelAdminDivision() &&
                        geoname.getFeatureCode().name().compareTo("ADM1") != 0) {
                    IndexedEventSourceLocation loc = new IndexedEventSourceLocation();
                    loc.setSourceId(source.getId());
                    loc.setLocation(geoname.getName() + ", " + geoname.getAdmin1Code());
                    loc.setLatitude(Double.toString(geoname.getLatitude()));
                    loc.setLongitude(Double.toString(geoname.getLongitude()));
                    loc.initId();
                    if (!indexedLocations.stream().anyMatch(p -> p.getId().compareTo(loc.getId()) == 0)) {
                        indexedLocations.add(loc);
                    }
                }
            }

            if (indexedLocations.size() > 0) {
                IndexedEventSourceLocation loc = indexedLocations.get(0);
                event.setLocation(loc.getLocation());
                event.setLatitude(loc.getLatitude());
                event.setLongitude(loc.getLongitude());

                List<IndexedEventSource> sources = new ArrayList<>();
                sources.add(source);

                try {
                    List<IndexedEvent> events = new ArrayList<>();
                    events.add(event);
                    List<IndexedEvent> updEvents = solrClient.QueryIndexedDocuments(IndexedEvent.class, "id:" + event.getId(), 1, 0, null);
                    if (!updEvents.isEmpty()) {
                        IndexedEvent updEvent = updEvents.get(0);
                        event.updateForDynamicFields(updEvent);
                    } else {
                        categorizer.detectEventDataCategories(events);
                    }

                    solrClient.indexDocuments(events);
                    solrClient.indexDocuments(sources);
                } catch (SolrServerException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private IndexedEventSource extractArticleMetadata(Document article, Element result) {
        String title = article.title();

        List<String> sourceTimeStamp = result.parent().parent().select(".slp").select("span").eachText();
        if (sourceTimeStamp.size() > 0) {
            String sourceName = sourceTimeStamp.get(0);
            String timestamp = sourceTimeStamp.get(2);
            String articleDate = getFormattedDateTimeString(timestamp);

            IndexedEventSource source = new IndexedEventSource();
            source.setTitle(title);
            source.setSourceName(sourceName);
            source.setArticleDate(articleDate);
            source.setUrl(article.location());
            source.setUri("N/A");
            source.initId();

            return source;
        }
        return null;
    }

    private String getArticleBody(Document article) {
        String body = null;
        try {
            body = articleExtractor.getText(article.body().html());
            if (DetectHtml.isHtml(body)) {
                body = articleExtractor.getText(body);
            }
        } catch (BoilerpipeProcessingException e) {
            e.printStackTrace();
        }
        return body;
    }

    private Elements getGoogleSearchResults(String queryTerm, String timeFrameSelector, int start) {
        Document doc = null;
        try {
            doc = Jsoup.connect("https://www.google.com/search?q=" + queryTerm + "&cr=countryUS&lr=lang_en&tbas=0&tbs=sbd:1," + timeFrameSelector + ",lr:lang_1en,ctr:countryUS&tbm=nws&start=" + start)
                    .userAgent(USER_AGENT)
                    .get();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return null;
        }

        Elements results = doc.select("h3.r a");

        return results;
    }

    private String getFormattedDateTimeString(String timestamp) {
        String sourceDate = null;
        if (timestamp.contains("ago")) {
            Integer ago = extractNumericFromString(timestamp);
            if (ago != null) {
                long millis = 0;
                if (timestamp.contains("hour")) {
                    millis = TimeUnit.HOURS.toMillis(ago);
                } else if (timestamp.contains("minute")) {
                    millis = TimeUnit.MINUTES.toMillis(ago);
                }
                sourceDate = Tools.getFormattedDateTimeString(Instant.now().minusMillis(millis));
            }
        } else {
            String pattern = "MMM dd, yyyy";
            DateFormat df = new SimpleDateFormat(pattern);
            try {
                Date date = df.parse(timestamp);
                sourceDate = Tools.getFormattedDateTimeString(date.toInstant());
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        return sourceDate;
    }

    private Integer extractNumericFromString(String input) {
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            Integer num = Integer.decode(matcher.group());
            return num;
        }
        return null;
    }

    private String[] getQueryCategories() {
        String hazardTerms = Tools.getResource(Tools.getProperty("google.hazardTerms")).toLowerCase();
        String[] hazardTermsArr = hazardTerms.split(System.lineSeparator());
        return hazardTermsArr;
    }
}
