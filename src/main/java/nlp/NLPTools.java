package nlp;

import common.SpellChecker;
import common.Tools;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.util.CoreMap;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.*;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.util.Version;
import org.apache.solr.common.SolrDocument;
import org.springframework.core.io.ClassPathResource;
import sun.awt.Mutex;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NLPTools {
    final static Logger logger = LogManager.getLogger(NLPTools.class);
    private static final String stopwordsText = Tools.getResource(Tools.getProperty("nlp.stopwords"));
    private static final Stemmer stemmer = new PorterStemmer();
    private static TreeSet stopwords;
    private static Mutex mutex;

    //used for setting a backup category for the case of a Not_Applicable doccat or LDA category
    private static final double MIN_LDA_PROB = 0.35;
    private static final double MIN_DOCCAT_PROB = 0.15;

    public static final String CORPUS_DATA_DELIMITER = "@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@";

    static {
        List<String> wordsList = Arrays.asList(stopwordsText.split("\\n"));
        stopwords = new TreeSet<>();
        stopwords.addAll(wordsList);
        mutex = new Mutex();
    }

    public static TrainingParameters getTrainingParameters(int iterations, int cutoff) {
        TrainingParameters mlParams = new TrainingParameters();
        mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
        mlParams.put(TrainingParameters.TRAINER_TYPE_PARAM, EventTrainer.EVENT_VALUE);
        mlParams.put(TrainingParameters.ITERATIONS_PARAM, iterations);
        mlParams.put(TrainingParameters.CUTOFF_PARAM, cutoff);
        mlParams.put(TrainingParameters.THREADS_PARAM, Runtime.getRuntime().availableProcessors());

        return mlParams;
    }

    public static final class TrainingParameterTracker {
        private int iStart = 25; //Starting iterations
        private int iStep = 5; //Iteration step size
        private int iStop = 100; //Max iterations
        private int iSize = (iStop - iStart)/iStep + 1;

        private int cStart = 1; //Starting cutoff
        private int cStep = 1; //Cutoff step size
        private int cStop = 9; //Max cutoff
        private int cSize = (cStop - cStart)/cStep + 1;

        private OptimizationTuple current;
        private int coordI;
        private int coordC;

        private OptimizationTuple[][] grid; //keeps track of performance measures for each i/c pair

        private void makeGrid() {
            grid = new OptimizationTuple[iSize][cSize];

            for (int i = 0; i < iSize; i++) {
                for (int c = 0; c < cSize; c++) {
                    int iParam = iStart + (iStep * i);
                    int cParam = cStart + (cStep * c);
                    grid[i][c] = new OptimizationTuple(iParam, cParam);
                }
            }
        }

        public TrainingParameterTracker() {
            makeGrid();
            coordI = 0;
            coordC = 0;
            current = null;
        }

        public Boolean hasNext() {
            if (current == null) {
                return true;
            }
            return coordI <= (iSize - 1) || coordC <= (cSize - 1);
        }

        private void testLimitForOptimization() {
            double threshold = 0.01; //must be at least 1% improvement since previous step in order to continue
            int prevI = coordI - 1;
            int prevI2 = coordI - 2;
            double dPdi = 0;
            Boolean iCalc = false;
            int prevC = coordC - 1;
            int prevC2 = coordC - 2;
            double dPdc = 0;
            Boolean cCalc = false;


            if (prevI >= 0 && prevI2 >= 0) {
                //calculate the 1st derivative dP/di.
                OptimizationTuple prev = grid[prevI][coordC];
                OptimizationTuple prev2 = grid[prevI2][coordC];
                dPdi = (prev.P - prev2.P)/prevI;
                iCalc = true;

            }

            if (prevC >= 0 && prevC2 >= 0) {
                //calculate the 1st derivative dP/dc.
                OptimizationTuple prev = grid[coordI][prevC];
                OptimizationTuple prev2 = grid[coordI][prevC2];
                dPdc = (prev.P - prev2.P)/prevC;
                cCalc = true;
            }

            //A negative derivative here is a good indication of overfitting.
            if (iCalc && cCalc) {
                //calculate the 2nd 2D derivative d2P/didc
                double d2Pdidc = dPdi * dPdc;
                //If the 2nd 2D derivative is less than threshold this is a good indication that further
                //optimization will yield diminishing returns.  It's time to stop optimizing.
                if (d2Pdidc < 0 || d2Pdidc < threshold) {
                    coordI = iSize;
                    coordC = cSize;
                }
            }
            else if (iCalc && !cCalc) {
                //If dP/di<threshold then increment coordC and reset coordI to 0.
                if (dPdi < 0 || dPdi < threshold) {
                    ++coordC;
                    coordI = 0;
                }
            }
        }

        public OptimizationTuple getNext() {
            testLimitForOptimization();
            if (coordI <= (iSize - 1)) {
                current = grid[coordI++][coordC];
            } else if (++coordC <= (cSize - 1)){
                coordI = 0;
                current = grid[coordI][coordC];
            }
            return current;
        }

        public OptimizationTuple getBest() {
            OptimizationTuple best = null;

            for (int i = 0; i < iSize; i++) {
                for (int c = 0; c < cSize; c++) {
                    if (best == null) {
                        best = grid[i][c];
                        continue;
                    }
                    OptimizationTuple current = grid[i][c];
                    if (best.P < current.P) {
                        best = current;
                    }
                }
            }
            return best;
        }
    }

    public static <T> T getModel(Class<T> clazz, ClassPathResource modelResource) {
        try (InputStream modelIn = modelResource.getInputStream()) {

            Constructor<?> cons = clazz.getConstructor(InputStream.class);

            T o = (T) cons.newInstance(modelIn);

            return o;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    public static <T> T getModel(Class<T> clazz, String modelFilePath) throws IOException {
        try (InputStream modelIn = new FileInputStream(modelFilePath)) {

            Constructor<?> cons = clazz.getConstructor(InputStream.class);

            T o = (T) cons.newInstance(modelIn);

            return o;
        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException |
                IllegalArgumentException | InvocationTargetException e) {
            logger.fatal(e.getMessage(), e);
        }
        return null;
    }

    public static ObjectStream<String> getLineStreamFromString(final String data)
    {
        ObjectStream<String> lineStream = null;
        try {
            InputStreamFactory factory = new InputStreamFactory() {
                public InputStream createInputStream() throws IOException {
                    return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
                }
            };

            lineStream = new PlainTextByLineStream(factory, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return lineStream;
    }

    public static ObjectStream<String> getLineStreamFromFile(final String filePath)
    {
        ObjectStream<String> lineStream = null;
        try {
            InputStreamFactory factory = new InputStreamFactory() {
                public InputStream createInputStream() throws IOException {
                    return new FileInputStream(filePath);
                }
            };

            lineStream = new PlainTextByLineStream(factory, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return lineStream;
    }

    public static ObjectStream<String> getLineStreamFromMarkableFile(final String filePath)
    {
        ObjectStream<String> lineStream = null;
        try {
            MarkableFileInputStreamFactory factory = new MarkableFileInputStreamFactory(new File(filePath));

            lineStream = new PlainTextByLineStream(factory, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return lineStream;
    }

    public static String[] detectSentences(SentenceModel model, String input) {
        SentenceDetectorME sentenceDetector = new SentenceDetectorME(model);

        String[] sentences = sentenceDetector.sentDetect(input);

        return sentences;
    }

    public static String[] detectSentences(String input) {
        SentenceModel model = getModel(SentenceModel.class, new ClassPathResource(Tools.getProperty("nlp.sentenceDetectorModel")));

        return detectSentences(model, input);
    }

    public static String[] detectTokens(TokenizerModel model, String input) {
        TokenizerME tokenDetector = new TokenizerME(model);

        String[] tokens = tokenDetector.tokenize(input);

        return tokens;
    }

    public static String[] detectTokens(String input) {
        TokenizerModel model = getModel(TokenizerModel.class, new ClassPathResource(Tools.getProperty("nlp.tokenizerModel")));

        return detectTokens(model, input);
    }

    public static List<NamedEntity> extractNamedEntities(String annotated) {
        Pattern docPattern = Pattern.compile(" ?<START:.+?<END>");
        Pattern entityTypePattern = Pattern.compile("(?<=:).+?(?=>)");

        annotated = annotated.replaceAll("[\\(]", "-LP-");
        annotated = annotated.replaceAll("[\\)]", "-RP-");
        List<CoreMap> sentencesList = NLPTools.detectSentencesStanford(annotated);
        String[] sentences = sentencesList.stream().map(p -> p.toString()).toArray(String[]::new);

        List<NamedEntity> entities = new ArrayList<>();
        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i];
            List<CoreLabel> tokens = NLPTools.detectTokensStanford(sentence);
            Matcher sentMatcher = docPattern.matcher(sentence);
            int tagTokenNum = 0;
            while(sentMatcher.find()) {
                int annotatedStart = sentMatcher.start();
                int annotatedEnd = sentMatcher.end();
                List<CoreLabel> spanTokens = tokens.stream()
                        .filter(p -> (annotatedStart == 0 || p.beginPosition() > annotatedStart) && p.endPosition() <= annotatedEnd)
                        .collect(Collectors.toList());
                CoreLabel startToken = spanTokens.get(0);
                Matcher typeMatcher = entityTypePattern.matcher(startToken.value());
                String type = null;
                if (typeMatcher.find()) {
                    type = startToken.value().substring(typeMatcher.start(), typeMatcher.end());
                    type = type.replace("-LP-", "(");
                    type = type.replace("-RP-", ")");
                }
                List<CoreLabel> entityTokens = spanTokens.subList(1, spanTokens.size() - 1); // extract just the tokens that comprise the entity

                String[] entityTokensArr = entityTokens.stream().map(p -> p.toString()).toArray(String[]::new);
                String entity = String.join(" ", entityTokensArr);
                int tokenIndexDecrement = 1 + 2 * tagTokenNum;
                int spanStart = entityTokens.get(0).get(CoreAnnotations.TokenEndAnnotation.class).intValue() - tokenIndexDecrement - 1; //subtract two token indices for every entity to accomodate for start/end tags
                int spanEnd = entityTokens.get(entityTokens.size() - 1).get(CoreAnnotations.TokenEndAnnotation.class).intValue() - tokenIndexDecrement;
                Span span = new Span(spanStart, spanEnd, type);
                NamedEntity namedEntity = new NamedEntity(entity, span, i, "Annotation");
                entities.add(namedEntity);
                tagTokenNum++;
            }
        }

        return entities;
    }

    public static String autoAnnotate(String document, List<NamedEntity> entities) {
        List<CoreMap> sentencesList = NLPTools.detectSentencesStanford(document);
        String[] sentences = sentencesList.stream().map(p -> p.toString()).toArray(String[]::new);
        if (!entities.isEmpty()) {
            Map<Integer, List<NamedEntity>> lineEntities = entities.stream()
                    .collect(Collectors.groupingBy(p -> p.getLine()));

            for (int s = 0; s < sentences.length; s++) {
                String sentence = sentences[s];
                if (lineEntities.containsKey(s)) {
                    sentences[s] = autoAnnotateSentence(sentence, lineEntities.get(s));
                }
            }
            document = String.join("\r\n", sentences);
            document = fixFormattingAfterAutoAnnotation(document);
        } else {
            document = String.join("\r\n", sentences);
        }
        return document;
    }

    public static String autoAnnotateSentence(String sentence, List<NamedEntity> lineEntities) {
        List<CoreLabel> tokens = NLPTools.detectTokensStanford(sentence);
        String[] tokensArr = tokens.stream().map(p -> p.toString()).toArray(String[]::new);
        for (NamedEntity namedEntity : lineEntities) {
            namedEntity.autoAnnotate(tokensArr);
        }
        sentence = String.join(" ", tokensArr);
        return sentence;
    }

    public static void calculatePercentAnnotated(SolrDocument doc) {
        if (doc.containsKey("annotated")) {
            String annotated = doc.get("annotated").toString();
            List<CoreMap> sentences = detectSentencesStanford(annotated);
            int total = sentences.size();
            long annotatedLines = sentences.stream().filter(p -> p.toString().contains("<END>")).count();
            long percentAnnotated = (long)(((double)annotatedLines / (double)total) * 100);
            doc.put("percentAnnotated", percentAnnotated);
            doc.put("totalLines", total);
        }
    }

    public static String fixFormattingAfterAutoAnnotation(String text) {
        //remove random spaces that are an artifact of the tokenization process
        text = text.replaceAll("(\\b (?=,)|(?<=\\.) (?=,)|\\b (?=\\.)|(?<=,) (?=\\.)|\\b (?=')|(?<=<END>) (?= ')|(?<=<END>) (?= ,)|(?<=<END>) (?= \\.))", "");

        return text;
    }

    public static String fixFormattingForModelTraining(String text) {
        text = text.replaceAll(" {2,}", " "); //ensure there are no multi-spaces that could disrupt model training
        //remove random spaces that are an artifact of the tokenization process
        text = fixFormattingAfterAutoAnnotation(text);

        return text;
    }

    public static List<String> removeProbabilitiesFromCategories(List<String> categories) {
        List<String> categoriesNoProb = new ArrayList<>();
        for (String category : categories) {
            if (category.contains(" ")) {
                String[] catProb = category.split(" ");
                category = catProb[0];
            }
            categoriesNoProb.add(category);
        }
        return categoriesNoProb;
    }

    public static List<CategoryWeight> separateProbabilitiesFromCategories(List<String> categories) {
        List<CategoryWeight> categoriesWithProb = new ArrayList<>();
        for (String category : categories) {
            CategoryWeight catWeight = new CategoryWeight();
            if (category.contains(" ")) {
                String[] catProb = category.split(" ");
                catWeight.category = catProb[0];
                catWeight.catWeight = Double.parseDouble(catProb[1]);
                categoriesWithProb.add(catWeight);
            } else {
                catWeight.category = category;
                catWeight.catWeight = 1.0;
            }
        }
        return categoriesWithProb;
    }

    public static List<String> resolveCategoriesBetweenLDAandDoccat(List<String> doccatCategories, List<String> ldaCategories) {
        List<CategoryWeight> ldaCats = separateProbabilitiesFromCategories(ldaCategories);
        List<CategoryWeight> doccatCats = separateProbabilitiesFromCategories(doccatCategories);
        List<String> ldaCatNames = ldaCats.stream().map(p -> p.category).collect(Collectors.toList());
        List<String> doccatCatNames = doccatCats.stream().map(p -> p.category).collect(Collectors.toList());

        List<String> finalizedCategories;
        if (doccatCatNames.contains("Not_Applicable") && !ldaCatNames.contains("Not_Applicable")) {
            finalizedCategories = ldaCats.stream()
                    .filter(p -> p.catWeight >= MIN_LDA_PROB)
                    .map(p -> p.category)
                    .collect(Collectors.toList());
        } else if (!doccatCatNames.contains("Not_Applicable") && ldaCatNames.contains("Not_Applicable")) {
            finalizedCategories = doccatCats.stream()
                    .filter(p -> p.catWeight >= MIN_DOCCAT_PROB)
                    .map(p -> p.category)
                    .collect(Collectors.toList());
        } else {
            //merge LDA and Doccat together
            finalizedCategories = Stream.concat(ldaCats.stream(), doccatCats.stream())
                    .map(p -> p.category)
                    .distinct()
                    .collect(Collectors.toList());
        }

        if (finalizedCategories.size() == 0) {
            //prefer doccat in case all categories were eliminated during previous steps
            finalizedCategories = doccatCats.stream().map(p -> p.category).collect(Collectors.toList());
        }

        return finalizedCategories;
    }

    public static String deepCleanText(String document) {
        String document1 = document.replace("\r\n", " ");
        String document2 = document1.replace("(", " ");
        String document3 = document2.replace(")", " ");
        String document4 = document3.replaceAll("\\P{Print}", " ");
        //String document4a = Tools.removeAllNumbers(document4);
        //document = Tools.removeSpecialCharacters(document);
        String document5 = document4.replaceAll("[\\\\%-*/:-?{-~!\"^_`\\[\\]+]", " ");
        String document6= document5.replaceAll(" +\\.", ".");
        String document7 = document6.replaceAll("\\.{2,}", ". ");
        String document8 = document7.replaceAll(" {2,}", " ");
        String document9 = fixDocumentWordBreaks(document8);
        String document10 = document9.replaceAll("(?<=[a-z])-\\s(?=[a-z])", "");
        String document11 = document10.replaceAll("\\b\\ss\\s\\b", "'s ");

        return document11;
    }

    private static StanfordCoreNLP tokenPipeline;
    private static StanfordCoreNLP getTokenPipeline() {
        if (tokenPipeline == null) {
            Properties props = new Properties();
            props.setProperty("annotators", "tokenize");
            tokenPipeline = new StanfordCoreNLP(props);
        }
        return tokenPipeline;
    }

    public static List<CoreLabel> detectTokensStanford(String input) {
        Annotation processed = getTokenPipeline().process(input);
        List<CoreLabel> tokens = processed.get(CoreAnnotations.TokensAnnotation.class);
        return tokens;
    }

    private static StanfordCoreNLP sentencePipeline;
    private static StanfordCoreNLP getSentencePipeline() {
        if (sentencePipeline == null) {
            Properties props = new Properties();
            props.setProperty("annotators", "tokenize,ssplit");
            sentencePipeline = new StanfordCoreNLP(props);
        }
        return sentencePipeline;
    }

    public static List<CoreMap> detectSentencesStanford(String input) {
        Annotation processed = getSentencePipeline().process(input);
        List<CoreMap> sentences = processed.get(CoreAnnotations.SentencesAnnotation.class);
        return sentences;
    }

    private static StanfordCoreNLP posPipeline;
    private static StanfordCoreNLP getPOSPipeline() {
        if (posPipeline == null) {
            Properties props = new Properties();
            props.setProperty("annotators", "tokenize,ssplit,pos");
            posPipeline = new StanfordCoreNLP(props);
        }
        return posPipeline;
    }

    public static List<CoreMap> detectPOSStanford(String input) {
        Annotation processed = getPOSPipeline().process(input);
        List<CoreMap> sentences = processed.get(CoreAnnotations.SentencesAnnotation.class);
        return sentences;
    }

    private static StanfordCoreNLP nerPipeline;
    private static StanfordCoreNLP getNERPipeline() {
        if (nerPipeline == null) {
            Properties props = new Properties();
            props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
            nerPipeline = new StanfordCoreNLP(props);
        }
        return nerPipeline;
    }

    public static List<CoreMap> detectNERStanford(String input) {
        Annotation processed = getNERPipeline().process(input);
        List<CoreMap> sentences = processed.get(CoreAnnotations.SentencesAnnotation.class);
        return sentences;
    }

    public static StanfordCoreNLP getStanfordOpenIEPipeline() {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse,natlog,openie");
        props.setProperty("threads", "4");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        return pipeline;
    }

    public static List<CoreMap> runStanfordPipeline(String input, StanfordCoreNLP pipeline) {
        Annotation processed = pipeline.process(input);
        List<CoreMap> sentences = processed.get(CoreAnnotations.SentencesAnnotation.class);
        return sentences;
    }

    public static double similarity(String s1, String s2) {
        if (s1 == null && s2 == null) {
            return 1.0;
        } else if ((s1 != null && s2 == null) || (s1 == null && s2 != null)) {
            return 0.0;
        } else {
            String longer = s1, shorter = s2;
            if (s1.length() < s2.length()) { // longer should always have greater length
                longer = s2; shorter = s1;
            }
            int longerLength = longer.length();
            if (longerLength == 0) { return 1.0; /* both strings are zero length */ }
            LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
            return (longerLength - levenshteinDistance.apply(longer, shorter)) / (double) longerLength;
        }
    }

    public static String normalizeText(String text) {
        try {
            text = text.replace("\r", " ").replace("\n", " ");
            String reduced = Tools.removeSpecialCharacters(Tools.removeAllNumbers(text.toString()));
            reduced = reduced.replace(".", " ").replace("'", " ");
            StringBuilder str = new StringBuilder();
            List<CoreLabel> tokens = detectTokensStanford(reduced);
            for (CoreLabel token : tokens) {
                String word = token.word().toLowerCase();
                if (!stopwords.contains(word) && word.length() > 1) {
                    try {
                        mutex.lock();
                        str.append(stemmer.stem(word) + " ");
                    } finally {
                        mutex.unlock();
                    }
                }
            }
            String normalized = Tools.removeSpecialCharacters(Tools.removeAllNumbers(str.toString()));
            return normalized;
        } catch (Exception e) {
            return null;
        }
    }

    public static String redactTextForNLP(List<CoreMap> sentenceAnnotations, double threshold, int maxLength) {
        List<String> sentences = sentenceAnnotations.stream().map(p -> p.toString()).collect(Collectors.toList());
        int sentTotal = sentences.size();
        for (int i = 0; i < sentTotal; i++) {
            String sentence = sentences.get(i);
            if (sentence.length() <= maxLength) {
                List<CoreLabel> tokens = sentenceAnnotations.get(i).get(CoreAnnotations.TokensAnnotation.class);
                int numTokens = tokens.size();
                int numNounTokens = tokens.stream()
                        .filter(p -> p.tag().contains("NN") || p.tag().contains("CD") || p.tag().contains("LS") || p.tag().contains("."))
                        .collect(Collectors.toList())
                        .size();
                double percentNouns = (double) numNounTokens / (double) numTokens;
                if (percentNouns > threshold) {
                    sentences.set(i, "Redacted.");
                } else {
                    //check for long strings of numbers or list items
                    List<CoreLabel> numberTokens = tokens.stream().filter(p -> p.tag().contains("CD") || p.tag().contains("LS"))
                            .collect(Collectors.toList());

                    Map<Integer, List<CoreLabel>> contiguousTokens = new HashMap<>();

                    int partition = 0;
                    for (int n = 0; n < numberTokens.size() - 1; n++) {
                        if (!contiguousTokens.containsKey(partition)) {
                            contiguousTokens.put(partition, new ArrayList<>());
                        }
                        CoreLabel current = numberTokens.get(n);
                        CoreLabel next = numberTokens.get(n + 1);

                        if (next.index() - current.index() == 1) {
                            if (!contiguousTokens.get(partition).contains(current)) {
                                contiguousTokens.get(partition).add(current);
                            }
                            contiguousTokens.get(partition).add(next);
                        } else {
                            partition++;
                        }
                    }

                    for (int part : contiguousTokens.keySet()) {
                        String contiguousNumbers = contiguousTokens.get(part).stream().map(p -> p.originalText() + p.after()).reduce((c, n) -> c + n).orElse("");
                        sentence = sentence.replace(contiguousNumbers, "");
                    }

                    //check for long sequences of connected capitalized/uncapitalized letters
                    //if a large portion of the sentence is occupied by such sequences then the sentence can be dropped
                    String testSentence = sentence.replaceAll("(?<= )(\\w+[a-z][A-Z]\\w+)(?= )", ""); //regex for finding non-separated capitalized words
                    int testLength = testSentence.length();
                    int sentLength = sentence.length();
                    double proportion = (double)(sentLength - testLength) / (double)sentLength;

                    if (proportion < 0.3) {
                        sentences.set(i, sentence);
                    } else {
                        sentences.set(i, "Redacted.");
                    }
                }
            } else {
                sentences.set(i, "Redacted.");
            }
        }
        String text = StringUtils.join(sentences, "\r\n");

        text = text.replace("Redacted.\r\n", "");
        text = text.replace("Redacted.", "");

        return text;
    }

    public static void main(String[] args) {
//        //tagText("The North Milwaukee Power Plant is a coal-fired steam-turbine based generator with a maximum operating capacity of 250 MW.");
//
        String text = "Water Conservation Plans for Industrial or Mining Use.\n" +
                "a A water conservation plan for industrial or mining uses of water must provide information in response to each of the following elements.\n" +
                "If the plan does not provide information for each requirement, the industrial or mining water user shall include in the plan an explanation of why the requirement is not applicable.\n" +
                "1 a description of the use of the water in the production process, including how the water is diverted and transported from the source's of supply, how the water is utilized in the production process, and the estimated quantity of water consumed in the production process and therefore unavailable for reuse, discharge, or other means of disposal 2 specific, quantified five-year and ten-year targets for water savings and the basis for the development of such goals.\n" +
                "The goals established by industrial or mining water users under this paragraph are not enforceable 3 a description of the device's and or methods within an accuracy of plus or minus 5.0 to be used in order to measure and account for the amount of water diverted from the source of supply 4 leak-detection, repair, and accounting for water loss in the water distribution system 5 application of state-of-the-art equipment and or process modifications to improve water use efficiency and 6 any other water conservation practice, method, or technique which the user shows to be appropriate for achieving the stated goal or goals of the water conservation plan.\n" +
                "b An industrial or mining water user shall review and update its water conservation plan, as appropriate, based on an assessment of previous five-year and tenyear targets and any other new or updated information.\n" +
                "The industrial or mining water user shall review and update the next revision of its water conservation plan every five years to coincide with the regional water planning group.\n" +
                "Adopted November 14, 2012 Effective December 6, 2012 288.4.\n" +
                "Redacted.\n" +
                "Texas Commission on Environmental Quality Page 9 Chapter 288 - Water Conservation Plans, Drought Contingency Plans, Guidelines and Requirements a A water conservation plan for agricultural use of water must provide information in response to the following subsections.\n" +
                "If the plan does not provide information for each requirement, the agricultural water user must include in the plan an explanation of why the requirement is not applicable.\n" +
                "1 For an individual agricultural user other than irrigation A a description of the use of the water in the production process, including how the water is diverted and transported from the source's of supply, how the water is utilized in the production process, and the estimated quantity of water consumed in the production process and therefore unavailable for reuse, discharge, or other means of disposal B specific, quantified five-year and ten-year targets for water savings and the basis for the development of such goals.\n" +
                "The goals established by agricultural water users under this subparagraph are not enforceable C a description of the device's and or methods within an accuracy of plus or minus 5.0 to be used in order to measure and account for the amount of water diverted from the source of supply D leak-detection, repair, and accounting for water loss in the water distribution system E application of state-of-the-art equipment and or process modifications to improve water use efficiency and F any other water conservation practice, method, or technique which the user shows to be appropriate for achieving the stated goal or goals of the water conservation plan.\n" +
                "2 For an individual irrigation user A a description of the irrigation production process which shall include, but is not limited to, the type of crops and acreage of each crop to be irrigated, monthly irrigation diversions, any seasonal or annual crop rotation, and soil types of the land to be irrigated B a description of the irrigation method, or system, and equipment including pumps, flow rates, plans, and or sketches of the system layout Texas Commission on Environmental Quality Page 10 Chapter 288 - Water Conservation Plans, Drought Contingency Plans, Guidelines and Requirements C a description of the device's and or methods, within an accuracy of plus or minus 5.0 , to be used in order to measure and account for the amount of water diverted from the source of supply D specific, quantified five-year and ten-year targets for water savings including, where appropriate, quantitative goals for irrigation water use efficiency and a pollution abatement and prevention plan.\n" +
                "The goals established by an individual irrigation water user under this subparagraph are not enforceable E water-conserving irrigation equipment and application system or method including, but not limited to, surge irrigation, low pressure sprinkler, drip irrigation, and nonleaking pipe F leak-detection, repair, and water-loss control G scheduling the timing and or measuring the amount of water applied for example, soil moisture monitoring H land improvements for retaining or reducing runoff, and increasing the infiltration of rain and irrigation water including, but not limited to, land leveling, furrow diking, terracing, and weed control I tailwater recovery and reuse and J any other water conservation practice, method, or technique which the user shows to be appropriate for preventing waste and achieving conservation.\n" +
                "3 For a system providing agricultural water to more than one user A a system inventory for the supplier's i structural facilities including the supplier's water storage, conveyance, and delivery structures ii management practices, including the supplier's operating rules and regulations, water pricing policy, and a description of practices and or devices used to account for water deliveries and iii a user profile including square miles of the service area, the number of customers taking delivery of water by the system, the types of crops, the Texas Commission on Environmental Quality Page 11 Chapter 288 - Water Conservation Plans, Drought Contingency Plans, Guidelines and Requirements types of irrigation systems, the types of drainage systems, and total acreage under irrigation, both historical and projected B specific, quantified five-year and ten-year targets for water savings including maximum allowable losses for the storage and distribution system.\n" +
                "The goals established by a system providing agricultural water to more than one user under this subparagraph are not enforceable C a description of the practice's and or device's which will be utilized to measure and account for the amount of water diverted from the source's of supply D a monitoring and record management program of water deliveries, sales, and losses E a leak-detection, repair, and water loss control program F a program to assist customers in the development of on-farm water conservation and pollution prevention plans and or measures G a requirement in every wholesale water supply contract entered into or renewed after official adoption of the plan by either ordinance, resolution, or tariff , and including any contract extension, that each successive wholesale customer develop and implement a water conservation plan or water conservation measures using the applicable elements in this chapter.\n" +
                "If the customer intends to resell the water, the contract between the initial supplier and customer must provide that the contract for the resale of the water must have water conservation requirements so that each successive customer in the resale of the water will be required to implement water conservation measures in accordance with applicable provisions of this chapter H official adoption of the water conservation plan and goals, by ordinance, rule, resolution, or tariff, indicating that the plan reflects official policy of the supplier I any other water conservation practice, method, or technique which the supplier shows to be appropriate for achieving conservation and J documentation of coordination with the regional water planning groups, in order to ensure consistency with appropriate approved regional water plans.\n" +
                "b A water conservation plan prepared in accordance with the rules of the United States Department of Agriculture Natural Resource Conservation Service, the Texas Texas Commission on Environmental Quality Page 12 Chapter 288 - Water Conservation Plans, Drought Contingency Plans, Guidelines and Requirements State Soil and Water Conservation Board, or other federal or state agency and substantially meeting the requirements of this section and other applicable commission rules may be submitted to meet application requirements in accordance with a memorandum of understanding between the commission and that agency.\n" +
                "c An agricultural water user shall review and update its water conservation plan, as appropriate, based on an assessment of previous five-year and ten-year targets and any other new or updated information.\n" +
                "An agricultural water user shall review and update the next revision of its water conservation plan every five years to coincide with the regional water planning group.\n" +
                "Adopted November 14, 2012 Effective December 6, 2012 288.5.\n" +
                "Water Conservation Plans for Wholesale Water Suppliers.\n" +
                "A water conservation plan for a wholesale water supplier must provide information in response to each of the following paragraphs.\n" +
                "If the plan does not provide information for each requirement, the wholesale water supplier shall include in the plan an explanation of why the requirement is not applicable.\n" +
                "Redacted.\n" +
                "All water conservation plans for wholesale water suppliers must include the following elements A a description of the wholesaler's service area, including population and customer data, water use data, water supply system data, and wastewater data B specific, quantified five-year and ten-year targets for water savings including, where appropriate, target goals for municipal use in gallons per capita per day for the wholesaler's service area, maximum acceptable water loss, and the basis for the development of these goals.\n" +
                "The goals established by wholesale water suppliers under this subparagraph are not enforceable C a description as to which practice's and or device swill be utilized to measure and account for the amount of water diverted from the source's of supply D a monitoring and record management program for determining water deliveries, sales, and losses E a program of metering and leak detection and repair for the wholesaler's water storage, delivery, and distribution system Texas Commission on Environmental Quality Page 13 Chapter 288 - Water Conservation Plans, Drought Contingency Plans, Guidelines and Requirements F a requirement in every water supply contract entered into or renewed after official adoption of the water conservation plan, and including any contract extension, that each successive wholesale customer develop and implement a water conservation plan or water conservation measures using the applicable elements of this chapter.\n" +
                "If the customer intends to resell the water, then the contract between the initial supplier and customer must provide that the contract for the resale of the water must have water conservation requirements so that each successive customer in the resale of the water will be required to implement water conservation measures in accordance with applicable provisions of this chapter G a reservoir systems operations plan, if applicable, providing for the coordinated operation of reservoirs owned by the applicant within a common watershed or river basin.\n" +
                "The reservoir systems operations plans shall include optimization of water supplies as one of the significant goals of the plan H a means for implementation and enforcement, which shall be evidenced by a copy of the ordinance, rule, resolution, or tariff, indicating official adoption of the water conservation plan by the water supplier and a description of the authority by which the water supplier will implement and enforce the conservation plan and I documentation of coordination with the regional water planning groups for the service area of the wholesale water supplier in order to ensure consistency with the appropriate approved regional water plans.\n" +
                "2 Additional conservation strategies.\n" +
                "Any combination of the following strategies shall be selected by the water wholesaler, in addition to the minimum requirements of paragraph 1 of this section, if they are necessary in order to achieve the stated water conservation goals of the plan.\n" +
                "The commission may require by commission order that any of the following strategies be implemented by the water supplier if the commission determines that the strategies are necessary in order for the conservation plan to be achieved A conservation-oriented water rates and water rate structures such as uniform or increasing block rate schedules, and or seasonal rates, but not flat rate or decreasing block rates B a program to assist agricultural customers in the development of conservation pollution prevention and abatement plans C a program for reuse and or recycling of wastewater and or graywater and Texas Commission on Environmental Quality Page 14 Chapter 288 - Water Conservation Plans, Drought Contingency Plans, Guidelines and Requirements D any other water conservation practice, method, or technique which the wholesaler shows to be appropriate for achieving the stated goal or goals of the water conservation plan.\n" +
                "3 Review and update requirements.\n" +
                "The wholesale water supplier shall review and update its water conservation plan, as appropriate, based on an assessment of previous five-year and ten-year targets and any other new or updated information.\n" +
                "A wholesale water supplier shall review and update the next revision of its water conservation plan every five years to coincide with the regional water planning group.\n" +
                "Adopted November 14, 2012 Effective December 6, 2012 288.6.\n" +
                "Water Conservation Plans for Any Other Purpose or Use.\n" +
                "A water conservation plan for any other purpose or use not covered in this subchapter shall provide information where applicable about those practices, techniques, and technologies that will be used to reduce the consumption of water, prevent or reduce the loss or waste of water, maintain or improve the efficiency in the use of water, increase the recycling and reuse of water, or prevent the pollution of water.\n" +
                "Adopted April 5, 2000 Effective April 27, 2000 288.7.\n" +
                "Plans Submitted With a Water Right Application for New or Additional State Water.\n" +
                "a A water conservation plan submitted with an application for a new or additional appropriation of water must include data and information which 1 supports the applicant's proposed use of water with consideration of the water conservation goals of the water conservation plan 2 evaluates conservation as an alternative to the proposed appropriation and 3 evaluates any other feasible alternative to new water development including, but not limited to, waste prevention, recycling and reuse, water transfer and marketing, regionalization, and optimum water management practices and procedures.\n" +
                "b It shall be the burden of proof of the applicant to demonstrate that no feasible alternative to the proposed appropriation exists and that the requested amount of appropriation is necessary and reasonable for the proposed use.\n" +
                "Redacted.\n" +
                "Redacted.\n" +
                "Dallas.\n" +
                "Redacted.\n" +
                "Redacted.\n" +
                "09 2013 Fiscal Period Begin mm yyyy Period End mm yyyy J Calendar Period Begin mm yyyy Period End mm yyyy Check all of the following that apply to your entity LI Receive financial assistance of $500,000 or more from TWDB Have 3,300 or more retail connections RI Have a water right with TCEQ Page 1 of 9 4 Retail Customer Categories Residential Single Family Residential Multi-family Industrial Commercial Institutional.\n" +
                "Agricultural Recommended Customer Categories for classifying your customerwateruse.\n" +
                "Fordefinitions, refer to d c i M ry Cpn.\n" +
                "7 it 1.\n" +
                "For this reporting period, select the 171 Residential Single Family 171 Residential Multi-family Industrial cate ory's used to classify customer water use I Commercial E1 tien-a - RI gruai 2.\n" +
                "For this reporting period, enter the gallons of metered retail water used by each customer category.\n" +
                "If the Customer Category does not apply, enter zero or leave blank, Residential Single Family - Residential Multi-family Industrial Commercial.\n" +
                "Redacted.\n" +
                "Residential lndustnaL - Commercial Institutional Agricultural Total Retail Water Metered Page 2 of 9 Total Gallons During the Reporting Period Water Produced Water from permitted sources such as rivers, lakes, streams, and wells, Same as line 14 of the 144,321,717,172 water loss audit.\n" +
                "Wholesale Water Imported Purchased wholesale water transferred into the system.\n" +
                "Same as line 15 of the water o loss audit.\n" +
                "Wholesale Water Exported Wholesale water sold or transferred out of the system.\n" +
                "Same as line l6of the 0 water loss audit.\n" +
                "- System Input Total water supplied to system and 144 321 717.172available for retail use.\n" +
                "Produced Imported Exported System Input Total Retail Water Metered 122,768,307,000 Other Authorized Consumption Water that is authorized for other uses such as the following This water may be metered or unmetered.\n" +
                "Same as the total of lines 19, 20, and 21 of the water loss audit.\n" +
                "- back flushing - line flushing I ,.\n" +
                "JLR.J,.\n" +
                "JtL1,JJV - storage tank cleaning - municipal golf courses parks - fire department use - municipal government offices Total Authorized Use All water that has been authorized for use 130,268,847,000 Total Retail Water Other Authorized consumption Total Authorized Use Apparent Losses Water that has been consumed but not properly measured or billed.\n" +
                "Same as line 28 of the water loss audit.\n" +
                "485,103,343 Includes losses due to customer meter accuracy, systematic data discrepancy, unauthorized consumption such as theft Real Losses Physical losses from the distribution system prior to reaching the customer destination.\n" +
                "Same as line 29 of the water loss audit.\n" +
                "873,100.000 Includes phycical losses from system or mains, reported breaks and leaks, or storage overflow Unidentified Water Losses Unreported losses not known 12,694 666 829or quantified.\n" +
                "System Input Total Authorized Use Apparent Losses Real Losses Unidentified Water Losses 14,052,870,172 Total Water Loss Apparent Real Unidcnt5ied Total Water Loss Page 3 of 9 Targets and Goals Provide the specific and quantified five and ten-year targets as listed in your current Water Conservation Plan.\n" +
                "Target dates and numbers should match your current Water Conservation Plan.\n" +
                "1 f Target for Target for Achieve Date T Water l.oss Water Loss Percentagea a expressed in GPCD expressed in percentage Five-year targetdate 196 28 10 2019 Ten-year targetdate 195 27 10 2024 Gallons Per Capita er Day GPCD and Water Loss Provide current GPCD and water loss totals.\n" +
                "To see if you are making progress towards your stated goals, compare these totals to the above targets and goals.\n" +
                "Provide the population and residential water use of your service area.\n" +
                "PermanentTotal System Input in Gallons.\n" +
                "i Total GPCDPopulation 144,321,717,172 2,427,010 163 Water Produced Wholesale Imported - Wholesale Exported System Input Permanent Population - 365 1.\n" +
                "Permanent Population is the total permanent population of the service area, including single Family, multi-family, and group quarter populations.\n" +
                "Residential Use in Gallons Residential 1 Residential GPCD Single Family Multi-family Population 98 43,311 542000 1,213,600 Residential Use - Residential Population 365 1 Residential Population is the total residential population of the service area, including only single family and multifamily populations.\n" +
                "Permanent Water Loss Total Water Loss Population GPCD Percent2 14,052,870,172 2,427,010 16 10 Apparent Real Unidentified Total Water Loss 1 Total Water Loss Permanent Population 365 Water Loss GPCD 2 Total Water Loss Total System Input x 100 Water Loss Percentage Page 4 of 9 rgrjm cz t's As you complete this section, review your utility's water conservation plan to see if you are making progress towards meeting your stated goals.\n" +
                "2014 1.\n" +
                "What year did your entity adopt or revise the most recent Water Conservation Plan 2.\n" +
                "Does The Plan incorporate Benerr nt Prt Yes No 3.\n" +
                "Using the table below select the types of Best Management Practices or water conservation strategies actively administered during this reporting period and estimate the savings incurred in implementing water conservation activities and programs.\n" +
                "Leave fields blank if unknown.\n" +
                "Redacted.\n" +
                "Regulatory and Enforcement Prohibition on Wasting Water Other, please describe 31.929.810.000 Total Gallons of Water Saved - 32,000,456,000 4.\n" +
                "For this reporting period, provide the estimated gallons of direct or indirect reuse activities.\n" +
                "Reuse Activity Estimated Volume in gallons On-site irri ation.\n" +
                "Redacted.\n" +
                "I. Industrial.\n" +
                "Redacted.\n" +
                "55,562,000 A ricultural.\n" +
                "Other, please describe Nonpotable water uses for 4 200 000 000 on-site irrigation, plant Total Volume of Reuse 4,255,562,000 5.\n" +
                "For this reporting period, estimate the savings from water conservation activities and programs.\n" +
                "Gallons Gallons Total Volume of Dollar Value Saved Conserved Recycled Reused Water Saved1 of Water Saved2 32,000,456,000 4,255,562,000 36,256,018,000 $ 25,125,408 1, Estimated Gallons Saved Conserved Estimated Gallons Recycled Reused Total Volume Saved 2 Estimate this value by taking into account water savings, the Cost of treatment or purchase of water and deferred capital costs due to conservation, Page 6 of 9 6.\n" +
                "During this reporting period, did your rates or rate structure change Yes Select the type of rate pricing structures used.\n" +
                "Check all that apply.\n" +
                "QN0 Uniform Rates Flat Rates ZI Inclining Inverted Block Rates Li L Declining Block Rates U Seasonal Rates Water Budget Based Rates Excess Use Rates Drought Demand Rates Tailored Rates Surcharge - usage demand Surcharge - seasonal Surcharge - drought Other, please describe 7, For this reporting period, select the public awareness or educational activities used.\n" +
                "Redacted.\n" +
                "During this reporting period, how many leaks were repaired in the system or at service connections 503 Select the main cause's of water loss in your system.\n" +
                "Leaks and breaks Unmetered utility or city uses Master meter problems Customer meter problems Record and data problems Other Other 2.\n" +
                "For this reporting period, provide the following information regarding meter repair Type of Meter Total Number Total Tested Total Repaired Total Replaced Production 310 018 5 556 5,556 20,075Meters 30,594 1,314 1,314 2,595 MeterslY2or 279,424 4,242 4,242 23,472smaller 3.\n" +
                "Does your system have automated meter reading Yes No Page 8 of 9 1.\n" +
                "in your opinion, how would you rank the effectiveness of your conservation activities Residential Customers Industrial Customers 0 Institutional Customers 0 Commercial Customers Agricultural Customers 2.\n" +
                "During the reporting period, did you implement your Drought Contingency Plan Q Yes No If yes, how many days were water use restrictions in effect If es, check the reason's for implementing your Drought Contingency Plan.\n" +
                "Redacted.\n" +
                "Select the areas for which you would like to receive more technical assistance Best Management Practices Drought Contingency Plans Landscape Irrigation Leak Detection and Equipment Rainwater Harvesting Rate Structures SUBMIT Educational Resources Water Conservation Annual Reports Water Conservation Plans Water 10 Know Your Water Water Loss Audits Recycling and Reuse Customer Classification Less Than Somewhat Highly Effective Effective Effective 0 0 0 a 0 0 Does Not Apply 0 0 0 0 0 Page 9 of 9 Water Conservation Plan Annual Report Wholesale Water Supplier Name of Entity City of Dallas Water Utilities 0570004 Public Water Supply Identification Number PWS ID P0001 CCN Number 12468 etc..\n" +
                "Redacted.\n" +
                "Dallas.\n" +
                "Redacted.\n" +
                "Water Conservation Form Completed By Title 3.11.14 Date orting Period check only one 10 2012 09 2013 C Fiscal Period Begin mm yyyy Period End mm yyyy Calendar Period Begin mm yyyy Period End mm yyyy Check all that apply Received financial assistance of $500,000 or more from TWDB Have 3,300 or more retail connections Have a surface water right with TCEQ Page lof 5 1.\n" +
                "For this reporting period, provide the total volume of wholesale water exported transferred or sold 55741239OOO gallons 2.\n" +
                "For this reporting period, does your billing accounting system have the capability to classify customers into the Wholesale Customer Categories 0 Yes No 3, For this reporting period, select the category's used to calculate wholesale customer water usage Municipal Industrial Commercial Institutional Agricultural 4.\n" +
                "For this reporting year, enter the gallons of WHOLESALE water exported transferred or sold.\n" +
                "Enter zero if a Customer Category does not apply.\n" +
                "Gallons Exported Number of Wholesale Customer Category transferred or sold Customers Municipal 55,741239,OOO Industrial 0 Commercial 0 Institutional 0 Agricultural 0 Total 55741,239,OOO 0 Wholesale Customer Cateaories Municipal Industrial Commercial Institutional Agricultural Recon mended Cud mer C feg r m Fur assy rg c fr e waMr ve.\n" +
                "deb s, refer c 1 Page 2 of 5 Total Gallons During the Reporting Period Water Produced Water from permitted sources such as rivers, lakes, streams, and wells.\n" +
                "142,878,500,000 Wholesale Water Imported Purchased wholesale water transferred into the system.\n" +
                "0 System input Total water supplied to system and available 142,878,500,000 for use.\n" +
                "Produced Imported System Input Wholesale Water Exported Wholesale water sold or transferred out of the system.\n" +
                "55,741,239,000 152,715,7231 Wholesale Water Exported 365 Gallons Per Day Population Estimated total population for municipal customers.\n" +
                "1,213,410 Municipal Gallons Per Capita Per Day 126 Municipal Exported Municipal Population 365 Municipal Gallons Per Capita Per Day Provide the specific and quantified five and ten-year targets as listed in your most current Water Conservation Plan.\n" +
                "Date to Achieve Specified and Quantified Targets Target Five-yeartarget 2019 196 Ten-year target 2024 195 Page 3of5 1.\n" +
                "Water Conservation Plan What year did your entity adopt or revise their most recent Water Conservation Plan 2014 Does The Plan incorporate BetM pntPractic ctice Yes No 2.\n" +
                "Water Conservation Programs Has our entity implemented any type of water conservation activity or program Yes No If yes, select the type's of Best Management Practices or water conservation strategies implemented during this reporting period.\n" +
                "Redacted.\n" +
                "3.\n" +
                "Recycle Reuse Water or Wastewater Effluent For this reporting period, provide direct and indirect reuse activities.\n" +
                "Reuse Activity I Estimated Volume in gallons On-site irrigation Plant washdown Chlorination de-chlorination Industrial Landscape irrgation park golf courses 55562000 Agricultural OtherL please describe Non-potable water uses for on-site irr 4200000000 Estimated Volume of Reuse 4255,562O00 Page 4 of 5 4.\n" +
                "Water Savings For this reporting period, estimate the savings that resulted from water conservation activities and programs.\n" +
                "Redacted.\n" +
                "Program Effectiveness In your opinion, how would you rank the overall effectiveness of your conservation programs and activities Less Than Effective p Somewhat Effective Highly Effective Does Not Apply c 6.\n" +
                "What might your entity do to improve the effectiveness of your water conservation program 7.\n" +
                "Select the areas for which you would like to receive technical assistance Agricultural Best Management Practices Wholesale Best Management Practices Industrial Best Management Practices Drought Contingency Plans Landscape Efficient Systems Leak Detection and Equipment Educational Resources SUBMIT Water Conservation Plans Water lQ Know Your Water Water Loss Audits Rainwater Harvesting Systems Recycling and Reuse PageS of 5 TCEQ-20646 rev. 09-18-2013 Page 2 of 11 Please check all of the following that apply to your entity A surface water right holder of 1,000 acre-feet year or more for non-irrigation uses A surface water right holder of 10,000 acre-feet year or more for irrigation uses Important If your entity meets the following description, please skip page 3 and go directly to page 4.\n" +
                "Your entity is a Wholesale Public Water Supplier that ONLY provides wholesale water services for public consumption.\n" +
                "For example, you only provide wholesale water toother municipalities or water districts.\n" +
                "TCEQ-20646 rev. 09-18-2013 Page 3 of 11 Fields that are gray are entered by the user.\n" +
                "Select fields that are white and press F9 to updated fields.\n" +
                "Water Use Accounting Retail Water Sold All retail water sold for public use and human consumption.\n" +
                "Helpful Hints There are two options available for you to provide the requested information.\n" +
                "Both options ask the same information however, the level of detail and breakdown of information differs between the two options.\n" +
                "Please select just one option that works best for your entity and fill in the fields as completely as possible.\n" +
                "For the five-year reporting period, enter the gallons of RETAIL water sold in each major water use category.\n" +
                "Use only one of the following options.\n" +
                "Redacted.\n" +
                "Redacted.\n" +
                "Redacted.\n" +
                "Single Family Multi-Family 136,360,220,000 Commercial Please select all of the sectors that your account for as Commercial.\n" +
                "Commercial Multi-Family Industrial Institutional 174,853,715,000 Industrial Please select all of the sectors that your account for as Industrial.\n" +
                "Industrial Commercial Institutional 25,321,252,000 Other Please select all of the sectors that your account for as Other.\n" +
                "Commercial Multi-Family Industrial Institutional 287,066,794,000 TOTAL Retail Water Sold 1 Total Billed Volume 623,601,981,000.00 1.\n" +
                "Res Com Ind Other Retail Water Sold TCEQ-20646 rev. 09-18-2013 Page 4 of 11 Wholesale Water Exported Wholesale water sold or transferred out of the distribution system.\n" +
                "For the five-year reporting period, enter the gallons of WHOLESALE water exported to each major water use category.\n" +
                "1.\n" +
                "Mun Agr Ind Com Ins Wholesale Water Exported Water Use Category Gallons of Exported Wholesale Water Municipal Customers 0 Agricultural Customers Industrial Customers Commercial Customers Institutional Customers TOTAL Wholesale Water Exported 1 0.00 TCEQ-20646 rev. 09-18-2013 Page 5 of 11 System Data Total Gallons During the Five-Year Reporting Period Water Produced Volume produced from own sources 733,356,000000 Wholesale Water Imported Purchased wholesale water imported from other sources into the distribution system 0 Wholesale Water Exported Wholesale water sold or transferred out of the distribution system Insert Total Volume calculated on Page 4 0 TOTAL System Input Total water supplied to the infrastructure 733,356,000,000.00 Produced Imported Exported System Input Retail Water Sold All retail water sold for public use and human consumption Insert Total Residential Use from Option 1 or Option 2 calculated on Page 3 623,601,981,000 Other Consumption Authorized for Use but not Sold - back flushing water-line flushing - storage tank cleaning - golf courses - fire department use - parks - municipal government offices 46,231,500,000 TOTAL Authorized Water Use All water that has been authorized for use or consumption.\n" +
                "669,833,481,000.00 Retail Water Sold Other Consumption Total Authorized Apparent Losses Water that has been consumed but not properly measured Includes customer meter accuracy, systematic data discrepancy, unauthorized consumption such as theft 1,559,004,953 Real Losses Physical losses from the distribution system prior to reaching the customer destination Includes physical losses from system or mains, reported breaks and leaks, storage overflow 3,490,000,000 Unidentified Water Losses 58,473,514,047.00 System Input- Total Authorized - Apparent Losses - Real Losses Unidentified Water Losses TOTAL Water Loss 63,522,519,000.00 Apparent Real Unidentified Total Water Loss Fields that are gray are entered by the user.\n" +
                "Select fields that are white and hit F9 to updated fields.\n" +
                "TCEQ-20646 rev. 09-18-2013 Page 6 of 11 In the table below, please provide the specific and quantified five and ten-year targets for water savings listed in your water conservation plan.\n" +
                "Date Target for Total GPCD Target for Water Loss expressed in GPCD Target for Water Loss Percentage expressed in Percentage Five-year target date 9 30 2019 203 20 10 Ten-year target date 9 30 2024 195 20 10 Are targets in the water conservation plan being met Yes No If these targets are not being met, provide an explanation as to why, including any progress on these targets Click hereto enter text.\n" +
                "Gallons per Capita per Day GPCD and Water Loss Compare your current gpcd and water loss to the above targets and goals set in your previous water conservation plan.\n" +
                "Redacted.\n" +
                "This includes single family, multi-family, and group quarter populations.\n" +
                "Total Residential Use Permanent Population Residential GPCD 224,713,902,900 1,213,600 101.46 Residential Use Residential Population 5 365 Residential Population is the total residential population of the service area including single multi-family population.\n" +
                "Fields that are gray are entered by the user.\n" +
                "Select fields that are white and hit F9 to update fields.\n" +
                "TCEQ-20646 rev. 09-18-2013 Page 7 of 11 Fields that are gray are entered by the user.\n" +
                "Select fields that are white and hit F9 to updated fields.\n" +
                "Total Water Loss Total System Input in Gallons Permanent Population Water Loss calculated in GPCD 1 Percent 2 63,522,519,000 Apparent Real Unidentified Total Water Loss 623,601,981,000.00 Water Produced Wholesale Imported - Wholesale Exported 1,213,600 28.68 10 1.\n" +
                "Redacted.\n" +
                "Total Water Loss Total System Input x 100 Water Loss Percentage Water Conservation Programs and Activities As you complete this section, please review your water conservation plan to see if you are making progress towards meeting your stated goals.\n" +
                "1.\n" +
                "Water Conservation Plan What year did your entity adopt, or revise, their most recent water conservation plan 2010 Does the plan incorporate Best Management Practices Yes No 2.\n" +
                "Water Conservation Programs For the reporting period, please select the types of activities and programs that have been actively administered, and estimate the expense and savings that incurred in implementing the conservation activities and programs for the past five years.\n" +
                "Redacted.\n" +
                "Reuse Water or Wastewater Effluent For the reporting period, please provide the following data regarding the types of direct and indirect reuse activities that were administered for the past five years Reuse Activity Estimated Volume in gallons On-site irrigation Plant washdown Chlorination de-chlorination Industrial Landscape irrigation parks, golf courses 311,039,067 Agricultural Other, please describe Non-potable water uses for onsite irrigation, plant washdown and other processes.\n" +
                "18,510,000,000 TCEQ-20646 rev. 09-18-2013 Page 9 of 11 Estimated Volume of Recycled or Reuse 18,821,039,067 4.\n" +
                "Water Savings For the five-year reporting period, estimate the total savings that resulted from your overall water conservation activities and programs Estimated Gallons Saved Total from Conservation Programs Table Estimated Gallons Recycled or Reused Total from Reuse Table Total Volume of Water Saved 1 Dollar Value of Water Saved 2 116,862,000,000 18,821,039,067 135,683,039,067 $105,055,000 1.\n" +
                "Estimated Gallons Saved Estimated Gallons Recycled or Reused Total Volume Saved 2.\n" +
                "Estimate this value by taking into account water savings, the cost of treatment or purchase of your water, and any deferred capital costs due to conservation.\n" +
                "5.\n" +
                "Conservation Pricing Conservation Rate Structures During the five-year reporting period, have your rates or rate structure changed Yes No Please indicate the type of rate pricing structures that you use Uniform rates Water Budget Based rates Surcharge - seasonal Flat rates Excess Use Rates Surcharge - drought Inclining Inverted Block rates Drought Demand rates Surcharge - usage demand Declining Block rates Tailored rates Seasonal rates 6.\n" +
                "Public Awareness and Education Program For the five-year reporting period, please check the appropriate boxes regarding any public awareness and educational activities that your entity has provided Implemented Number Unit Example Brochures Distributed 10,000 year Example Educational School Programs 50 students month Brochures Distributed 319,351 Messages Provided on Utility Bills 15,948,000 Press Releases 24 TV Public Service Announcements 16,055 Radio Public Service Announcements 13,537 Educational School Programs 80,422 participants Displays, Exhibits, and Presentations 1,047 TCEQ-20646 rev. 09-18-2013 Page 10 of 11 Community Events 283 Social Media campaigns 14 Facility Tours 5 Other Print Advertisements 323 7.\n" +
                "Leak Detection During the five-year reporting period, how many leaks were repaired in the system or at service connections 66,106 Please check the appropriate boxes regarding the main cause of water loss in your system during the reporting period Leaks and breaks Un-metered utility or city uses Master meter problems Customer meter problems Record and data problems Other Other 8.\n" +
                "Universal Metering and Meter Repair For the five-year reporting period, please provide the following information regarding meter repair Total Number Total Tested Total Repaired Total inInIReplaced Production Meters 310,018 41,017 41,017 82,034 Meters larger than 1 30,594 7,872 7,872 15,744 Meters 1 or smaller 279,424 33,145 33,145 66,290 Does your system have automated meter reading Yes No TCEQ-20646 rev. 09-18-2013 Page 11 of 11 9.\n" +
                "Conservation Communication Effectiveness In your opinion, how would you rank the effectiveness of your conservation activities in reaching the following types of customers for the past five years 10.\n" +
                "Drought Contingency and Emergency Water Demand Management During the five-year reporting period, did you implement your Drought Contingency Plan Yes No If yes, indicate the number of days that your water use restrictions were in effect 133 If yes, please check all the appropriate reasons for your drought contingency efforts going into effect.\n" +
                "Water Supply Shortage Equipment Failure High Seasonal Demand Impaired Infrastructure Capacity Issues Other If you have any questions on how to fill out this form or about the Water Conservation program, please contact us at 512 239-4691.\n" +
                "Individuals are entitled to request and review their personal information that the agency gathers on its forms.\n" +
                "They may also have any errors in their information corrected.\n" +
                "To review such information, contact us at 512-239-3282.\n" +
                "Do not have activities or programs that target this type customer.\n" +
                "Less Than Effective Somewhat Effective Highly Effective Residential Customers Industrial Customers Institutional Customers Commercial Customers Agricultural Customers";

        List<CoreMap> sentences = detectPOSStanford(text);
        String redacted = redactTextForNLP(sentences, 0.8, 500);
        StanfordCoreNLP pipeline = getStanfordOpenIEPipeline();
        List<CoreMap> annotations = runStanfordPipeline(redacted, pipeline);
        System.out.println(redacted);
    }

    public static String fixDocumentWordBreaks(String text) {
        List<CoreMap> sentences = detectSentencesStanford(text);

        StringBuilder fixedDocument = new StringBuilder();
        for (CoreMap sentence : sentences) {
            List<TreeMap<Integer, String>> resolvedIndices = new ArrayList<>();
            List<CoreLabel> tokens = detectTokensStanford(sentence.toString());
            for (int i = 0; i < tokens.size(); i++) {
                CoreLabel token = tokens.get(i);
                String currentWord = token.word();
                TreeMap<Integer, String> currentMap = new TreeMap<>();
                currentMap.put(i, currentWord);
                if (!SpellChecker.check(currentWord.toLowerCase()) && i <= tokens.size() - 1) {
                    if (!Pattern.compile( "[0-9]" ).matcher(currentWord).find()) {

                        TreeMap<Integer, String> correctedMap = correctSpelling(tokens, i, i, new TreeMap<>(currentMap));
                        if (correctedMap != null) { //spelling correction was successful
                            resolvedIndices.add(correctedMap);
                            i = correctedMap.lastKey();
                        } else { //failed to correct spelling
                            resolvedIndices.add(currentMap);
                        }
                    } else {
                        resolvedIndices.add(currentMap);
                    }
                } else {
                    resolvedIndices.add(currentMap);
                }
            }

            int numMerges;
            do {
                numMerges = mergeWordParts(resolvedIndices);
            } while (numMerges > 0);

            StringBuilder fixedSentence = new StringBuilder();
            for (TreeMap<Integer, String> resolvedWord : resolvedIndices) {
                fixedSentence.append(StringUtils.join(resolvedWord.values(), ""));
                fixedSentence.append(tokens.get(resolvedWord.lastKey()).after());
            }

            fixedDocument.append(fixedSentence.toString());
            fixedDocument.append(System.lineSeparator());
        }

        return fixedDocument.toString();
    }

    public static int mergeWordParts(List<TreeMap<Integer, String>> resolvedIndices) {
        int numMerges = 0;
        for (int i = 0; i < resolvedIndices.size(); i++) {
            Map<Integer, String> resolvedWord = resolvedIndices.get(i);
            for (int j = i + 1; j < resolvedIndices.size(); j++) {
                Map<Integer, String> otherWord = resolvedIndices.get(j);
                if(!Collections.disjoint(resolvedWord.keySet(), otherWord.keySet())) {
                    resolvedWord.putAll(otherWord);
                    resolvedIndices.remove(j);
                    j--;
                    numMerges++;
                }
            }
        }
        return numMerges;
    }

    public static TreeMap<Integer, String> correctSpelling(List<CoreLabel> tokens, int center, int index, TreeMap<Integer, String> spellCorrection) {
        String prevToken = index > 0 ? tokens.get(index - 1).word() : null;
        String currToken = tokens.get(index).word();
        String nextToken = index < tokens.size() - 1 ? tokens.get(index + 1).word() : null;

        spellCorrection.put(index, currToken);

        boolean leftRecurseOK = false;
        if (prevToken != null && !Pattern.compile( "[0-9]" ).matcher(prevToken).find() && !spellCorrection.containsKey(index - 1)) {
            spellCorrection.put(index - 1, prevToken);
            leftRecurseOK = true;
        }

        boolean rightRecurseOK = false;
        if (nextToken != null && !Pattern.compile( "[0-9]" ).matcher(nextToken).find() && !spellCorrection.containsKey(index + 1)) {
            spellCorrection.put(index + 1, nextToken);
            rightRecurseOK = true;
        }

        TreeMap<Integer, String> left = new TreeMap(spellCorrection.headMap(center, true));
        TreeMap<Integer, String> right = new TreeMap(spellCorrection.tailMap(center, true));

        String correctedLeft = StringUtils.join(left.values(), "");
        String correctedRight = StringUtils.join(right.values(), "");
        String correctedAll = StringUtils.join(spellCorrection.values(), "");

        boolean leftPassed = SpellChecker.check(correctedLeft.toLowerCase());
        boolean rightPassed = SpellChecker.check(correctedRight.toLowerCase());
        boolean allPassed = SpellChecker.check(correctedAll.toLowerCase());

        if (rightPassed) {
            //always prefer forward direction
            return right;
        } else if (allPassed) {
            return spellCorrection;
        } else if (leftPassed) {
            //lowest priority is given to backward search
            return left;
        } else {
            if (correctedAll.length() > 30 || spellCorrection.size() == 15) {
                return null;
            } else {
                right = rightRecurseOK ? correctSpelling(tokens, center, index + 1, new TreeMap<>(spellCorrection)) : null;
                left = leftRecurseOK ? correctSpelling(tokens, center, index - 1, new TreeMap<>(spellCorrection)) : null;
                if (right != null) {
                    return right;
                } else {
                    return left;
                }
            }
        }
    }

//    public static String fixDocumentWordBreaks(String text) {
//        List<CoreMap> sentences = detectSentencesStanford(text);
//
//        StringBuilder fixedDocument = new StringBuilder();
//        for (CoreMap sentence : sentences) {
//            StringBuilder fixedSentence = new StringBuilder();
//            List<CoreLabel> tokens = detectTokensStanford(sentence.toString());
//            for (int i = 0; i < tokens.size(); i++) {
//                CoreLabel token = tokens.get(i);
//                if (!SpellChecker.check(token.toString().toLowerCase()) && i <= tokens.size() - 1) {
//                    String current = token.word();
//                    if (!Pattern.compile( "[0-9]" ).matcher(current).find()) {
//                        List<String> corrected = new ArrayList<>();
//                        corrected.add(token.word());
//                        if (correctSpelling(tokens.subList(i + 1, tokens.size()), corrected)) {
//                            List<CoreLabel> fixedTokens = tokens.subList(i, i + corrected.size());
//                            fixedSentence.append(StringUtils.join(fixedTokens, ""));
//                            fixedSentence.append(fixedTokens.get(fixedTokens.size() - 1).after());
//                            i += corrected.size() - 1;
//                        } else { //failed to correct spelling
//                            fixedSentence.append(token.word());
//                            fixedSentence.append(token.after());
//                        }
//                    } else {
//                        fixedSentence.append(token.word());
//                        fixedSentence.append(token.after());
//                    }
//                } else {
//                    fixedSentence.append(token.word());
//                    fixedSentence.append(token.after());
//                }
//            }
//            fixedDocument.append(fixedSentence.toString());
//            fixedDocument.append(System.lineSeparator());
//        }
//
//        return fixedDocument.toString();
//    }

//    public static boolean correctSpelling(List<CoreLabel> tokens, List<String> current) {
//        if (tokens.size() == 0) {
//            return false;
//        }
//        String nextToken = tokens.get(0).word();
//        if (Pattern.compile( "[0-9]" ).matcher(nextToken).find()) {
//            return false;
//        }
//        current.add(nextToken);
//
//        String corrected = StringUtils.join(current, "");
//        if (!SpellChecker.check(corrected.toLowerCase())) {
//            if (corrected.length() > 30 || current.size() == 15) {
//                return false;
//            }
//
//            return correctSpelling(tokens.subList(1, tokens.size()), current);
//        } else {
////            if (corrected.length() < 6 && tokens.size() > 1) { //possible false-positive?
////                String nextToken2 = tokens.get(1).word();
////
////            }
//            return true;
//        }
//    }

    public static List<List<TaggedWord>> tagText(String text) {
        String modelPath = Tools.getProperty("nlp.stanfordPOSTagger");
        MaxentTagger tagger = new MaxentTagger(modelPath);
        List<List<HasWord>> sentences = MaxentTagger.tokenizeText(new BufferedReader(new StringReader(text)));
        List<List<TaggedWord>> taggedSentences = new ArrayList<>();
        for (List<HasWord> sentence : sentences) {
            List<TaggedWord> tSentence = tagger.tagSentence(sentence);
            taggedSentences.add(tSentence);
        }
        return taggedSentences;
    }

}
