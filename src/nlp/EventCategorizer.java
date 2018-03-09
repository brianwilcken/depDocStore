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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import common.Tools;
import eventsregistryapi.model.IndexedEvent;
import opennlp.tools.cmdline.doccat.DoccatEvaluationErrorListener;
import opennlp.tools.doccat.DoccatEvaluationMonitor;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerEvaluator;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

public class EventCategorizer {
	
	public void TrainEventCategorizationModel(String trainingDataFile, int iterations, int cutoff) {
		try {		
			ObjectStream<String> lineStream = GetLineStreamFromFile(trainingDataFile);
			
			DoccatModel model;
			
			try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream)) {
				TrainingParameters mlParams = new TrainingParameters();
			    mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
			    mlParams.put(TrainingParameters.TRAINER_TYPE_PARAM, EventTrainer.EVENT_VALUE);
			    mlParams.put(TrainingParameters.ITERATIONS_PARAM, iterations);
			    mlParams.put(TrainingParameters.CUTOFF_PARAM, cutoff);
				model = DocumentCategorizerME.train("en", sampleStream, mlParams, new DoccatFactory());
			}

			try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(Tools.getProperty("nlp.doccatModel")))) {
				model.serialize(modelOut);
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public List<IndexedEvent> DetectEventDataCategories(List<IndexedEvent> events) {
		DoccatModel model = GetModel(DoccatModel.class, Tools.getProperty("nlp.doccatModel"));
		DocumentCategorizerME categorizer = new DocumentCategorizerME(model);
		
		TokenizerModel tokenizerModel = GetModel(TokenizerModel.class, Tools.getProperty("nlp.tokenizerModel"));
		
		for (IndexedEvent event : events) {
			String[] tokens = DetectTokens(tokenizerModel, event.GetDocCatForm());
			double[] outcomes = categorizer.categorize(tokens);
			String category = categorizer.getBestCategory(outcomes);
			event.setCategory(category);
		}
		
		return events;
	}
	
	public double EvaluateEventCategorizationModel(String evalData) {
		try (InputStream modelIn = new FileInputStream(Tools.getProperty("nlp.doccatModel"))) {
			DoccatModel model = new DoccatModel(modelIn);
			DocumentCategorizerME categorizer = new DocumentCategorizerME(model);
			
			List<DoccatEvaluationMonitor> listeners = new LinkedList<DoccatEvaluationMonitor>();
			listeners.add(new DoccatEvaluationErrorListener());

			DocumentCategorizerEvaluator evaluator = new DocumentCategorizerEvaluator(categorizer, listeners.toArray(new DoccatEvaluationMonitor[listeners.size()]));

			ObjectStream<String> lineStream = GetLineStreamFromString(evalData);
			
			try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream)) {
				evaluator.evaluate(sampleStream);
			}
			
			return evaluator.getAccuracy();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return -1;
		}
	}
	
	private String[] DetectTokens(TokenizerModel model, String testData) {
		TokenizerME tokenDetector = new TokenizerME(model);
		
		String[] tokens = tokenDetector.tokenize(testData);
		
		return tokens;
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
