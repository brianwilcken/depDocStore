package nlp;

import common.Tools;
import opennlp.tools.namefind.*;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.lang.ArrayUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.core.io.ClassPathResource;
import solrapi.SolrClient;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class NamedEntityRecognizer {

    final static Logger logger = LogManager.getLogger(NamedEntityRecognizer.class);

    public static void main(String[] args) {
        SolrClient client = new SolrClient("http://localhost:8983/solr");
        NamedEntityRecognizer namedEntityRecognizer = new NamedEntityRecognizer(client);

        autoAnnotateAllForCategory(client, namedEntityRecognizer, "Wastewater");

//        try {
//            namedEntityRecognizer.trainNERModel("Electricity");
//        } catch (InsufficientTrainingDataException e) {
//            e.printStackTrace();
//        }
    }

    private static void autoAnnotateAllForCategory(SolrClient client, NamedEntityRecognizer namedEntityRecognizer, String category) {
        try {
            SolrDocumentList docs = client.QuerySolrDocuments("category:" + category + " AND -annotated:*", 1000, 0, null);
            for (SolrDocument doc : docs) {
                String document = (String)doc.get("docText");
                List<NamedEntity> entities = namedEntityRecognizer.detectNamedEntities(document, category, 0.5);
                String annotated = namedEntityRecognizer.autoAnnotate(document, entities);
                if (doc.containsKey("annotated")) {
                    doc.replace("annotated", annotated);
                } else {
                    doc.addField("annotated", annotated);
                }
                //FileUtils.writeStringToFile(new File("data/annotated.txt"), annotated, Charset.forName("Cp1252").displayName());

                client.indexDocument(doc);
            }
        } catch (SolrServerException | IOException e) {
            e.printStackTrace();
        }
    }

    public String prepForAnnotation(String document) {
        String[] sentences = detectSentences(document);
        document = String.join("\r\n", sentences);

        return document;
    }

    public String autoAnnotate(String document, List<NamedEntity> entities) {
        String[] sentences = detectSentences(document);
        if (!entities.isEmpty()) {
            Map<Integer, List<NamedEntity>> lineEntities = entities.stream()
                    .collect(Collectors.groupingBy(p -> p.getLine()));

            for (int s = 0; s < sentences.length; s++) {
                String sentence = sentences[s];
                if (lineEntities.containsKey(s)) {
                    String[] tokens = NLPTools.detectTokens(tokenizerModel, sentence);
                    for (NamedEntity namedEntity : lineEntities.get(s)) {
                        namedEntity.autoAnnotate(tokens);
                    }
                    sentence = String.join(" ", tokens);
                    sentences[s] = sentence;
                }
            }
            document = String.join("\r\n", sentences);
            document = document.replaceAll(" {2,}", " "); //ensure there are no multi-spaces that could disrupt model training
        } else {
            document = String.join("\r\n", sentences);
        }
        return document;
    }

    private static final Map<String, String> models;
    static
    {
        models = new HashMap<>();
        models.put("Water", Tools.getProperty("nlp.waterNerModel"));
        models.put("Wastewater", Tools.getProperty("nlp.wastewaterNerModel"));
        models.put("Electricity", Tools.getProperty("nlp.electricityNerModel"));
    }

    private static final Map<String, String> trainingFiles;
    static
    {
        trainingFiles = new HashMap<>();
        trainingFiles.put("Water", Tools.getProperty("nlp.waterNerTrainingFile"));
        trainingFiles.put("Wastewater", Tools.getProperty("nlp.wastewaterNerTrainingFile"));
        trainingFiles.put("Electricity", Tools.getProperty("nlp.electricityNerTrainingFile"));
    }

    private static final Map<String, Function<SolrQuery, SolrQuery>> dataGetters;
    static
    {
        dataGetters = new HashMap<>();
        dataGetters.put("Water", SolrClient::getWaterDataQuery);
        dataGetters.put("Wastewater", SolrClient::getWastewaterDataQuery);
        dataGetters.put("Electricity", SolrClient::getElectricityDataQuery);
    }

    private SentenceModel sentModel;
    private TokenizerModel tokenizerModel;
    private SolrClient client;

    public NamedEntityRecognizer(SolrClient client) {
        sentModel = NLPTools.getModel(SentenceModel.class, new ClassPathResource(Tools.getProperty("nlp.sentenceDetectorModel")));
        tokenizerModel = NLPTools.getModel(TokenizerModel.class, new ClassPathResource(Tools.getProperty("nlp.tokenizerModel")));
        this.client = client;
    }

    public List<NamedEntity> detectNamedEntities(String document, String category, double threshold) throws IOException {
        String[] sentences = detectSentences(document);
        return detectNamedEntities(sentences, category, threshold);
    }

    public List<NamedEntity> detectNamedEntities(String[] sentences, String category, double threshold, int... numTries) {
        List<NamedEntity> namedEntities = new ArrayList<>();
        try {
            TokenNameFinderModel model = NLPTools.getModel(TokenNameFinderModel.class, models.get(category));
            NameFinderME nameFinder = new NameFinderME(model);

            List<String> tokenized = new ArrayList<>();
            for (int s = 0; s < sentences.length; s++) {
                String sentence = sentences[s];
                String[] tokens = NLPTools.detectTokens(tokenizerModel, sentence);
                tokenized.add(String.join(" ", tokens));
                Span[] nameSpans = nameFinder.find(tokens);
                double[] probs = nameFinder.probs(nameSpans);
                for (int i = 0; i < nameSpans.length; i++) {
                    double prob = probs[i];
                    Span span = nameSpans[i];
                    int start = span.getStart();
                    int end = span.getEnd();
                    String[] entityParts = Arrays.copyOfRange(tokens, start, end);
                    String entity = String.join(" ", entityParts);
                    if (prob > threshold) {
                        namedEntities.add(new NamedEntity(entity, span, s));
                    }
                }
            }

//        try {
//            FileUtils.writeLines(new File("data/sentences.txt"), Charset.forName("Cp1252").displayName(), Arrays.asList(sentences));
//            FileUtils.writeLines(new File("data/tokenized.txt"), Charset.forName("Cp1252").displayName(), tokenized);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

            return namedEntities;
        } catch (IOException e) {
            if(numTries.length == 0) {
                trainNERModel(category); //model may not yet exist, but maybe there is data to train it...
                return detectNamedEntities(sentences, category, threshold, 1);
            } else {
                //no model training data available...
                logger.error(e.getMessage(), e);
                return namedEntities; //this collection will be empty
            }
        }
    }

    public String[] detectSentences(String document) {
        document = document.replace("\r\n", " ");
        document = document.replace("(", " ");
        document = document.replace(")", " ");
        document = document.replaceAll("\\P{Print}", " ");
        document = Tools.removeAllNumbers(document);
        document = Tools.removeSpecialCharacters(document);
        document = document.replaceAll("[\\\\%-*/:-?{-~!\"^_`\\[\\]+]", "");
        document = document.replaceAll(" +\\.", ".");
        document = document.replaceAll("\\.{2,}", ". ");
        document = document.replaceAll(" {2,}", " ");
        String[] sentences = NLPTools.detectSentences(sentModel, document);

        return sentences;
    }

    public void trainNERModel(String category) {
        try {
            String trainingFile = trainingFiles.get(category);

            client.writeTrainingDataToFile(trainingFile, dataGetters.get(category), client::formatForNERModelTraining);
            ObjectStream<String> lineStream = NLPTools.getLineStreamFromMarkableFile(trainingFile);

            TokenNameFinderModel model;

            TrainingParameters params = new TrainingParameters();
            params.put(TrainingParameters.ITERATIONS_PARAM, 300);
            params.put(TrainingParameters.CUTOFF_PARAM, 1);

            try (ObjectStream<NameSample> sampleStream = new NameSampleDataStream(lineStream)) {
                model = NameFinderME.train("en", null, sampleStream, params,
                        TokenNameFinderFactory.create(null, null, Collections.emptyMap(), new BioCodec()));
            }

            try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(models.get(category)))) {
                model.serialize(modelOut);
            }

        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
