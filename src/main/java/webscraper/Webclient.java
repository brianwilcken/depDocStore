package webscraper;

import geoparsing.LocationResolver;

import common.DetectHtml;
import common.Tools;
import de.l3s.boilerpipe.BoilerpipeProcessingException;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import nlp.NLPTools;
import nlp.NamedEntityRecognizer;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.stemmer.PorterStemmer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
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

    public static void main(String[] args) {
        Webclient client = new Webclient();
        //past hour
        client.queryGoogle("qdr:h", client::processSearchResults);
        //archives
        //client.queryGoogle("ar:1", client::gatherData);
    }

    public Webclient() {
        articleExtractor = new ArticleExtractor();
        ner = new NamedEntityRecognizer();
        stemmer = new PorterStemmer();
        sentModel = NLPTools.getModel(SentenceModel.class, new ClassPathResource(Tools.getProperty("nlp.sentenceDetectorModel")));
        locationResolver = new LocationResolver();
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
                        Document article = Jsoup.connect(href).userAgent(USER_AGENT).get();
                        action.accept(article, result);
                    }
                    start += 10;
                    results = getGoogleSearchResults(category, timeFrameSelector, start);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void gatherData(Document article, Element result) {
        String body = getArticleBody(article);
        String[] sentences = NLPTools.detectSentences(sentModel, body);

        List<String> lsSentences = Arrays.stream(sentences)
                .map(p -> p.replace("\n", " "))
                .filter(p -> p.endsWith("."))
                .collect(Collectors.toList());

        String data = String.join(System.lineSeparator(), lsSentences);
        try {
            Files.write(Paths.get("data/ner-training-data.txt"), data.getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processSearchResults(Document article, Element result) {
        String body = getArticleBody(article);

        extractArticleMetadata(article, result);
        //List<String> locations = ner.detectNamedEntities(body, new ClassPathResource(Tools.getProperty("nlp.locationNerModel")));
        locationResolver.resolveLocations(body);
    }

    private void extractArticleMetadata(Document article, Element result) {
        String title = article.title();

        List<String> sourceTimeStamp = result.parent().parent().select(".slp").select("span").eachText();
        String source = sourceTimeStamp.get(0);
        String timestamp = sourceTimeStamp.get(2);
        String sourceDate = getFormattedDateTimeString(timestamp);
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
            doc = Jsoup.connect("https://www.google.com/search?q=" + queryTerm + "&tbas=0&tbs=sbd:1," + timeFrameSelector + "&tbm=nws&start=" + start)
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
