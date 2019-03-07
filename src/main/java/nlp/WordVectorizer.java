package nlp;

import org.apache.commons.io.FileUtils;
import org.deeplearning4j.clustering.cluster.Cluster;
import org.deeplearning4j.clustering.cluster.ClusterSet;
import org.deeplearning4j.clustering.cluster.Point;
import org.deeplearning4j.clustering.cluster.PointClassification;
import org.deeplearning4j.clustering.kmeans.KMeansClustering;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.sentenceiterator.LineSentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import solrapi.SolrClient;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WordVectorizer {

    final static Logger logger = LogManager.getLogger(WordVectorizer.class);

    private SolrClient client;

    public static void main(String[] args) {
        WordVectorizer vectorizer = new WordVectorizer(new SolrClient("http://localhost:8983/solr"));
        //vectorizer.trainModel("data/wordVectors.txt", "Water");
        //vectorizer.evaluateModel("data/wordVectors.txt");

        try {
            vectorizer.generateWordClusterDictionary("Water", 1);
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

        client.writeCorpusDataToFile(trainingFilePath, category, client.getCategorySpecificNERModelTestingDataQuery(category), client::formatForWord2VecModelTraining, new SolrClient.NERThrottle());

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
                .minWordFrequency(5)
                .layerSize(100)
                .seed(42)
                .windowSize(5)
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

    private ClusterSet generateKMeansCluster(Word2Vec vec) {
        //iterate over rows in the wordvector and create a List of word vectors
        List<INDArray> vectors = new ArrayList<>();
        for (String word : vec.vocab().words()) {
            vectors.add(vec.getWordVectorMatrix(word));
        }
        logger.info(vectors.size() + " vectors extracted to create Point list");
        List<Point> pointsLst = Point.toPoints(vectors);
        logger.info(pointsLst.size() + " Points created out of " + vectors.size() + " vectors");

        //create a kmeanscluster instance
        int maxIterationCount = 5;
        String distanceFunction = "cosinesimilarity";
        int maxClusterCount = 20;
        int clusterCount = 1;
        Map<Integer, Double> sumSquaredErrors = new HashMap<>();
        Map<Integer, Double> backDiffs = new HashMap<>();
        Map<Integer, ClusterSet> clusterSets = new HashMap<>();
        for (; clusterCount <= maxClusterCount; clusterCount++) {
            logger.info("optimizing cluster count: " + clusterCount);
            sumSquaredErrors.put(clusterCount, 0d);
            KMeansClustering kmc = KMeansClustering.setup(clusterCount, maxIterationCount, distanceFunction);
            ClusterSet cs = kmc.applyTo(pointsLst);
            clusterSets.put(clusterCount, cs);
            for (Cluster cluster : cs.getClusters()) {
                Point center = cluster.getCenter();
                for (Point point : cluster.getPoints()) {
                    double squaredError = Math.pow(point.getArray().squaredDistance(center.getArray()), 2);
                    double sse = sumSquaredErrors.get(clusterCount) + squaredError;
                    sumSquaredErrors.replace(clusterCount, sse);
                }
            }
            if (clusterCount > 1) {
                double backDiff = calculateBackwardDifference(clusterCount, sumSquaredErrors);
                backDiffs.put(clusterCount, backDiff);
            }
            if (clusterCount > 2) {
                int optimalCluster = findOptimalClusterNum(backDiffs);
                if (optimalCluster != clusterCount) {
                    return clusterSets.get(optimalCluster);
                }
            }
        }

        int optimalCluster = findOptimalClusterNum(backDiffs);

        return clusterSets.get(optimalCluster);
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

    public void generateWordClusterDictionary(String category, int numAttempts) throws IOException {
        String clusterFilePath = getClusterFilePath(category);
        String modelFilePath = getModelFilePath(category);

        Word2Vec vec;
        try {
            vec = WordVectorSerializer.readWord2VecModel(modelFilePath);
        } catch (ND4JIllegalStateException e) {
            if (numAttempts < 2) {
                trainModel(category);
                generateWordClusterDictionary(category, ++numAttempts);
                return;
            } else {
                throw new IOException("Unable to read word vector model!");
            }
        }
        ClusterSet cs = generateKMeansCluster(vec);

        StringBuilder dict = new StringBuilder();
        for (String word : vec.vocab().words()) {
            Point testPoint = new Point("testId", word, vec.getWordVector(word));
            PointClassification pc = cs.classifyPoint(testPoint);
            String clusterId = pc.getCluster().getId();
            dict.append(word + " " + clusterId);
            dict.append(System.lineSeparator());
        }

        FileUtils.writeStringToFile(new File(clusterFilePath), dict.toString(), StandardCharsets.UTF_8);
    }

    public void evaluateModel(String modelPath) {
        Word2Vec vec = WordVectorSerializer.readWord2VecModel(modelPath);
        vec.wordsNearest("water", 5);
    }

    public String getClusterFilePath(String category) {
        String modelFile = "data/ner/" + category + "/cluster.txt";
        return modelFile;
    }

    private String getModelFilePath(String category) {
        String modelFile = "data/ner/" + category + "/w2v.txt";
        return modelFile;
    }

    private String getTrainingFilePath(String category) {
        String trainingFile = "data/ner/" + category + "/w2v.train";
        return trainingFile;
    }
}
