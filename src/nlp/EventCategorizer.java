package nlp;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.standard.StandardAnalyzer;

import common.Tools;
import eventsregistryapi.model.IndexedEvent;
import opennlp.tools.cmdline.doccat.DoccatEvaluationErrorListener;
import opennlp.tools.doccat.DoccatCrossValidator;
import opennlp.tools.doccat.DoccatEvaluationMonitor;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerEvaluator;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

public class EventCategorizer {
	
	private TrainingParameters getTrainingParameters(int iterations, int cutoff) {
		TrainingParameters mlParams = new TrainingParameters();
	    mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
	    mlParams.put(TrainingParameters.TRAINER_TYPE_PARAM, EventTrainer.EVENT_VALUE);
	    mlParams.put(TrainingParameters.ITERATIONS_PARAM, iterations);
	    mlParams.put(TrainingParameters.CUTOFF_PARAM, cutoff);
	    
	    return mlParams;
	}
	
	private final class TrainingParameterTracker {
		private int iStart = 25; //Starting iterations
		private int iStep = 5; //Iteration step size
		private int iStop = 100; //Max iterations
		private int iSize = (iStop - iStart)/iStep + 1;
		
		private int cStart = 1; //Starting cutoff
		private int cStep = 1; //Cutoff step size
		private int cStop = 6; //Max cutoff
		private int cSize = (cStop - cStart)/cStep + 1;
		
		private Tuple current;
		private int coordI;
		private int coordC;
		
		private Tuple[][] grid; //keeps track of performance measures for each i/c pair
		
		public final class Tuple {
			int i;
			int c;
			double P;
			
			public Tuple(int i, int c) {
				this.i = i;
				this.c = c;
				this.P = 0;
			}
		}
		
		private void makeGrid() {
			grid = new Tuple[iSize][cSize];
			
			for (int i = 0; i < iSize; i++) {
				for (int c = 0; c < cSize; c++) {
					int iParam = iStart + (iStep * i);
					int cParam = cStart + (cStep * c);
					grid[i][c] = new Tuple(iParam, cParam);
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
		
		public Tuple getNext() {
			if (coordI <= (iSize - 1)) {
				current = grid[coordI++][coordC];
			} else if (++coordC <= (cSize - 1)){
				coordI = 0;
				current = grid[coordI][coordC];
			} 
			return current;
		}
		
		public Tuple getBest() {
			Tuple best = null;
			
			for (int i = 0; i < iSize; i++) {
				for (int c = 0; c < cSize; c++) {
					if (best == null) {
						best = grid[i][c];
						continue;
					}
					Tuple current = grid[i][c];
					if (best.P < current.P) {
						best = current;
					}
				}
			}
			return best;
		}
	}
	
	public double TrainEventCategorizationModel(String trainingDataFile) {
		double finalAccuracy;
		try {		
			ObjectStream<String> lineStream = GetLineStreamFromFile(trainingDataFile);
			
			DoccatModel model;

			try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream)) {
				//Optimize iterations/cutoff using 5-fold cross validation
				TrainingParameterTracker tracker = new TrainingParameterTracker();
				while (tracker.hasNext()) {
					TrainingParameterTracker.Tuple tuple = tracker.getNext();
					tuple.P = CrossValidateEventCategorizationModel(sampleStream, getTrainingParameters(tuple.i, tuple.c));
				}

				//Use optimized iterations/cutoff to train model on full dataset
				TrainingParameterTracker.Tuple best = tracker.getBest();
				sampleStream.reset();
				model = DocumentCategorizerME.train("en", sampleStream, getTrainingParameters(best.i, best.c), new DoccatFactory());
				
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
	
	public List<IndexedEvent> DetectEventDataCategories(List<IndexedEvent> events) {
		DoccatModel model = GetModel(DoccatModel.class, Tools.getProperty("nlp.doccatModel"));
		DocumentCategorizerME categorizer = new DocumentCategorizerME(model);
		
		PorterStemmer stemmer = new PorterStemmer();
		
		TokenizerModel tokenizerModel = GetModel(TokenizerModel.class, Tools.getProperty("nlp.tokenizerModel"));
		
		for (IndexedEvent event : events) {
			String[] docCatTokens = event.GetDocCatTokens(tokenizerModel, stemmer);

			//Categorize
			double[] outcomes = categorizer.categorize(docCatTokens);
			String category = categorizer.getBestCategory(outcomes);
			event.setCategory(category);
		}
		
		return events;
	}
	
	private double CrossValidateEventCategorizationModel(ObjectStream<DocumentSample> samples, TrainingParameters params) {
		List<DoccatEvaluationMonitor> listeners = new LinkedList<DoccatEvaluationMonitor>();
		listeners.add(new DoccatEvaluationErrorListener());
		
		DoccatCrossValidator validator = new DoccatCrossValidator("en", params, new DoccatFactory(), new DoccatEvaluationMonitor[] { new DoccatEvaluationErrorListener() });
		try {
			validator.evaluate(samples, 5);
			return validator.getDocumentAccuracy();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return -1;
		}
	}
	
	private double EvaluateEventCategorizationModel(ObjectStream<DocumentSample> samples, DoccatModel model) {
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
	
	private ObjectStream<String> GetLineStreamFromString(final String data)
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
	
	private ObjectStream<String> GetLineStreamFromFile(final String filePath)
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
	
	private <T> T GetModel(Class<T> clazz, String modelFilePath) {
		try (InputStream modelIn = new FileInputStream(modelFilePath)) {
			
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
}
