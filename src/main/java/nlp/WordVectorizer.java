package nlp;

import common.Tools;
import org.apache.commons.io.FileUtils;
import org.deeplearning4j.clustering.cluster.ClusterSet;
import org.deeplearning4j.clustering.cluster.Point;
import org.deeplearning4j.clustering.kmeans.KMeansClustering;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.plot.BarnesHutTsne;
import org.deeplearning4j.text.sentenceiterator.LineSentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.stopwords.StopWords;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import smile.clustering.HierarchicalClustering;
import smile.clustering.XMeans;
import smile.clustering.linkage.CompleteLinkage;
import smile.math.Math;
import solrapi.SolrClient;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WordVectorizer {

    final static Logger logger = LogManager.getLogger(WordVectorizer.class);

    private static String wordVectorsPath = Tools.getProperty("wordVectors.location");

    private SolrClient client;

    public static void main(String[] args) {
        WordVectorizer vectorizer = new WordVectorizer(new SolrClient("http://localhost:8983/solr"));
        //vectorizer.trainModel("Water");
        //vectorizer.evaluateModel("Water");

        try {
            vectorizer.generateWordClusterDictionary("Water");
        } catch (IOException e) {
            e.printStackTrace();
        }

//        Word2Vec vec = WordVectorSerializer.readWord2VecModel("data/wordVectors.txt");
//        ClusterSet cs = vectorizer.generateKMeansCluster(vec);
//
//        for (String word : vec.vocab().words()) {
//            Point testPoint = new Point("testId", word, vec.getWordVector(word));
//            PointClassification pc = cs.classifyPoint(testPoint);
//            String clusterId = pc.getCluster().getId();
//            System.out.println(word + " " + clusterId);
//        }

//        Map<String, TreeMap<Double, String>> clusterRankings = new HashMap<>();
//
//        for (String word : vec.vocab().words()) {
//            Point testPoint = new Point("testId", word, vec.getWordVector(word));
//            PointClassification pc = cs.classifyPoint(testPoint);
//            String clusterId = pc.getCluster().getId();
//            if (!clusterRankings.containsKey(clusterId)) {
//                clusterRankings.put(clusterId, new TreeMap<>());
//            }
//            clusterRankings.get(clusterId).put(Math.abs(pc.getDistanceFromCenter()), word);
//        }
//
//        for (String clusterId : clusterRankings.keySet()) {
//            TreeMap<Double, String> terms = clusterRankings.get(clusterId);
//            System.out.println("Cluster Size: " + terms.size());
//            System.out.print("Top Cluster Terms: ");
//            for (Double distance : terms.keySet()) {
//                System.out.print(terms.get(distance) + ", ");
//            }
//            System.out.println("");
//        }

//        Point testPoint = new Point("testId", "testLabel", vec.getWordVector("minerals"));
//        PointClassification pc = cs.classifyPoint(testPoint);
//        for (Point point : pc.getCluster().getPoints()) {
//            System.out.println(point.getId() + ": " + pc.getCluster().getDistanceToCenter(point));
//        }
    }

    public WordVectorizer(SolrClient client) {
        this.client = client;
    }

    public void trainModel(String category) {
        String modelFilePath = getModelFilePath(category);
        String trainingFilePath = getTrainingFilePath(category);

        client.writeCorpusDataToFile(trainingFilePath, category, client.getCategorySpecificNERModelTrainingDataQuery(category), client::formatForWord2VecModelTraining, new SolrClient.NERThrottle());

        SentenceIterator iter = new LineSentenceIterator(new File(trainingFilePath));

        iter.setPreProcessor(new SentencePreProcessor() {
            @Override
            public String preProcess(String sentence) {
                return sentence.toLowerCase();
            }
        });

        // Split on white spaces in the line to get words
        TokenizerFactory t = new DefaultTokenizerFactory();
        t.setTokenPreProcessor(new CommonPreprocessor());

        logger.info("Building model....");
        Word2Vec vec = new Word2Vec.Builder()
                .minWordFrequency(200)
                .layerSize(100)
                .seed(42)
                .windowSize(20)
                .iterate(iter)
                .tokenizerFactory(t)
                .build();

        logger.info("Fitting Word2Vec model....");
        vec.fit();

        try {
            // Write word vectors
            WordVectorSerializer.writeWordVectors(vec, modelFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void evaluateModel(String category) {
        try {
            //STEP 1: Initialization
            int iterations = 100;
            String modelFilePath = getModelFilePath(category);
            String tsneFilePath = getTSNEFilePath(category);
            //create an n-dimensional array of doubles
            Nd4j.setDataType(DataBuffer.Type.DOUBLE);
            List<String> cacheList = new ArrayList<>(); //cacheList is a dynamic array of strings used to hold all words

            //STEP 2: Turn text input into a list of words
            logger.info("Load & Vectorize data....");
            //Get the data of all unique word vectors
            Pair<InMemoryLookupTable,VocabCache> vectors = WordVectorSerializer.loadTxt(new File(modelFilePath));
            VocabCache cache = vectors.getSecond();
            INDArray weights = vectors.getFirst().getSyn0();    //seperate weights of unique words into their own list

            for(int i = 0; i < cache.numWords(); i++)   //seperate strings of words into their own list
                cacheList.add(cache.wordAtIndex(i));

            //STEP 3: build a dual-tree tsne to use later
            logger.info("Build model....");
            BarnesHutTsne tsne = new BarnesHutTsne.Builder()
                    .setMaxIter(iterations).theta(0.5)
                    .normalize(false)
                    .learningRate(500)
                    .useAdaGrad(false)
    //                .usePca(false)
                    .build();

            //STEP 4: establish the tsne values and save them to a file
            logger.info("Store TSNE Coordinates for Plotting....");
            (new File(tsneFilePath)).getParentFile().mkdirs();

            tsne.fit(weights);
            tsne.saveAsFile(cacheList, tsneFilePath);
            //This tsne will use the weights of the vectors as its matrix, have two dimensions, use the words strings as
            //labels, and be written to the outputFile created on the previous line
            // Plot Data with gnuplot
            //    set datafile separator ","
            //    plot 'tsne-standard-coords.csv' using 1:2:3 with labels font "Times,8"
            //!!! Possible error: plot was recently deprecated. Might need to re-do the last line
            //
            // If you use nDims=3 in the call to tsne.plot above, you can use the following gnuplot commands to
            // generate a 3d visualization of the word vectors:
            //    set datafile separator ","
            //    splot 'tsne-standard-coords.csv' using 1:2:3:4 with labels font "Times,8"
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private ClusterSet generateKMeansCluster(INDArray matrix) {
        //iterate over rows in the wordvector and create a List of word vectors
        logger.info(matrix.size(0) + " vectors extracted to create Point list");
        List<Point> pointsLst = Point.toPoints(matrix);
        logger.info(pointsLst.size() + " Points created out of " + matrix.size(0) + " vectors");

        //create a kmeanscluster instance
        int maxIterationCount = 5;
        String distanceFunction = "cosinesimilarity";
        int K = (int)Math.round(pointsLst.size() * 0.001); //number of clusters is 0.1% of the total number of word vectors
        KMeansClustering kmc = KMeansClustering.setup(K, maxIterationCount, distanceFunction);
        ClusterSet cs = kmc.applyTo(pointsLst);
        return cs;
//        int maxClusterCount = 100;
//        int clusterCount = 1;
//        Map<Integer, Double> sumSquaredErrors = new HashMap<>();
//        Map<Integer, Double> backDiffs = new HashMap<>();
//        Map<Integer, ClusterSet> clusterSets = new HashMap<>();
//        for (; clusterCount <= maxClusterCount; clusterCount++) {
//            logger.info("optimizing cluster count: " + clusterCount);
//            sumSquaredErrors.put(clusterCount, 0d);
//            KMeansClustering kmc = KMeansClustering.setup(clusterCount, maxIterationCount, distanceFunction);
//            ClusterSet cs = kmc.applyTo(pointsLst);
//            clusterSets.put(clusterCount, cs);
//            for (Cluster cluster : cs.getClusters()) {
//                Point center = cluster.getCenter();
//                for (Point point : cluster.getPoints()) {
//                    double squaredError = Math.pow(point.getArray().squaredDistance(center.getArray()), 2);
//                    double sse = sumSquaredErrors.get(clusterCount) + squaredError;
//                    sumSquaredErrors.replace(clusterCount, sse);
//                }
//            }
//            if (clusterCount > 1) {
//                double backDiff = calculateBackwardDifference(clusterCount, sumSquaredErrors);
//                backDiffs.put(clusterCount, backDiff);
//            }
//            if (clusterCount > 2) {
//                int optimalCluster = findOptimalClusterNum(backDiffs);
//                if (optimalCluster != clusterCount) {
//                    return clusterSets.get(optimalCluster);
//                }
//            }
//        }
//
//        int optimalCluster = findOptimalClusterNum(backDiffs);
//
//        return clusterSets.get(optimalCluster);
    }

    //using "elbow method" for finding best number of clusters
    //https://bl.ocks.org/rpgove/0060ff3b656618e9136b
    private int findOptimalClusterNum(Map<Integer, Double> backDiffs) {
        final double slopeThreshold = 30; //max positive slope before ending search
        double prevDiff = 0d;
        List<Double> concavity = new ArrayList<>();
        int currentCluster = 0;
        for (int clusterNum : backDiffs.keySet()) {
            currentCluster = clusterNum;
            double diff = backDiffs.get(clusterNum); //slope
            if (prevDiff != 0) {
                if (diff > slopeThreshold) { //excessively positive slope! end search
                    return clusterNum - 1;
                }
                double diff2 = diff - prevDiff; //concavity
                //if concave up then rate of convergence is decreasing
                concavity.add(diff2);
                if (concavity.size() > 2) {
                    //check concavity for the last few iterations to see if it's time to stop
                    double localConcavity = 1;
                    for (int i = concavity.size() - 1; i >= concavity.size() - 3; i--) {
                        localConcavity *= concavity.get(i);
                    }
                    //if the last three iterations have produced positive concavity then go ahead and halt
                    if (localConcavity > 0) {
                        return clusterNum - 1;
                    }
                }
            }
            prevDiff = diff;
        }
        return currentCluster;
    }

    private double calculateBackwardDifference(int clusterNum, Map<Integer, Double> sumSquaredErrors) {
        double fx = sumSquaredErrors.get(clusterNum);
        double fxmh = sumSquaredErrors.get(clusterNum - 1);
        double backwardDiff = fx - fxmh;
        return backwardDiff;
    }

    public void generateWordClusterDictionary(String category) throws IOException {
        String xmeansClusterFilePath = getXMeansClusterFilePath(category);
        String brownClusterFilePath = getBrownClusterFilePath(category);
        String trainingFilePath = getTrainingFilePath(category);
        client.writeCorpusDataToFile(trainingFilePath, category, client.getCategorySpecificNERModelTrainingDataQuery(category), client::formatForWord2VecModelTraining, new SolrClient.NERThrottle());

        Word2Vec vec;
        try {
            //load the Google word vectors model
            vec = WordVectorSerializer.readWord2VecModel(wordVectorsPath);
        } catch (ND4JIllegalStateException e) {
            throw new IOException("Unable to read Google word vector model!");
        }

        String corpus = FileUtils.readFileToString(new File(trainingFilePath), Charset.forName("utf-8"));

        String numberless = Tools.removeAllNumbers(corpus);
        String cleaned = Tools.removeSpecialCharacters(numberless);

        String[] allWords = NLPTools.detectTokens(cleaned);

        List<String> stopWords = StopWords.getStopWords();
        TreeSet<String> stopWordsTree = new TreeSet<>(stopWords);
        TreeMap<String, Integer> uniqueWords = new TreeMap<>();
        for (String word : allWords) {
            if (!stopWordsTree.contains(word)) {
                if (!uniqueWords.containsKey(word)) {
                    uniqueWords.put(word, 1);
                } else {
                    int count = uniqueWords.get(word);
                    uniqueWords.replace(word, ++count);
                }
            }
        }

        outputXMeansClusters(xmeansClusterFilePath, vec, uniqueWords);
        outputBrownClusters(brownClusterFilePath, vec, uniqueWords);
    }

    private void outputXMeansClusters(String clusterFilePath, Word2Vec vec, TreeMap<String, Integer> uniqueWords) throws IOException {
        INDArray matrix = vec.getWordVectors(uniqueWords.keySet());
        double[][] weights = matrix.toDoubleMatrix();
        XMeans xmeans = new XMeans(weights, 100);
        VocabCache<VocabWord> vocab = vec.getVocab();
        StringBuilder dict = new StringBuilder();
        for (String word : uniqueWords.keySet()) {
            if (vocab.containsWord(word)) {
                double[] wordvec = vec.getWordVector(word);
                int label = xmeans.predict(wordvec);
                dict.append(word + " " + label);
                dict.append(System.lineSeparator());
            }
        }

        FileUtils.writeStringToFile(new File(clusterFilePath), dict.toString(), StandardCharsets.UTF_8);
    }

    private void outputBrownClusters(String clusterFilePath, Word2Vec vec, TreeMap<String, Integer> uniqueWords) throws IOException {
        VocabCache<VocabWord> vocab = vec.getVocab();
        TreeMap<String, double[]> wordVectors = new TreeMap<>();
        for (String word : uniqueWords.keySet()) {
            if (vocab.containsWord(word)) {
                double[] vector = vec.getWordVector(word);
                wordVectors.put(word, vector);
            }
        }
        double[][] weights = wordVectors.values().toArray(new double[wordVectors.size()][]);
        double[][] proximity = getProximityMatrix(weights);
        CompleteLinkage linkage = new CompleteLinkage(proximity);
        HierarchicalClustering clustering = new HierarchicalClustering(linkage);
        int[] labels = clustering.partition(100);

//        PlotCanvas canvas = Dendrogram.plot(clustering.getTree(), clustering.getHeight());
//        JFrame frame = new JFrame();
//        frame.add(canvas);

        String[] words = wordVectors.keySet().toArray(new String[wordVectors.size()]);
        StringBuilder dict = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            //form binary representation of label
            String label = Integer.toBinaryString(labels[i]);
            String word = words[i];
            int occurrences = uniqueWords.get(word);
            dict.append(label);
            dict.append("\t");
            dict.append(word);
            dict.append("\t");
            dict.append(occurrences);
            dict.append(System.lineSeparator());
        }

        FileUtils.writeStringToFile(new File(clusterFilePath), dict.toString(), StandardCharsets.UTF_8);
    }

    private double[][] getProximityMatrix(double[][] matrix) {
        int n = matrix.length;

        double[][] proximity = new double[n][];
        for (int i = 0; i < n; i++) {
            proximity[i] = new double[i + 1];
            for (int j = 0; j < i; j++) {
                proximity[i][j] = Math.distance(matrix[i], matrix[j]);
            }
        }

        return proximity;
    }

    public String getXMeansClusterFilePath(String category) {
        String modelFile = "data/ner/" + category + "/xmeans-clusters.txt";
        return modelFile;
    }

    public String getBrownClusterFilePath(String category) {
        String modelFile = "data/ner/" + category + "/brown-clusters.txt";
        return modelFile;
    }

    private String getModelFilePath(String category) {
        String modelFile = "data/ner/" + category + "/w2v.txt";
        return modelFile;
    }

    private String getTSNEFilePath(String category) {
        String modelFile = "data/ner/" + category + "/tsne-standard-coords.csv";
        return modelFile;
    }

    private String getTrainingFilePath(String category) {
        String trainingFile = "data/ner/" + category + "/w2v.train";
        return trainingFile;
    }
}
