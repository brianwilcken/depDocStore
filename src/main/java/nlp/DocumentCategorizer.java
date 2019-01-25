package nlp;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import common.Tools;
import opennlp.tools.cmdline.doccat.DoccatFineGrainedReportListener;
import opennlp.tools.doccat.*;
import opennlp.tools.util.ObjectStream;
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
                solrClient.writeTrainingDataToFile(doccatTrainingFile, "", solrClient::getDoccatDataQuery, solrClient::formatForDoccatModelTraining,
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

    public double trainDoccatModel() throws IOException {
        double modelAccuracy;
        DoccatModel model;
        try {
            //Write training data to file
            String optimalTrainingFile = Tools.getProperty("nlp.doccatTrainingFile");
            solrClient.writeTrainingDataToFile(optimalTrainingFile, "", solrClient::getDoccatDataQuery, solrClient::formatForDoccatModelTraining,
                    new SolrClient.DoccatThrottle());

            //Use optimized iterations/cutoff to train model
            OptimizationTuple best = readTrainingParametersFromFile();
            ObjectStream<String> lineStream = NLPTools.getLineStreamFromFile(optimalTrainingFile);
            try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream)) {
                model = DocumentCategorizerME.train("en", sampleStream, NLPTools.getTrainingParameters(best.i, best.c), new DoccatFactory());

                modelAccuracy = evaluateDoccatModel(sampleStream, model);
            }

            try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(Tools.getProperty("nlp.doccatModel")))) {
                model.serialize(modelOut);
            }
        } catch (ClassNotFoundException e) {
            logger.error(e.getMessage(), e);
            modelAccuracy = -1;
        }
        logger.info("Doccat Model Training Complete!  Accuracy: " + (modelAccuracy * 100) + "%");
        return modelAccuracy;
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

    private double evaluateDoccatModel(ObjectStream<DocumentSample> samples, DoccatModel model) {
        try {
            DocumentCategorizerME categorizer = new DocumentCategorizerME(model);

            DoccatFineGrainedReportListener reportListener = new DoccatFineGrainedReportListener();
            DoccatEvaluationMonitor[] listeners = { reportListener};

            DocumentCategorizerEvaluator evaluator = new DocumentCategorizerEvaluator(categorizer, listeners);

            samples.reset();
            evaluator.evaluate(samples);
            reportListener.writeReport();
            return evaluator.getAccuracy();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return -1;
        }
    }
}

