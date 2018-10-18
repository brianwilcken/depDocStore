package nlp.gibberish;

import common.Tools;
import nlp.NamedEntityRecognizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * gibberish detector used to train and classify sentences as gibberish or not.
 * @author sfiszman
 *
 */
public class GibberishDetector {
	
	private final Map<Character, Integer> alphabetPositionMap = new HashMap<Character, Integer>();
	private static final int MIN_COUNT_VAL = 10;

	private NamedEntityRecognizer recognizer;

	//for gibberish detection
	private static String goodEnglish = Tools.getResource(Tools.getProperty("nlp.gibberish.goodEnglish"));
	private static String badEnglish = Tools.getResource(Tools.getProperty("nlp.gibberish.badEnglish"));
	private static String bigEnglish = Tools.getResource(Tools.getProperty("nlp.gibberish.bigEnglish"));
	private static String alphabet = "abcdefghijklmnopqrstuvwxyz ";
	private static String[] goodEng;
	private static String[] badEng;
	private static String[] bigEng;
	private double[][] logProbabilityMatrix = null;
	private double threshold = 0d;

	public GibberishDetector(NamedEntityRecognizer recognizer) {
		this.recognizer = recognizer;
		goodEng = recognizer.detectSentences(goodEnglish);
		badEng = recognizer.detectSentences(badEnglish);
		bigEng = recognizer.detectSentences(bigEnglish);
		train(Arrays.asList(bigEng), Arrays.asList(goodEng), Arrays.asList(badEng));
	}
			
	private void train(List<String> trainingLinesList, List<String> goodLinesList, List<String> badLinesList) {
		initializePositionMap();
		
		int[][] alphabetCouplesMatrix = getAlphaBetCouplesMatrix(trainingLinesList);
		logProbabilityMatrix = getLogProbabilityMatrix(alphabetCouplesMatrix);
			
		List<Double> goodProbability = getAvgTransitionProbability(goodLinesList, logProbabilityMatrix);
		List<Double> badProbability = getAvgTransitionProbability(badLinesList, logProbabilityMatrix);
		
		double minGood = Collections.min(goodProbability);
		double maxBad = Collections.max(badProbability);
				
		if (minGood <= maxBad) {
			throw new AssertionError("cannot create a threshold");
		}
		threshold = getThreshold(minGood, maxBad);
	}
	
	// can be overridden for another threshold heuristic implementation
	protected double getThreshold(double minGood, double maxBad) {
		return (minGood + maxBad) / 2;
	}
		
	private void initializePositionMap() {
		char[] alphabetChars = alphabet.toCharArray();
		for (int i = 0; i < alphabetChars.length; i++) {
			alphabetPositionMap.put(alphabetChars[i], i);
		}
	}
	
	private String normalize(String line) {
		StringBuilder normalizedLine = new StringBuilder();
		for (char c: line.toLowerCase().toCharArray()) {
			normalizedLine.append(alphabet.contains(Character.toString(c)) ? c : "");
		}
		return normalizedLine.toString();
	}
	
	private List<String> getNGram(int n, String line) {
		String filteredLine = normalize(line);
		List<String> nGram = new ArrayList<String>();
		for (int start = 0; start < filteredLine.length() - n + 1; start++) {
			nGram.add(filteredLine.substring(start, start + n));			
		}
		return nGram;
	}
	
	private int[][] getAlphaBetCouplesMatrix(List<String> trainingLinesList) {
		int[][] counts = createArray(alphabet.length());		
		for (String line : trainingLinesList) {
			List<String> nGram = getNGram(2, line);
			for (String touple : nGram) {
				counts[alphabetPositionMap.get(touple.charAt(0))][alphabetPositionMap.get(touple.charAt(1))]++;				
			}	
		}
		return counts;
	}
	
	private double[][] getLogProbabilityMatrix(int[][] alphabetCouplesMatrix) {
		int alphabetLength = alphabet.length();
		double[][] logProbabilityMatrix = new double[alphabetLength][alphabetLength];
		for (int i = 0; i < alphabetCouplesMatrix.length; i++) {
			double sum = getSum(alphabetCouplesMatrix[i]); 
			for (int j = 0; j < alphabetCouplesMatrix[i].length; j++) {				
				logProbabilityMatrix[i][j] = Math.log(alphabetCouplesMatrix[i][j]/sum);
			}
		}
		return logProbabilityMatrix;
	}
	
	private List<Double> getAvgTransitionProbability(List<String> lines, double[][] logProbabilityMatrix) {		
		List<Double> result = new ArrayList<Double>();		
		for (String line : lines) {
			result.add(getAvgTransitionProbability(line, logProbabilityMatrix));
		}
		return result;
	}
	
	private double getAvgTransitionProbability(String line, double[][] logProbabilityMatrix) {
		double logProb = 0d;
		int transitionCount = 0;
		List<String> nGram = getNGram(2, line);
		for (String touple : nGram) {
			logProb += logProbabilityMatrix[alphabetPositionMap.get(touple.charAt(0))][alphabetPositionMap.get(touple.charAt(1))];
			transitionCount++;
		}
		return Math.exp(logProb / Math.max(transitionCount, 1));
	}
		
	private int[][] createArray(int length){
		int[][] counts = new int[length][length];
	    for (int i = 0; i < counts.length; i++){
	        Arrays.fill(counts[i], MIN_COUNT_VAL);
	    }
		return counts;
	}
	
	private double getSum(int[] array) {
		double sum = 0; 
		for (int i = 0; i < array.length; i++) {
			sum += array[i];
		}
		return sum; 
	}

	/**
	 * determines if a document is gibberish or not.
	 * @param doc a document to be classified as gibberish or not.
	 * @return the line-by-line percentage of the document containing gibberish.
	 */
	public double getPercentGibberish(String doc) {
		int docSize = doc.length();
		double minConfidence = threshold / docSize;
		String[] sentences = recognizer.detectSentences(doc);

		int totalLines = sentences.length;
		int gibberishLines = 0;
		if (sentences.length > 1) {
			for (String sentence : sentences) {
				String line = sentence.replaceAll(" ", ""); //remove all whitespace
				double degreeOfConfidence = getAvgTransitionProbability(line, logProbabilityMatrix) - threshold;
				double confidenceScore = degreeOfConfidence / line.length();
				if (confidenceScore < minConfidence) {
					++gibberishLines;
				}
			}
		} else if (sentences.length > 0) {
			if (isLineGibberish(sentences[0])) {
				++gibberishLines;
			}
		}

		double percentGibberish = (double)gibberishLines / (double)totalLines;

		return percentGibberish;
	}

	public String removeGibberishLines(String doc) {
		int docSize = doc.length();
		double minConfidence = threshold / docSize;
		String[] sentences = recognizer.detectSentences(doc);

		List<String> notGibberish = new ArrayList<>();
		if (sentences.length > 1) {
			for (String sentence : sentences) {
				String line = sentence.replaceAll(" ", ""); //remove all whitespace
				double degreeOfConfidence = getAvgTransitionProbability(line, logProbabilityMatrix) - threshold;
				double confidenceScore = degreeOfConfidence / line.length();
				if (confidenceScore < minConfidence) {
					notGibberish.add(sentence);
				}
			}
		} else if (sentences.length > 0) {
			if (!isLineGibberish(sentences[0])) {
				notGibberish.add(sentences[0]);
			}
		}

		String gibberishRemoved = String.join(System.lineSeparator(), notGibberish);

		return gibberishRemoved;
	}

	private boolean isLineGibberish(String line) {
		return !(getAvgTransitionProbability(line, logProbabilityMatrix) > threshold);
	}
}