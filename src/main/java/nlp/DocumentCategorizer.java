package nlp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import common.Tools;
import opennlp.tools.cmdline.ModelLoader;
import opennlp.tools.cmdline.doccat.DoccatEvaluationErrorListener;
import opennlp.tools.doccat.*;
import opennlp.tools.util.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import opennlp.tools.tokenize.TokenizerModel;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.core.io.ClassPathResource;
import solrapi.DoccatThrottle;
import solrapi.SolrClient;
import sun.awt.Mutex;

public class DocumentCategorizer {

    final static Logger logger = LogManager.getLogger(DocumentCategorizer.class);

    private static final double CATEGORY_THRESHOLD = 0.3;

    private DoccatModelManager modelMgr;
    private DocumentCategorizerME categorizer;
    private TokenizerModel tokenizerModel;
    private SolrClient solrClient;
    private Mutex mutex;

    private static final int NUM_CROSS_VALIDATION_PARTITIONS = 5;

    public DocumentCategorizer(SolrClient solrClient) {
        this.solrClient = solrClient;
        modelMgr = new DoccatModelManager();
        mutex = new Mutex();
    }

    public static void main(String[] args) {
        SolrClient solrClient = new SolrClient("http://134.20.2.51:8983/solr");
        DocumentCategorizer cat = new DocumentCategorizer(solrClient);
//        //TopicModeller topicModeller = new TopicModeller(solrClient);
//        solrClient.setTopicModeller(null);
//
//        String optimalTrainingFile = Tools.getProperty("nlp.doccatTrainingFile");
//        solrClient.writeCorpusDataToFile(optimalTrainingFile, null, null, "", solrClient::getDoccatDataQuery, solrClient::formatForDoccatModelTraining,
//                new DoccatThrottle(solrClient, solrClient::getDoccatDataQuery));
//
//        ObjectStream<String> lineStream = NLPTools.getLineStreamFromFile(optimalTrainingFile);
//        TrainTestSplitter splitter = new TrainTestSplitter(42, optimalTrainingFile);
//        splitter.trainTestSplit(0.8, lineStream);
//
//        DoccatModel model = null;
////        try {
////            model = NLPTools.getModel(DoccatModel.class, Tools.getProperty("nlp.doccatModel"));
////        } catch (IOException e) {
////            e.printStackTrace();
////        }
//        try {
//            try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(splitter.getTrain())) {
//                model = DocumentCategorizerME.train("en", sampleStream, NLPTools.getTrainingParameters(25, 2), new DoccatFactory(cat.getFeatureGenerators()));
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(splitter.getTest())) {
//            String evalReport = cat.evaluateDoccatModel(sampleStream, model);
//            System.out.println(evalReport);
//        } catch (IOException e) {
//            logger.error(e.getMessage(), e);
//        }



//        try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(splitter.getTrain())) {
//            model = DocumentCategorizerME.train("en", sampleStream, NLPTools.getTrainingParameters(100, 2), new DoccatFactory(cat.getFeatureGenerators()));
//        } catch (IOException e) {
//            logger.error(e.getMessage(), e);
//        }

        try {
            SolrDocumentList docs = solrClient.QuerySolrDocuments("id:93d0ec37fc219c0fdd20f2d3a63797dab7f1e49f", 1, 0, null, null);
            SolrDocument doc = docs.get(0);

            String parsed = doc.get("parsed").toString();
            List<String> category = cat.detectBestCategories(parsed, 0);

        } catch (SolrServerException | IOException e) {
            logger.error(e.getMessage(), e);
        }


        //cat.optimizeModelTrainingParameters();
    }

    private class DoccatModelManager {
        private DoccatModel model;
        private boolean needsRefresh;

        public DoccatModelManager() {
            needsRefresh = true;
            try {
                refreshModel();
            } catch (IOException e) { }
        }

        private void refreshModel() throws IOException {
            if (needsRefresh) {
                try {
                    model = NLPTools.getModel(DoccatModel.class, Tools.getProperty("nlp.doccatModel"));
                    model.getFactory().setFeatureGenerators(getFeatureGenerators());
                    needsRefresh = false;
                } catch (IOException e) {
                    logger.fatal("Document Categorization is unavailable!  Unable to load doccat model!", e);
                    needsRefresh = true;
                    throw e;
                }
            }
        }

        public DoccatModel getModel() throws IOException {
            refreshModel();
            return model;
        }

        public void toggleRefreshNeeded() {
            needsRefresh = true;
        }
    }

    public List<String> detectBestCategories(String document, int numTries) throws IOException {
        try {
            SortedMap<Double, Set<String>> outcomes;
            String[] docCatTokens = GetDocCatTokens(document);

            DoccatModel model = modelMgr.getModel();
            DocumentCategorizerME categorizer = new DocumentCategorizerME(model);

            //Categorize
            outcomes = categorizer.sortedScoreMap(docCatTokens);

            StandardDeviation standardDev = new StandardDeviation();
            Double[] probs = outcomes.keySet().toArray(new Double[outcomes.size()]);
            Arrays.sort(probs, Collections.reverseOrder());
            Double maxProb = probs[0];
            final double stdDev = standardDev.evaluate(ArrayUtils.toPrimitive(probs));

            List<String> categories = new ArrayList<>();
            int numCats = categorizer.getNumberOfCategories();
            double minAllowableProb = (1.02 / (double)numCats);
            if (stdDev < 0.001 || maxProb < minAllowableProb) { //this is an excessively flat probability distribution
                categories.addAll(outcomes.entrySet().stream().filter(p -> p.getValue().contains("Not_Applicable"))
                        .map(p -> p.getValue().toArray(new String[1])[0] + " " + p.getKey())
                        .collect(Collectors.toList()));
            } else if (outcomes.get(maxProb).contains("Not_Applicable")) {
                final double closeEnough = stdDev * 0.05; // 1/20th standard deviation from the max
                double secondBest = probs[1];
                if (secondBest >= (maxProb - closeEnough)) {
                    categories.addAll(outcomes.entrySet().stream()
                            .filter(p -> !p.getValue().contains("Not_Applicable") && p.getKey() >= (maxProb - closeEnough))
                            .map(p -> p.getValue().toArray(new String[1])[0] + " " + p.getKey())
                            .collect(Collectors.toList()));
                } else {
                    categories.add(outcomes.get(maxProb).toArray(new String[1])[0] + " " + maxProb);
                }
            } else {
                categories.addAll(outcomes.entrySet().stream()
                        .filter(p -> !p.getValue().contains("Not_Applicable") && p.getKey() >= (maxProb - (0.125 * stdDev)))
                        .map(p -> p.getValue().toArray(new String[1])[0] + " " + p.getKey())
                        .collect(Collectors.toList()));
            }

            return categories;
        } catch (IOException e) {
            if (numTries < 10) {
                return detectBestCategories(document, ++numTries);
            } else {
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
                        new DoccatThrottle(solrClient, solrClient::getDoccatDataQuery));

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

    public String trainDoccatModel(int iterations) throws IOException {
        String evalReport;
        DoccatModel model;
        //Write training data to file
        String optimalTrainingFile = Tools.getProperty("nlp.doccatTrainingFile");
        solrClient.writeCorpusDataToFile(optimalTrainingFile, null, null, "", solrClient::getDoccatDataQuery, solrClient::formatForDoccatModelTraining,
                new DoccatThrottle(solrClient, solrClient::getDoccatDataQuery));

        //Use optimized iterations/cutoff to train model
        ObjectStream<String> lineStream = NLPTools.getLineStreamFromFile(optimalTrainingFile);
        TrainTestSplitter splitter = new TrainTestSplitter(42, optimalTrainingFile);
        splitter.trainTestSplit(0.8, lineStream);
        try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(splitter.getTrain())) {
            model = DocumentCategorizerME.train("en", sampleStream, NLPTools.getTrainingParameters(iterations, 2), new DoccatFactory(getFeatureGenerators()));
        }

        try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(splitter.getTest())) {
            evalReport = evaluateDoccatModel(sampleStream, model);
        }

        try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(Tools.getProperty("nlp.doccatModel")))) {
            model.serialize(modelOut);
        }
        modelMgr.toggleRefreshNeeded();
        logger.info(evalReport);
        return evalReport;
    }

    private FeatureGenerator[] getFeatureGenerators() {
        try {
            FeatureGenerator[] featureGenerators = new FeatureGenerator[] { new BagOfWordsFeatureGenerator(), new NGramFeatureGenerator(2, 3) };
            return featureGenerators;
        } catch (InvalidFormatException e) {
            return null;
        }
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

        DoccatCrossValidator validator = new DoccatCrossValidator("en", params, new DoccatFactory(getFeatureGenerators()), listeners);
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
            model.getFactory().setFeatureGenerators(getFeatureGenerators());
            DocumentCategorizerME categorizer = new DocumentCategorizerME(model);

            ByteArrayOutputStream reportStream = new ByteArrayOutputStream();
            //ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
            DoccatFineGrainedReportListener reportListener = new DoccatFineGrainedReportListener(reportStream);
            //DoccatEvaluationErrorListener errorListener = new DoccatEvaluationErrorListener(errorStream);
            DoccatEvaluationMonitor[] listeners = { reportListener/*, errorListener */ };

            DocumentCategorizerEvaluator evaluator = new DocumentCategorizerEvaluator(categorizer, listeners);

            samples.reset();
            evaluator.evaluate(samples);
            reportListener.writeReport();
            //String errors = errorStream.toString();
            String report = reportStream.toString();
            return report;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return "";
        }
    }
}

