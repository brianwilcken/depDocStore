package nlp;

import java.io.*;
import java.util.LinkedList;
import java.util.List;

import common.Tools;
import opennlp.tools.cmdline.doccat.DoccatFineGrainedReportListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import solrapi.SolrClient;
import solrapi.SolrConstants;
import solrapi.model.AnalyzedEvent;
import solrapi.model.IndexedEvent;
import opennlp.tools.cmdline.doccat.DoccatEvaluationErrorListener;
import opennlp.tools.doccat.DoccatCrossValidator;
import opennlp.tools.doccat.DoccatEvaluationMonitor;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerEvaluator;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import org.springframework.core.io.ClassPathResource;

public class EventCategorizer {

	final static Logger logger = LogManager.getLogger(EventCategorizer.class);

	public static void main(String[] args) {
		SolrClient solrClient = new SolrClient("http://localhost:8983/solr");
		solrClient.writeTrainingDataToFile(Tools.getProperty("nlp.analysisDataFile"), solrClient::getAnalyzedIrrelevantDataQuery, solrClient::formatForAnalysis, new SolrClient.ClusteringThrottle("", 0));
		EventCategorizer categorizer = new EventCategorizer(solrClient);

		List<AnalyzedEvent> analyzedEvents = solrClient.CollectAnalyzedEventsFromCSV(Tools.getProperty("nlp.analysisDataFile"));
		try {
			List<IndexedEvent> indexedEvents = solrClient.CollectIndexedEventsFromAnalyzedEvents(analyzedEvents);
			categorizer.detectEventDataCategories(indexedEvents);

		} catch (SolrServerException | IOException e) {
			e.printStackTrace();
		}

		//categorizer.optimizeModelTrainingParameters();
		//categorizer.trainEventCategorizationModel();

//		NLPTools.TrainingParameterTracker tracker = new NLPTools.TrainingParameterTracker();
//		OptimizationTuple optimizationTuple = tracker.getNext();
//		try {
//			categorizer.writeTrainingParametersToFile(optimizationTuple);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}

//		try {
//			OptimizationTuple best = categorizer.readTrainingParametersFromFile();
//			System.out.println(best.P);
//		} catch (IOException e) {
//			e.printStackTrace();
//		} catch (ClassNotFoundException e) {
//			e.printStackTrace();
//		}
	}

	private final double THROTTLE_FOR_IRRELEVANT_EVENTS = 0.15;
	private final int NUM_CROSS_VALIDATION_PARTITIONS = 5;
	private SolrClient solrClient;

	public EventCategorizer(SolrClient solrClient) {
		this.solrClient = solrClient;
	}

	public List<IndexedEvent> detectEventDataCategories(List<IndexedEvent> events, int... numTries) throws IOException {
		try {
			DoccatModel model = NLPTools.getModel(DoccatModel.class, Tools.getProperty("nlp.doccatModel"));
			DocumentCategorizerME categorizer = new DocumentCategorizerME(model);

			PorterStemmer stemmer = new PorterStemmer();

			TokenizerModel tokenizerModel = NLPTools.getModel(TokenizerModel.class, new ClassPathResource(Tools.getProperty("nlp.tokenizerModel")));

			for (IndexedEvent event : events) {
				String[] docCatTokens = event.GetDocCatTokens(tokenizerModel, stemmer);

				//Categorize
				double[] outcomes = categorizer.categorize(docCatTokens);
				String category = categorizer.getBestCategory(outcomes);
				//categorizer.getAllResults(outcomes);
				event.setCategory(category);
			}

			return events;
		} catch (IOException e) {
			//model may not exist
			if(numTries.length == 0) {
				trainEventCategorizationModel();
				return detectEventDataCategories(events, 1);
			} else {
				//something is very wrong!
				logger.fatal(e.getMessage(), e);
				throw e;
			}
		}
	}

	public void optimizeModelTrainingParameters() {
		try {
			NLPTools.TrainingParameterTracker tracker = new NLPTools.TrainingParameterTracker();

			//Optimize iterations/cutoff using n-fold cross validation
			while (tracker.hasNext()) {
				OptimizationTuple optimizationTuple = tracker.getNext();

				String doccatTrainingFile = Tools.getProperty("nlp.doccatTrainingFile") + optimizationTuple.i + optimizationTuple.c;
				solrClient.writeTrainingDataToFile(doccatTrainingFile, solrClient::getDoccatDataQuery,
						solrClient::formatForEventCategorization, new SolrClient.EventCategorizationThrottle(SolrConstants.Events.CATEGORY_IRRELEVANT, THROTTLE_FOR_IRRELEVANT_EVENTS));

				ObjectStream<String> lineStream = NLPTools.getLineStreamFromFile(doccatTrainingFile);

				try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream)) {
					optimizationTuple.P = crossValidateEventCategorizationModel(sampleStream, NLPTools.getTrainingParameters(optimizationTuple.i, optimizationTuple.c), NUM_CROSS_VALIDATION_PARTITIONS);
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

	public double trainEventCategorizationModel() {
		double modelAccuracy;
		DoccatModel model;
		try {
			//Write training data to file
			String optimalTrainingFile = Tools.getProperty("nlp.doccatTrainingFile");
			solrClient.writeTrainingDataToFile(optimalTrainingFile, solrClient::getDoccatDataQuery,
					solrClient::formatForEventCategorization, new SolrClient.EventCategorizationThrottle(SolrConstants.Events.CATEGORY_IRRELEVANT, THROTTLE_FOR_IRRELEVANT_EVENTS));

			//Use optimized iterations/cutoff to train model
			OptimizationTuple best = readTrainingParametersFromFile();
			ObjectStream<String> lineStream = NLPTools.getLineStreamFromFile(optimalTrainingFile);
			try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream)) {
				model = DocumentCategorizerME.train("en", sampleStream, NLPTools.getTrainingParameters(best.i, best.c), new DoccatFactory());

				modelAccuracy = evaluateEventCategorizationModel(sampleStream, model);
			}

			try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(Tools.getProperty("nlp.doccatModel")))) {
				model.serialize(modelOut);
			}
		} catch (IOException | ClassNotFoundException e) {
			logger.error(e.getMessage(), e);
			modelAccuracy = -1;
		}
		logger.info("Event Categorization Model Training Complete!  Accuracy: " + (modelAccuracy * 100) + "%");
		return modelAccuracy;
	}

	private double crossValidateEventCategorizationModel(ObjectStream<DocumentSample> samples, TrainingParameters params, int nFolds) {
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
	
	private double evaluateEventCategorizationModel(ObjectStream<DocumentSample> samples, DoccatModel model) {
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
