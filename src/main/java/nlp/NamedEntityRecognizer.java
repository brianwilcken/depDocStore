package nlp;

import common.Tools;
import opennlp.tools.cmdline.namefind.NameEvaluationErrorListener;
import opennlp.tools.namefind.*;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;
import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.core.io.ClassPathResource;
import solrapi.SolrClient;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

public class NamedEntityRecognizer {

    public static void main(String[] args) {
        SolrClient client = new SolrClient("http://localhost:8983/solr");
        NamedEntityRecognizer namedEntityRecognizer = new NamedEntityRecognizer();
        //autoAnnotate(namedEntityRecognizer, client,"Water");
        //client.writeTrainingDataToFile(Tools.getProperty("nlp.waterNerTrainingFile"), client::getWaterDataQuery, client::formatForNERModelTraining);
        namedEntityRecognizer.trainNERModel(Tools.getProperty("nlp.waterNerTrainingFile"), "FAC", Tools.getProperty("nlp.waterNerModel"));
    }

    private static void autoAnnotate(NamedEntityRecognizer namedEntityRecognizer, SolrClient client, String category) {
        try {
            SolrDocumentList docs = client.QuerySolrDocuments("category:" + category + " AND -annotated:*", 1000, 0, null);
            for (SolrDocument doc : docs) {
                String document = (String)doc.get("docText");
                String[] sentences = namedEntityRecognizer.detectSentences(document);
                Map<String, Double> entities = namedEntityRecognizer.detectNamedEntities(sentences, category, 0.5);
                entities = entities.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
                document = String.join("\r\n", sentences);
                for (String tag : entities.keySet()) {
                    document = document.replace(tag, " <START:FAC> " + tag + " <END> ");
                }
                document = document.replaceAll(" {2,}", " "); //ensure there are no multi-spaces that could disrupt model training
                if (doc.containsKey("annotated")) {
                    doc.replace("annotated", document);
                } else {
                    doc.addField("annotated", document);
                }

                FileUtils.writeStringToFile(new File("data/annotated.txt"), document, Charset.forName("Cp1252").displayName());

                //client.indexDocument(doc);
            }
        } catch (SolrServerException | IOException e) {
            e.printStackTrace();
        }
    }

    private static final Map<String, TokenNameFinderModel> models;
    static
    {
        models = new HashMap<>();
        models.put("Water", NLPTools.getModel(TokenNameFinderModel.class, Tools.getProperty("nlp.waterNerModel")));
        models.put("Wastewater", NLPTools.getModel(TokenNameFinderModel.class, Tools.getProperty("nlp.waterNerModel")));
        //models.put("Electricity", NLPTools.getModel(TokenNameFinderModel.class, new ClassPathResource(Tools.getProperty("nlp.electricityNerModel"))));
    }

    private SentenceModel sentModel;
    private TokenizerModel tokenizerModel;

    public NamedEntityRecognizer() {
        sentModel = NLPTools.getModel(SentenceModel.class, new ClassPathResource(Tools.getProperty("nlp.sentenceDetectorModel")));
        tokenizerModel = NLPTools.getModel(TokenizerModel.class, new ClassPathResource(Tools.getProperty("nlp.tokenizerModel")));
    }

    public Map<String, Double> detectNamedEntities(String document, String category, double threshold) {
        String[] sentences = detectSentences(document);
        return detectNamedEntities(sentences, category, threshold);
    }

    public Map<String, Double> detectNamedEntities(String[] sentences, String category, double threshold) {
        TokenNameFinderModel model = models.get(category);
        NameFinderME nameFinder = new NameFinderME(model);

        Map<String, Double> namedEntities = new HashMap<>();
        List<String> tokenized = new ArrayList<>();
        for (String sentence : sentences) {
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
                if (!namedEntities.containsKey(entity) && prob > threshold) {
                    namedEntities.put(entity, prob);
                }
            }
        }

        try {
            FileUtils.writeLines(new File("data/sentences.txt"), Charset.forName("Cp1252").displayName(), Arrays.asList(sentences));
            FileUtils.writeLines(new File("data/tokenized.txt"), Charset.forName("Cp1252").displayName(), tokenized);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return namedEntities;
    }

    private String[] detectSentences(String document) {
        document = document.replace("\r\n", "");
        document = document.replace("(", " ");
        document = document.replace(")", " ");
        document = document.replaceAll("\\P{Print}", " ");
        //document = document.replaceAll("(\\w+\\W+)?\\d+(\\w+\\W+)?", ""); //removes all the numbers
        //document = document.replaceAll("[$-,/:-?{-~!\"^_`\\[\\]+]", ""); //removes most special characters
        document = document.replaceAll("[%-*/:-?{-~!\"^_`\\[\\]+]", "");
        //document = document.replaceAll("-", " ");
        document = document.replaceAll(" +\\.", ".");
        document = document.replaceAll("\\.{2,}", ". ");
        document = document.replaceAll(" {2,}", " ");
        String[] sentences = NLPTools.detectSentences(sentModel, document);

        return sentences;
    }

    public void trainNERModel(String trainingFilePath, String type, String modelPath) {
        try {
            ObjectStream<String> lineStream = NLPTools.getLineStreamFromMarkableFile(trainingFilePath);

            TokenNameFinderModel model;

            TrainingParameters params = new TrainingParameters();
            params.put(TrainingParameters.ITERATIONS_PARAM, 70);
            params.put(TrainingParameters.CUTOFF_PARAM, 1);

            try (ObjectStream<NameSample> sampleStream = new NameSampleDataStream(lineStream)) {
//                //Optimize iterations/cutoff using 5-fold cross validation
//                NLPTools.TrainingParameterTracker tracker = new NLPTools.TrainingParameterTracker();
//                while (tracker.hasNext()) {
//                    OptimizationTuple optimizationTuple = tracker.getNext();
//                    optimizationTuple.P = crossValidateNERModel(sampleStream, NLPTools.getTrainingParameters(optimizationTuple.i, optimizationTuple.c));
//                }
//
//                //Use optimized iterations/cutoff to train model on full dataset
//                OptimizationTuple best = tracker.getBest();
//                sampleStream.reset();
                model = NameFinderME.train("en", type, sampleStream, params,
                        TokenNameFinderFactory.create(null, null, Collections.emptyMap(), new BioCodec()));
            }

            try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelPath))) {
                model.serialize(modelOut);
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private double crossValidateNERModel(ObjectStream<NameSample> samples, TrainingParameters params) {
        TokenNameFinderEvaluationMonitor[] listeners = { new NameEvaluationErrorListener() };

        TokenNameFinderCrossValidator validator = new TokenNameFinderCrossValidator("en", "hazard", TrainingParameters.defaultParams(), new TokenNameFinderFactory(), listeners);
        try {
            validator.evaluate(samples, 5);
            return validator.getFMeasure().getFMeasure();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return -1;
        }
    }
}
