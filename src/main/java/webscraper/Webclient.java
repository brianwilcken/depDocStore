package webscraper;

import com.bericotech.clavin.gazetteer.GeoName;
import com.bericotech.clavin.resolver.ResolvedLocation;
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
import org.apache.solr.client.solrj.SolrServerException;
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

public class Webclient {

    private String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.181 Safari/537.36";

    private ArticleExtractor articleExtractor;
    private NamedEntityRecognizer ner;
    private PorterStemmer stemmer;
    private SentenceModel sentModel;
    private LocationResolver locationResolver;
    private SolrClient solrClient;

    public static void main(String[] args) {
        Webclient client = new Webclient();
        //past hour
        //client.queryGoogle("qdr:h", client::processSearchResults);
        //archives
        //client.queryGoogle("ar:1", client::gatherData);
        client.queryGoogle("qdr:h", client::gatherData);
    }

    public Webclient() {
        articleExtractor = new ArticleExtractor();
        ner = new NamedEntityRecognizer();
        stemmer = new PorterStemmer();
        sentModel = NLPTools.getModel(SentenceModel.class, new ClassPathResource(Tools.getProperty("nlp.sentenceDetectorModel")));
        locationResolver = new LocationResolver();
        solrClient = new SolrClient(Tools.getProperty("solr.url"));
    }

    private void queryGoogle(String timeFrameSelector, BiConsumer<Document, Element> action) {
        String[] queryCategories = getQueryCategories();
        for (String category : queryCategories) {
            try {
                int start = 0;

                Elements results = getGoogleSearchResults(category, timeFrameSelector, start);
                while (results.eachText().size() > 0) {
                    for (Element result : results){
                        String href = result.attr("href");
                        try {
                            Document article = Jsoup.connect(href).userAgent(USER_AGENT).get();
                            action.accept(article, result);
                        }
                        catch (HttpStatusException e) {}
                    }
                    start += 10;
                    results = getGoogleSearchResults(category, timeFrameSelector, start);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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

    private void processSearchResults(Document article, Element result) {
        String body = getArticleBody(article);

        IndexedEventSource source = extractArticleMetadata(article, result);
        if (source != null) {
            source.setSummary(body);
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

            //index the source data to solr
            try {
                if (indexedLocations.size() > 0) {
                    List<IndexedEventSource> coll = new ArrayList<>();
                    coll.add(source);
                    solrClient.indexDocuments(coll);
                    solrClient.indexDocuments(indexedLocations);
                }
            } catch (SolrServerException e) {
                e.printStackTrace();
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
