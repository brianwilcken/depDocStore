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
import java.util.LinkedList;
import java.util.List;

import common.Tools;

import opennlp.tools.cmdline.namefind.NameEvaluationErrorListener;
import opennlp.tools.namefind.*;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.sentdetect.SentenceSampleStream;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenSampleStream;
import opennlp.tools.tokenize.TokenizerEvaluationMonitor;
import opennlp.tools.tokenize.TokenizerEvaluator;
import opennlp.tools.tokenize.TokenizerFactory;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.sentdetect.SentenceDetectorEvaluator;
import opennlp.tools.sentdetect.SentenceDetectorFactory;
import opennlp.tools.cmdline.doccat.DoccatEvaluationErrorListener;
import opennlp.tools.cmdline.sentdetect.SentenceEvaluationErrorListener;
import opennlp.tools.cmdline.tokenizer.TokenEvaluationErrorListener;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.doccat.DoccatEvaluationMonitor;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerEvaluator;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.sentdetect.SentenceDetectorEvaluationMonitor;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

public class NLPExperiments {
	public static void main(String args[]){
		NLPExperiments detector = new NLPExperiments();
		
		//detector.TrainSentenceDetectionModel("data/en-sent_brian.train", "data/en-sent_brian.bin");
		//detector.EvaluateSentenceDetectionModel("data/en-sent.eval", "data/en-sent_brian.bin");
		//string[] sentences = detector.DetectSentences("data/en-sent_brian.bin", "data/en-sent.test");
//		for (String sentence : sentences) {
//			System.out.println(sentence);
//		}
		
		
		//detector.TrainTokenizerModel("data/nepal-earthquake-tokenizer.train", "data/nepal-earthquake-tokenizer.bin");
//		//detector.EvaluateTokenizerModel("data/en-sent.eval", "data/en-token_brian.bin");
//		String[] tokens = detector.DetectTokens("data/nepal-earthquake-tokenizer.bin", "data/cyclone-pam-tokenizer.test");
//		for (String token : tokens) {
//			System.out.println(token);
//		}
		
//		detector.TrainNamedEntitiesModel("data/en-ner-hazard.train", "data/en-ner-hazard.bin");
//		List<String> namedEntities = detector.DetectNamedEntities("data/en-ner-hazard.bin", "data/en-ner-hazard.test");
//		for (String entity : namedEntities) {
//			System.out.println(entity);
//		}
		
//		LuceneExperiments lucene = new LuceneExperiments();
//		lucene.loadDictionary();
//		lucene.performSpellCheck("chooseee");
		
//		String cleanedTrainingData = detector.NormalizeDocData(Tools.GetFileString("data/news-data.train"));
//		detector.TrainDocCatModel(cleanedTrainingData, "data/news-data.bin", 100, 1);
		//detector.TrainDocCatModel(Tools.GetFileString("data/doccat.train"), "data/doccat.bin");
		
//		String cleanedEvalData = detector.NormalizeDocData(Tools.GetFileString("data/news-data.eval"));
//		detector.EvaluateDoccatModel(cleanedEvalData, "data/news-data.bin");
		//detector.EvaluateDoccatModel(Tools.GetFileString("data/doccat.eval"), "data/doccat.bin");
		
//		String cleanedTestData = detector.CleanDocData(Tools.GetFileString("data/doccat.test"));
//		List<String> categorized = detector.DetectDocCategory(cleanedTestData, "data/doccat.bin");
//		try {
//			FileUtils.writeLines(new File("data/doccat.out"), categorized);
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
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
	
	private String[] DetectSentences(String modelFilePath, String testFilePath) {
		SentenceModel model = GetModel(SentenceModel.class, modelFilePath);
		SentenceDetectorME sentenceDetector = new SentenceDetectorME(model);
		
		String[] sentences = sentenceDetector.sentDetect(Tools.GetFileString(testFilePath));
		
		return sentences;
	}
	
	private String[] DetectSentences(SentenceModel model, String testData) {
		SentenceDetectorME sentenceDetector = new SentenceDetectorME(model);
		
		String[] sentences = sentenceDetector.sentDetect(testData);
		
		return sentences;
	}
	
	private void TrainSentenceDetectionModel(final String trainingFilePath, String modelFilePath) {
		try {
			ObjectStream<String> lineStream = GetLineStreamFromFile(trainingFilePath);
			
			SentenceModel model;
			
			try (ObjectStream<SentenceSample> sampleStream = new SentenceSampleStream(lineStream)) {
				model = SentenceDetectorME.train("en", sampleStream, new SentenceDetectorFactory(), TrainingParameters.defaultParams());
			}
			
			try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelFilePath))) {
				model.serialize(modelOut);
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void EvaluateSentenceDetectionModel(final String evalFilePath, String modelFilePath) {
		try (InputStream modelIn = new FileInputStream(modelFilePath)) {
			SentenceModel model = new SentenceModel(modelIn);
			SentenceDetectorME sentenceDetector = new SentenceDetectorME(model);
			
			List<SentenceDetectorEvaluationMonitor> listeners = new LinkedList<SentenceDetectorEvaluationMonitor>();
			listeners.add(new SentenceEvaluationErrorListener());

			SentenceDetectorEvaluator evaluator = new SentenceDetectorEvaluator(sentenceDetector, listeners.toArray(new SentenceDetectorEvaluationMonitor[listeners.size()]));

			ObjectStream<String> lineStream = GetLineStreamFromFile(evalFilePath);
			
			try (ObjectStream<SentenceSample> sampleStream = new SentenceSampleStream(lineStream)) {
				evaluator.evaluate(sampleStream);
			}
			
			System.out.println(evaluator.getFMeasure());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private String[] DetectTokens(String modelFilePath, String testFilePath) {
		TokenizerModel model = GetModel(TokenizerModel.class, modelFilePath);
		TokenizerME tokenDetector = new TokenizerME(model);

		String[] tokens = tokenDetector.tokenize(Tools.GetFileString(testFilePath));
		
		return tokens;
	}
	
	private String[] DetectTokens(TokenizerModel model, String testData) {
		TokenizerME tokenDetector = new TokenizerME(model);
		
		String[] tokens = tokenDetector.tokenize(testData);
		
		return tokens;
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
	
	private void TrainTokenizerModel(String trainingFilePath, String modelFilePath) {
		try {
			ObjectStream<String> lineStream = GetLineStreamFromFile(trainingFilePath);
			
			TokenizerModel model;
			
			try (ObjectStream<TokenSample> sampleStream = new TokenSampleStream(lineStream)) {
				model = TokenizerME.train(sampleStream, new TokenizerFactory("en", new Dictionary(), false, null), TrainingParameters.defaultParams());
			}
			
			try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelFilePath))) {
				model.serialize(modelOut);
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void EvaluateTokenizerModel(final String evalFilePath, String modelFilePath) {
		try (InputStream modelIn = new FileInputStream(modelFilePath)) {
			TokenizerModel model = new TokenizerModel(modelIn);
			TokenizerME tokenDetector = new TokenizerME(model);
			
			List<TokenizerEvaluationMonitor> listeners = new LinkedList<TokenizerEvaluationMonitor>();
			listeners.add(new TokenEvaluationErrorListener());

			TokenizerEvaluator evaluator = new TokenizerEvaluator(tokenDetector, listeners.toArray(new TokenizerEvaluationMonitor[listeners.size()]));

			ObjectStream<String> lineStream = GetLineStreamFromFile(evalFilePath);
			
			try (ObjectStream<TokenSample> sampleStream = new TokenSampleStream(lineStream)) {
				evaluator.evaluate(sampleStream);
			}
			
			System.out.println(evaluator.getFMeasure());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private List<String> DetectNamedEntities(String modelFilePath, String testFilePath) {
		TokenNameFinderModel model = GetModel(TokenNameFinderModel.class, modelFilePath);
		NameFinderME nameFinder = new NameFinderME(model);

		String[] sentences = DetectSentences(GetModel(SentenceModel.class, "data/en-sent_brian.bin"), Tools.GetFileString(testFilePath));
		
		TokenizerModel tokenizerModel = GetModel(TokenizerModel.class, "data/en-token_brian.bin");
		
		List<String> namedEntities = new ArrayList<String>();
		
		for (String sentence : sentences) {
			String[] tokens = DetectTokens(tokenizerModel, sentence);
			Span[] nameSpans = nameFinder.find(tokens);
			for (Span span : nameSpans) {
				int start = span.getStart();
				int end = span.getEnd();
				for (int i = start; i < end; i++) {
					namedEntities.add(tokens[i]);
				}
			}
		}
		
		return namedEntities;
	}
	
	private void trainNERModel(String trainingFilePath, String modelFilePath) {
		try {
			ObjectStream<String> lineStream = GetLineStreamFromFile(trainingFilePath);
			
			TokenNameFinderModel model;
			
			try (ObjectStream<NameSample> sampleStream = new NameSampleDataStream(lineStream)) {
				//Optimize iterations/cutoff using 5-fold cross validation
				NLPTools.TrainingParameterTracker tracker = new NLPTools.TrainingParameterTracker();
				while (tracker.hasNext()) {
					OptimizationTuple optimizationTuple = tracker.getNext();
					optimizationTuple.P = CrossValidateNERModel(sampleStream, NLPTools.getTrainingParameters(optimizationTuple.i, optimizationTuple.c));
				}

				//Use optimized iterations/cutoff to train model on full dataset
				OptimizationTuple best = tracker.getBest();
				sampleStream.reset();
				model = NameFinderME.train("en", "hazard", sampleStream, NLPTools.getTrainingParameters(best.i, best.c), new TokenNameFinderFactory());
			}
			
			try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelFilePath))) {
				model.serialize(modelOut);
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private double CrossValidateNERModel(ObjectStream<NameSample> samples, TrainingParameters params) {
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
	
	private void TrainDocCatModel(String trainingData, String modelFilePath, int iterations, int cutoff) {
		try {		
			ObjectStream<String> lineStream = GetLineStreamFromString(trainingData);
			
			DoccatModel model;
			
			try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream)) {
				TrainingParameters mlParams = new TrainingParameters();
			    mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
			    mlParams.put(TrainingParameters.TRAINER_TYPE_PARAM, EventTrainer.EVENT_VALUE);
			    mlParams.put(TrainingParameters.ITERATIONS_PARAM, iterations);
			    mlParams.put(TrainingParameters.CUTOFF_PARAM, cutoff);
				model = DocumentCategorizerME.train("en", sampleStream, mlParams, new DoccatFactory());
			}
			
			try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelFilePath))) {
				model.serialize(modelOut);
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private List<String> DetectDocCategory(String testData, String modelFilePath) {
		DoccatModel model = GetModel(DoccatModel.class, modelFilePath);
		DocumentCategorizerME categorizer = new DocumentCategorizerME(model);
		
		TokenizerModel tokenizerModel = GetModel(TokenizerModel.class, "data/en-token.bin");
		
		String[] lines = testData.split(System.getProperty("line.separator"));
		
		List<String> categorized = new ArrayList<String>();
		
		for (String line : lines) {
			String[] tokens = DetectTokens(tokenizerModel, line);
			double[] outcomes = categorizer.categorize(tokens);
			String category = categorizer.getBestCategory(outcomes);
			categorized.add(category + "\t" + line);
		}
		
		return categorized;
	}
	
	private void EvaluateDoccatModel(String evalData, String modelFilePath) {
		try (InputStream modelIn = new FileInputStream(modelFilePath)) {
			DoccatModel model = new DoccatModel(modelIn);
			DocumentCategorizerME categorizer = new DocumentCategorizerME(model);
			
			List<DoccatEvaluationMonitor> listeners = new LinkedList<DoccatEvaluationMonitor>();
			listeners.add(new DoccatEvaluationErrorListener());

			DocumentCategorizerEvaluator evaluator = new DocumentCategorizerEvaluator(categorizer, listeners.toArray(new DoccatEvaluationMonitor[listeners.size()]));

			ObjectStream<String> lineStream = GetLineStreamFromString(evalData);
			
			try (ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream)) {
				evaluator.evaluate(sampleStream);
			}
			
			System.out.println(evaluator.getAccuracy());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private String NormalizeDocData(String data) {
		// use a regular expression to wash the data
		//String cleaned = data.replaceAll("(https?\\S+|@\\w+\\W|#\\b|RT|[^a-zA-Z\\s\\d\\,\\;\\/\\$\\:\\(\\)\\&\\.\\-\\_\\\\\"\\'\\!\\?])", "");
		//cleaned = cleaned.replaceAll("&amp;", "and");
		
//		try {
//			//Replace OOV words
//			String dictionary = FileUtils.readFileToString(new File("data/OOV_Dictionary_V1.0.tsv"));
//			String[] definitions = dictionary.split("\\r");
//			for (String definition : definitions) {
//				String[] tokens = definition.split("\\t");
//				if (tokens.length > 0) {
//					String misspelled = tokens[0];
//					String corrected = tokens[1];
//					cleaned.replaceAll(misspelled, corrected);
//				}
//			}
//			
//			//Remove stopwords
//			String stopWords = FileUtils.readFileToString(new File("data/nlp_en_stop_words.txt"));
//			String[] stopWordsArr = stopWords.split("\\n");
//			String stopWordsPattern = String.join("|", stopWordsArr);
//			Pattern pattern = Pattern.compile("\\b(?:" + stopWordsPattern + ")\\b[-]*", Pattern.CASE_INSENSITIVE);
//			Matcher matcher = pattern.matcher(cleaned);
//			cleaned = matcher.replaceAll("");
//			
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
//		cleaned = cleaned.toLowerCase();
//		
//		return cleaned;
		
		return data.toLowerCase();
	}
}
