package nlp;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import common.Tools;
import opennlp.tools.cmdline.doccat.DoccatFineGrainedReportListener;
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

	public List<IndexedEvent> detectEventDataCategories(List<IndexedEvent> events) {
		DoccatModel model = NLPTools.getModel(DoccatModel.class, Tools.getProperty("nlp.doccatModel"));
		DocumentCategorizerME categorizer = new DocumentCategorizerME(model);

		PorterStemmer stemmer = new PorterStemmer();

		TokenizerModel tokenizerModel = NLPTools.getModel(TokenizerModel.class, new ClassPathResource(Tools.getProperty("nlp.tokenizerModel")));

		for (IndexedEvent event : events) {
			String[] docCatTokens = event.GetDocCatTokens(tokenizerModel, stemmer);

			//Categorize
			double[] outcomes = categorizer.categorize(docCatTokens);
			String category = categorizer.getBestCategory(outcomes);
			event.setCategory(category);
		}

		return events;
	}

	public double trainEventCategorizationModel(String trainingDataFile) {
		double finalAccuracy;
		try {		
			ObjectStream<String> lineStream = NLPTools.getLineStreamFromFile(trainingDataFile);
			
			DoccatModel model;

			try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream)) {
				//Optimize iterations/cutoff using 5-fold cross validation
				NLPTools.TrainingParameterTracker tracker = new NLPTools.TrainingParameterTracker();
				while (tracker.hasNext()) {
					NLPTools.TrainingParameterTracker.Tuple tuple = tracker.getNext();
					tuple.P = crossValidateEventCategorizationModel(sampleStream, NLPTools.getTrainingParameters(tuple.i, tuple.c));
				}

				//Use optimized iterations/cutoff to train model on full dataset
				NLPTools.TrainingParameterTracker.Tuple best = tracker.getBest();
				sampleStream.reset();
				model = DocumentCategorizerME.train("en", sampleStream, NLPTools.getTrainingParameters(best.i, best.c), new DoccatFactory());
				
				finalAccuracy = best.P;
			}

			try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(Tools.getProperty("nlp.doccatModel")))) {
				model.serialize(modelOut);
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			finalAccuracy = -1;
		}
		return finalAccuracy;
	}

	private double crossValidateEventCategorizationModel(ObjectStream<DocumentSample> samples, TrainingParameters params) {
		DoccatFineGrainedReportListener reportListener = new DoccatFineGrainedReportListener();
		DoccatEvaluationMonitor[] listeners = { new DoccatEvaluationErrorListener(), reportListener};

		DoccatCrossValidator validator = new DoccatCrossValidator("en", params, new DoccatFactory(), listeners);
		try {
			validator.evaluate(samples, 5);
			reportListener.writeReport();
			return validator.getDocumentAccuracy();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return -1;
		}
	}
	
	private double evaluateEventCategorizationModel(ObjectStream<DocumentSample> samples, DoccatModel model) {
		try {
			DocumentCategorizerME categorizer = new DocumentCategorizerME(model);
			
			List<DoccatEvaluationMonitor> listeners = new LinkedList<DoccatEvaluationMonitor>();
			listeners.add(new DoccatEvaluationErrorListener());

			DocumentCategorizerEvaluator evaluator = new DocumentCategorizerEvaluator(categorizer, listeners.toArray(new DoccatEvaluationMonitor[listeners.size()]));

			evaluator.evaluate(samples);
			
			return evaluator.getAccuracy();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return -1;
		}
	}
}
