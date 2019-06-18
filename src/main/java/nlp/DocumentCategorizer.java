package nlp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import common.Tools;
import opennlp.tools.doccat.*;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import opennlp.tools.tokenize.TokenizerModel;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.core.io.ClassPathResource;
import solrapi.SolrClient;

public class DocumentCategorizer {

    final static Logger logger = LogManager.getLogger(DocumentCategorizer.class);

    private static final double CATEGORY_THRESHOLD = 0.3;

    private DoccatModel model;
    private DocumentCategorizerME categorizer;
    private TokenizerModel tokenizerModel;
    private SolrClient solrClient;

    private static final int NUM_CROSS_VALIDATION_PARTITIONS = 5;

    public DocumentCategorizer(SolrClient solrClient) {
        this.solrClient = solrClient;
    }

    public static void main(String[] args) {
        SolrClient client = new SolrClient("http://134.20.2.51:8983/solr");
        DocumentCategorizer cat = new DocumentCategorizer(client);
        TopicModeller topicModeller = new TopicModeller(client);
        client.setTopicModeller(topicModeller);

        String optimalTrainingFile = Tools.getProperty("nlp.doccatTrainingFile");
        client.writeCorpusDataToFile(optimalTrainingFile, null, null, "", client::getDoccatDataQuery, client::formatForDoccatModelTraining,
                new SolrClient.DoccatThrottle(10));
        ObjectStream<String> lineStream = NLPTools.getLineStreamFromFile(optimalTrainingFile);
        TrainTestSplitter splitter = new TrainTestSplitter(42, optimalTrainingFile);
        splitter.trainTestSplit(0.8, lineStream);

        DoccatModel model = null;
        try {
            model = NLPTools.getModel(DoccatModel.class, Tools.getProperty("nlp.doccatModel"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(splitter.getTest())) {
            String evalReport = cat.evaluateDoccatModel(sampleStream, model);
            System.out.println(evalReport);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }


//        try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(splitter.getTrain())) {
//            model = DocumentCategorizerME.train("en", sampleStream, NLPTools.getTrainingParameters(100, 2), new DoccatFactory());
//        } catch (IOException e) {
//            logger.error(e.getMessage(), e);
//        }

//        try {
//            SolrDocumentList docs = client.QuerySolrDocuments("id:8be8db4af950cf739daa3d127d43c77c44e95099", 1, 0, null, null);
//            SolrDocument doc = docs.get(0);
//
//            String parsed = doc.get("parsed").toString();
//            List<String> category = cat.detectBestCategories(parsed, 0);
//
//        } catch (SolrServerException | IOException e) {
//            logger.error(e.getMessage(), e);
//        }


        //cat.optimizeModelTrainingParameters();
    }

    public List<String> detectBestCategories(String document, int... numTries) throws IOException {
        try {
            DoccatModel model = NLPTools.getModel(DoccatModel.class, Tools.getProperty("nlp.doccatModel"));
            DocumentCategorizerME categorizer = new DocumentCategorizerME(model);

            String[] docCatTokens = GetDocCatTokens(document);

            //Categorize
            SortedMap<Double, Set<String>> outcomes = categorizer.sortedScoreMap(docCatTokens);
            StandardDeviation standardDev = new StandardDeviation();
            Double[] probs = outcomes.keySet().toArray(new Double[outcomes.size()]);
            Arrays.sort(probs, Collections.reverseOrder());
            Double maxProb = probs[0];
            final double stdDev = standardDev.evaluate(ArrayUtils.toPrimitive(probs));

            List<String> categories = new ArrayList<>();
            if (outcomes.get(maxProb).contains("Not_Applicable")) {
                final double closeEnough = stdDev * 0.125; // 1/8th standard deviation from the max
                double secondBest = probs[1];
                if (secondBest >= (maxProb - closeEnough)) {
                    categories.addAll(outcomes.entrySet().stream()
                            .filter(p -> !p.getValue().contains("Not_Applicable") && p.getKey() >= (maxProb - closeEnough))
                            .map(p -> p.getValue().toArray(new String[1])[0] + "|" + p.getKey())
                            .collect(Collectors.toList()));
                } else {
                    categories.add(outcomes.get(maxProb).toArray(new String[1])[0] + "|" + maxProb);
                }
            } else {
                categories.addAll(outcomes.entrySet().stream()
                        .filter(p -> !p.getValue().contains("Not_Applicable") && p.getKey() >= (maxProb - (1.5 * stdDev)))
                        .map(p -> p.getValue().toArray(new String[1])[0] + "|" + p.getKey())
                        .collect(Collectors.toList()));
            }

            return categories;
        } catch (IOException e) {
            //model may not exist
            if(numTries.length == 0) {
                trainDoccatModel(300, 0.1);
                return detectBestCategories(document, 1);
            } else {
                //something is very wrong!
                logger.fatal(e.getMessage(), e);
                throw e;
            }
        }
    }

    private String[] GetDocCatTokens(String document) {
        String normalized = NLPTools.normalizeText(document);
        String[] tokens = NLPTools.detectTokens(normalized);

        return tokens;
    }

    public void optimizeModelTrainingParameters() {
        try {
            NLPTools.TrainingParameterTracker tracker = new NLPTools.TrainingParameterTracker();

            //Optimize iterations/cutoff using n-fold cross validation
            while (tracker.hasNext()) {
                OptimizationTuple optimizationTuple = tracker.getNext();

                String doccatTrainingFile = Tools.getProperty("nlp.doccatTrainingFile") + optimizationTuple.i + optimizationTuple.c;
                solrClient.writeCorpusDataToFile(doccatTrainingFile, null, null, "", solrClient::getDoccatDataQuery, solrClient::formatForDoccatModelTraining,
                        new SolrClient.DoccatThrottle(0.1));

                ObjectStream<String> lineStream = NLPTools.getLineStreamFromFile(doccatTrainingFile);

                try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream)) {
                    optimizationTuple.P = crossValidateDoccatModel(sampleStream, NLPTools.getTrainingParameters(optimizationTuple.i, optimizationTuple.c), NUM_CROSS_VALIDATION_PARTITIONS);
                }
            }

            //Write optimized training parameters to file
            OptimizationTuple best = tracker.getBest();
            writeTrainingParametersToFile(best);

        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void writeTrainingParametersToFile(OptimizationTuple best) throws IOException {
        //Write optimized training parameters to file
        String doccatParametersFile = Tools.getProperty("nlp.doccatParametersFile_output");
        try(FileOutputStream fout = new FileOutputStream(doccatParametersFile)) {
            ObjectOutputStream oos = new ObjectOutputStream(fout);
            oos.writeObject(best);
            oos.flush();
            oos.close();
        }
    }

    private OptimizationTuple readTrainingParametersFromFile() throws IOException, ClassNotFoundException {
        //Read optimized training parameters from file
        ClassPathResource resource = new ClassPathResource(Tools.getProperty("nlp.doccatParametersFile_input"));
        OptimizationTuple best;
        try(InputStream stream = resource.getInputStream()) {
            ObjectInputStream ois = new ObjectInputStream(stream);
            best = (OptimizationTuple) ois.readObject();
            ois.close();
        }
        return best;
    }

    public String trainDoccatModel(int iterations, double entropyPercent) throws IOException {
        String evalReport;
        DoccatModel model;
        //Write training data to file
        String optimalTrainingFile = Tools.getProperty("nlp.doccatTrainingFile");
        solrClient.writeCorpusDataToFile(optimalTrainingFile, null, null, "", solrClient::getDoccatDataQuery, solrClient::formatForDoccatModelTraining,
                new SolrClient.DoccatThrottle(entropyPercent));

        //Use optimized iterations/cutoff to train model
        ObjectStream<String> lineStream = NLPTools.getLineStreamFromFile(optimalTrainingFile);
        TrainTestSplitter splitter = new TrainTestSplitter(42, optimalTrainingFile);
        splitter.trainTestSplit(0.8, lineStream);
        try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(splitter.getTrain())) {
            model = DocumentCategorizerME.train("en", sampleStream, NLPTools.getTrainingParameters(iterations, 2), new DoccatFactory());
        }

        try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(splitter.getTest())) {
            evalReport = evaluateDoccatModel(sampleStream, model);
        }

        try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(Tools.getProperty("nlp.doccatModel")))) {
            model.serialize(modelOut);
        }
        logger.info(evalReport);
        return evalReport;
    }

    private static class TrainTestSplitter {
        private File train;
        private File test;
        private Random random;

        public TrainTestSplitter(long seed, String trainingFile) {
            train = new File(trainingFile + "_split_train");
            test = new File(trainingFile + "_split_test");
            random = new Random(seed);
        }

        public void trainTestSplit(double percentTrain, ObjectStream<String> lineStream) {
            try {
                OutputStreamWriter trainWriter = new OutputStreamWriter(new FileOutputStream(train), StandardCharsets.UTF_8);
                OutputStreamWriter testWriter = new OutputStreamWriter(new FileOutputStream(test), StandardCharsets.UTF_8);

                String line = lineStream.read();
                while (line != null) {
                    double rand = random.nextDouble();
                    if (!Strings.isNullOrEmpty(line)) {
                        if (rand <= percentTrain) {
                            trainWriter.write(line);
                            trainWriter.write(System.lineSeparator());
                            trainWriter.flush();
                        } else {
                            testWriter.write(line);
                            testWriter.write(System.lineSeparator());
                            testWriter.flush();
                        }
                    }
                    line = lineStream.read();
                }

                trainWriter.close();
                testWriter.close();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }

        private ObjectStream<String> getObjectStream(final File file) {
            try {
                InputStreamFactory factory = new InputStreamFactory() {
                    public InputStream createInputStream() throws IOException {
                        InputStream stream = new FileInputStream(file);
                        return stream;
                    }
                };

                ObjectStream<String> lineStream = new PlainTextByLineStream(factory, StandardCharsets.UTF_8);
                return lineStream;
            } catch (IOException e) {
                return null;
            }
        }

        public ObjectStream<String> getTrain() {
            return getObjectStream(train);
        }

        public ObjectStream<String> getTest() {
            return getObjectStream(test);
        }
    }

    private double crossValidateDoccatModel(ObjectStream<DocumentSample> samples, TrainingParameters params, int nFolds) {
        DoccatFineGrainedReportListener reportListener = new DoccatFineGrainedReportListener();
        DoccatEvaluationMonitor[] listeners = { reportListener};
//		DoccatEvaluationErrorListener errorListener = new DoccatEvaluationErrorListener();
//		DoccatEvaluationMonitor[] listeners = { errorListener, reportListener};

        DoccatCrossValidator validator = new DoccatCrossValidator("en", params, new DoccatFactory(), listeners);
        try {
            validator.evaluate(samples, nFolds);
            reportListener.writeReport();
            return validator.getDocumentAccuracy();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return -1;
        }
    }



    private String evaluateDoccatModel(ObjectStream<DocumentSample> samples, DoccatModel model) {
        try {
            DocumentCategorizerME categorizer = new DocumentCategorizerME(model);

            ByteArrayOutputStream reportStream = new ByteArrayOutputStream();
            DoccatFineGrainedReportListener reportListener = new DoccatFineGrainedReportListener(reportStream);
            DoccatEvaluationMonitor[] listeners = { reportListener};

            DocumentCategorizerEvaluator evaluator = new DocumentCategorizerEvaluator(categorizer, listeners);

            samples.reset();
            evaluator.evaluate(samples);
            reportListener.writeReport();
            String report = reportStream.toString();
            return report;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return "";
        }
    }
}

