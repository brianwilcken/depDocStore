package nlp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import common.Tools;
import opennlp.tools.doccat.*;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import opennlp.tools.tokenize.TokenizerModel;
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
        DocumentCategorizer cat = new DocumentCategorizer(new SolrClient("http://localhost:8983/solr"));
        cat.optimizeModelTrainingParameters();
    }

    public List<String> detectBestCategories(String document, int... numTries) throws IOException {
        try {
            DoccatModel model = NLPTools.getModel(DoccatModel.class, Tools.getProperty("nlp.doccatModel"));
            DocumentCategorizerME categorizer = new DocumentCategorizerME(model);

            String[] docCatTokens = GetDocCatTokens(document);

            //Categorize
            double[] outcomes = categorizer.categorize(docCatTokens);
            List<String> categories = new ArrayList<>();
            String bestCategory = categorizer.getBestCategory(outcomes);
            if (!bestCategory.equals("Not_Applicable")) {
                for (int i = 0; i < outcomes.length; i++) {
                    if (outcomes[i] >= CATEGORY_THRESHOLD) {
                        categories.add(Tools.removeUTF8BOM(categorizer.getCategory(i)));
                    }
                }
                if (categories.size() == 0) {
                    categories.add(bestCategory);
                }
            } else {
                categories.add(bestCategory); //Not_Applicable
            }

            return categories;
        } catch (IOException e) {
            //model may not exist
            if(numTries.length == 0) {
                trainDoccatModel();
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
                solrClient.writeCorpusDataToFile(doccatTrainingFile, "", solrClient::getDoccatDataQuery, solrClient::formatForDoccatModelTraining,
                        new SolrClient.DoccatThrottle());

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

    public String trainDoccatModel() throws IOException {
        String evalReport;
        DoccatModel model;
        try {
            //Write training data to file
            String optimalTrainingFile = Tools.getProperty("nlp.doccatTrainingFile");
            solrClient.writeCorpusDataToFile(optimalTrainingFile, "", solrClient::getDoccatDataQuery, solrClient::formatForDoccatModelTraining,
                    new SolrClient.DoccatThrottle());

            //Use optimized iterations/cutoff to train model
            OptimizationTuple best = readTrainingParametersFromFile();
            ObjectStream<String> lineStream = NLPTools.getLineStreamFromFile(optimalTrainingFile);
            TrainTestSplitter splitter = new TrainTestSplitter(42);
            splitter.trainTestSplit(0.8, lineStream);
            try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(splitter.getTrain())) {
                model = DocumentCategorizerME.train("en", sampleStream, NLPTools.getTrainingParameters(best.i, best.c), new DoccatFactory());
            }

            try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(splitter.getTest())) {
                evalReport = evaluateDoccatModel(sampleStream, model);
            }

            try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(Tools.getProperty("nlp.doccatModel")))) {
                model.serialize(modelOut);
            }
        } catch (ClassNotFoundException e) {
            logger.error(e.getMessage(), e);
            evalReport = "";
        }
        logger.info(evalReport);
        return evalReport;
    }

    private class TrainTestSplitter {
        private List<String> train;
        private List<String> test;
        private Random random;

        public TrainTestSplitter(long seed) {
            train = new ArrayList<>();
            test = new ArrayList<>();
            random = new Random(seed);
        }

        public void trainTestSplit(double percentTrain, ObjectStream<String> lineStream) {
            try {
                String line = lineStream.read();
                while (line != null) {
                    double rand = random.nextDouble();
                    if (rand <= percentTrain) {
                        train.add(line);
                    } else {
                        test.add(line);
                    }
                    line = lineStream.read();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private String convertListToString(List<String> lines) {
            StringBuilder bldr = new StringBuilder();
            for (String line : lines) {
                bldr.append(line);
                bldr.append(System.lineSeparator());
            }

            return bldr.toString();
        }

        private ObjectStream<String> getObjectStream(List<String> lines) {
            String data = convertListToString(lines);
            try {
                InputStreamFactory factory = new InputStreamFactory() {
                    public InputStream createInputStream() throws IOException {
                        InputStream stream = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
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

