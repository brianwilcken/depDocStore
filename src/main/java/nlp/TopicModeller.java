package nlp;

import cc.mallet.pipe.CharSequenceLowercase;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.types.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.topics.*;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.google.common.collect.Lists;
import common.Tools;
import edu.stanford.nlp.util.CoreMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.core.io.ClassPathResource;
import solrapi.SolrClient;
import sun.awt.Mutex;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.util.stream.Collectors;

public class TopicModeller {
    private SolrClient client;
    private Mutex mutex;

    final static Logger logger = LogManager.getLogger(TopicModeller.class);

    private static ParallelTopicModel model;
    private static InstanceList instanceList;
    private static SerialPipes pipe;
    private static TopicInferencer inferencer;
    private static ArrayList<TreeSet<IDSorter>> topicSortedWords;
    private static Alphabet dataAlphabet;
    private static TreeSet<String> multiGrams;
    private static Map<String,TopicCategoryMapping> topicsToCategories;

    static {
        try {
            model = new ParallelTopicModel(50);
            instanceList = InstanceList.load(new File(Tools.getProperty("mallet.corpus")));
            pipe = (SerialPipes)instanceList.getPipe();
            model.addInstances(instanceList);
            model.initializeFromState(new File(Tools.getProperty("mallet.state")));
            inferencer = model.getInferencer();
            topicSortedWords = model.getSortedWords();
            dataAlphabet = model.getAlphabet();

            multiGrams = new TreeSet<>();
            Iterator it = dataAlphabet.iterator();
            while(it.hasNext()) {
                String term = it.next().toString();
                if (term.contains("_")) {
                    multiGrams.add(term);
                }
            }

            //initialize token pipeline
            ArrayList<Pipe> pipeList = pipe.pipes();
            pipeList.add(1, new CharSequenceLowercase());
            int size = pipeList.size();
            pipeList.add(size - 1, new TokenSequence2PorterStems());
            size = pipeList.size();
            pipeList.add(size - 1, new TokenSequenceNGrams(new int[] {1, 2, 3}, multiGrams));

            MappingIterator<TopicCategoryMapping> topicIter = new CsvMapper()
                    .readerWithTypedSchemaFor(TopicCategoryMapping.class)
                    .readValues(new File(Tools.getProperty("mallet.topicKeysToCategories")));
            topicsToCategories = new HashMap<>();
            while(topicIter.hasNext()) {
                TopicCategoryMapping mapping = topicIter.next();
                topicsToCategories.put(mapping.topic, mapping);
            }

        } catch (IOException e) {
            model = null;
        }
    }

    public static void main(String[] args) throws Exception {
        SolrClient client = new SolrClient("http://134.20.2.51:8983/solr");
        TopicModeller topicModeller = new TopicModeller(client);

        topicModeller.writeTopicVectorRepresentation(20);

//        //SolrDocumentList docs = client.QuerySolrDocuments("id:verdictmedia_66610", 1, 0, null, null);
//        SolrDocumentList docs = client.QuerySolrDocuments("id:dfe996c4c886449964067f5dcbb4e8549e3b8e80", 1, 0, null, null);
//        SolrDocument doc = docs.get(0);
//        String parsed = doc.get("parsed").toString();
//        List<String> docCategories = topicModeller.inferCategoriesByTopics(parsed);
//        System.out.println("Overall categories: " + docCategories.stream().reduce((c, n) -> c + ", " + n).orElse(""));
//
//        List<TextChunkTopic> textChunkTopics = topicModeller.getTextChunkTopics(parsed, 10);
//        for (TextChunkTopic textChunkTopic : textChunkTopics) {
//            System.out.println(textChunkTopic.toString());
//        }
    }

    public TopicModeller(SolrClient client) {
        this.client = client;
        client.setTopicModeller(this);
        mutex = new Mutex();
    }

    public List<TextChunkTopic> getTextChunkTopics(String text, int chunkSize) {
        List<CoreMap> sentences = NLPTools.detectSentencesStanford(text);
        List<List<CoreMap>> chunks = Lists.partition(sentences, chunkSize);

        List<TextChunkTopic> textChunkTopics = new ArrayList<>();
        int start = 1;
        for (List<CoreMap> chunk : chunks) {
            List<String> chunkSentences = chunk.stream().map(p -> p.toString()).collect(Collectors.toList());
            String chunkText = StringUtils.join(chunkSentences, "\r\n");
            List<String> categories = inferCategoriesByTopics(chunkText);
            int end = start + (chunkSize - 1);
            TextChunkTopic textChunkTopic = new TextChunkTopic(start, end, chunkText, categories);
            textChunkTopics.add(textChunkTopic);

            start += chunkSize;
        }

        return textChunkTopics;
    }

    public List<String> inferCategoriesByTopics(String text) {
        try {
            mutex.lock();
            Instance testInstance;
            try {
                testInstance = pipe.instanceFrom(new Instance(text, null, "test instance", null));
            } finally {
                mutex.unlock();
            }

            double[] topicDistribution = inferencer.getSampledDistribution(testInstance, 10, 1, 5);

            String category = resolveCategoryByTopics(topicDistribution);
            List<String> categories = Lists.newArrayList(category.split(";"));
            return categories;
        } catch (Exception e) {
            return null;
        }
    }

    private String getTopicText(int topic, int maxRank) {
        Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();
        int rank = 0;
        Formatter topicOut = new Formatter(new StringBuilder(), Locale.US);
        while (iterator.hasNext() && rank < maxRank) {
            IDSorter idCountPair = iterator.next();
            topicOut.format("%s ", dataAlphabet.lookupObject(idCountPair.getID()));
            rank++;
        }
        return topicOut.toString().trim();
    }

    private void writeTopicVectorRepresentation(int maxRank) {
        File output = new File("./topicVectors.csv");
        try (FileOutputStream fos = new FileOutputStream(output);
            OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)){

            for (int topic = 0; topic < model.numTopics; topic++) {
                double allCounts = topicSortedWords.get(topic).stream().mapToDouble(p -> p.getWeight()).sum();
                Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();
                int rank = 0;
                writer.write(topic);
                while (iterator.hasNext() && rank < maxRank) {
                    IDSorter idCountPair = iterator.next();
                    String word = (String) dataAlphabet.lookupObject(idCountPair.getID());
                    Double weight = idCountPair.getWeight();
                    String normalized = String.format("%.3f",weight / allCounts);
                    writer.write(word + "," + normalized);
                    writer.write(System.lineSeparator());
                    rank++;
                }
                writer.write(System.lineSeparator());
                writer.write(System.lineSeparator());
            }
            writer.flush();
            writer.close();
        } catch (Exception e) {

        }
    }

    private String resolveCategoryByTopics(double[] topicDistribution) {
        List<CategoryWeight> categoryWeights = new ArrayList<>();
        for (int i = 0; i < topicDistribution.length; i++) {
            String topicText = getTopicText(i, 20);
            TopicCategoryMapping mapping = topicsToCategories.get(topicText);
            double probability = topicDistribution[i];
            mapping.updateCategoryWeight(categoryWeights, probability);
        }
        //normalize category weights
        double weightSum = categoryWeights.stream().map(p -> p.catWeight).mapToDouble(Double::doubleValue).sum();
        String category;
        if (weightSum > 0.3) {
//            categoryWeights.stream().forEach(p -> {
//                p.catWeight = p.catWeight / weightSum;
//            });
            //sort to get best category
            categoryWeights.sort(new Comparator<CategoryWeight>() {
                @Override
                public int compare(CategoryWeight c1, CategoryWeight c2) {
                    return Double.compare(c2.catWeight, c1.catWeight); //descending sort
                }
            });

            double[] weights = categoryWeights.stream().map(p -> p.catWeight).mapToDouble(Double::doubleValue).toArray();
            StandardDeviation standardDev = new StandardDeviation();
            final double stdDev = standardDev.evaluate(weights);
            final double maxWeight = weights[0];
            categoryWeights = categoryWeights.stream()
                    .filter(p -> !p.category.equals("Not_Applicable") && p.catWeight >= (maxWeight - (0.5 * stdDev))).collect(Collectors.toList());
            if (categoryWeights.size() <= 3 && maxWeight >= 0.2) {
                category = categoryWeights.stream().map(p -> p.category + "|" + p.catWeight)
                        .reduce((c, n) -> c + ";" + n).orElse("");
            } else {
                category = "Not_Applicable|" + (1 - weightSum);
            }
        } else {
            category = "Not_Applicable|" + (1 - weightSum);
        }

        return category;
    }

//    //old version
//    private String resolveCategoryByTopics(double[] topicDistribution) {
//        List<CategoryWeight> categoryWeights = new ArrayList<>();
//        for (int i = 0; i < topicDistribution.length; i++) {
//            String topicText = getTopicText(i, 20);
//            TopicCategoryMapping mapping = topicsToCategories.get(topicText);
//            double probability = topicDistribution[i];
//            mapping.updateCategoryWeight(categoryWeights, probability);
//        }
//        //normalize category weights
//        double weightSum = categoryWeights.stream().map(p -> p.catWeight).mapToDouble(Double::doubleValue).sum();
//        categoryWeights.stream().forEach(p -> {
//            p.catWeight = p.catWeight / weightSum;
//        });
//        //sort to get best category
//        categoryWeights.sort(new Comparator<CategoryWeight>() {
//            @Override
//            public int compare(CategoryWeight c1, CategoryWeight c2) {
//                return Double.compare(c2.catWeight, c1.catWeight); //descending sort
//            }
//        });
//
//        double[] weights = categoryWeights.stream().map(p -> p.catWeight).mapToDouble(Double::doubleValue).toArray();
//        StandardDeviation standardDev = new StandardDeviation();
//        final double stdDev = standardDev.evaluate(weights);
//        final double maxWeight = weights[0];
//        String category;
//        if (categoryWeights.get(0).category.equals("Not_Applicable")) {
//            final double closeEnough = stdDev * 0.125; // 1/8th standard deviation from the max
//            double secondBest = weights[1];
//            if (secondBest >= (maxWeight - closeEnough)) {
//                category = categoryWeights.stream()
//                        .filter(p -> !p.category.equals("Not_Applicable") && p.catWeight >= (maxWeight - closeEnough))
//                        .map(p -> p.category + "|" + p.catWeight)
//                        .reduce((c, n) -> c + ";" + n).orElse("");
//            } else {
//                category = categoryWeights.get(0).category + "|" + categoryWeights.get(0).catWeight;
//            }
//        } else {
//            category = categoryWeights.stream()
//                    .filter(p -> !p.category.equals("Not_Applicable") && p.catWeight >= (maxWeight - (2 * stdDev)))
//                    .map(p -> p.category + "|" + p.catWeight)
//                    .reduce((c, n) -> c + ";" + n).orElse("");
//
//        }
//
//        return category;
//    }



//    public void addTopicsToDocuments() throws Exception {
//        String dataFilePath = "data/topic-modeling.data";
//        client.writeCorpusDataToFile(dataFilePath, null, null, client::getAllDocumentsDataQuery, client::formatForTopicModeling, new SolrClient.NERThrottle());
//
//        InstanceList instances = new InstanceList (getPreprocessPipeline());
//
//        Reader fileReader = new InputStreamReader(new FileInputStream(new File(dataFilePath)), "UTF-8");
//        instances.addThruPipe(new CsvIterator (fileReader, Pattern.compile("^(\\S*)[\\s,]*(\\S*)[\\s,]*(.*)$"), 3, 2, 1)); // data, label, name fields
//
//        //double[][] topicVectors = new double[instances.size()][model.numTopics];
//
//        Formatter out = new Formatter(new StringBuilder(), Locale.US);
//        for (int i = 0; i < instances.size(); i++) {
//            Instance instance = instances.get(i);
//            String docName = instance.getName().toString();
//            String[] nameAndIdTokens = getNameAndIdTokens(docName);
//            String id = nameAndIdTokens[0];
//            docName = nameAndIdTokens[1];
//
//            double[] topicDistribution = inferencer.getSampledDistribution(instance, 10, 1, 5);
//            //topicVectors[i] = topicDistribution;
//            int[] orderedTopics = Tools.argsort(topicDistribution, false);
//            double prob1 = topicDistribution[orderedTopics[0]];
//            String topicText1 = getTopicText(topicSortedWords, dataAlphabet, orderedTopics[0], 5);
//            double prob2 = topicDistribution[orderedTopics[1]];
//            String topicText2 = getTopicText(topicSortedWords, dataAlphabet, orderedTopics[1], 5);
//            double prob3 = topicDistribution[orderedTopics[2]];
//            String topicText3 = getTopicText(topicSortedWords, dataAlphabet, orderedTopics[2], 5);
//            String category = resolveCategoryByTopics(topicSortedWords, dataAlphabet, topicDistribution);
//            out.format("%s,%s,%s,%.3f,%s,%.3f,%s,%.3f,%s\r\n", id, docName, category, prob1, topicText1, prob2, topicText2, prob3, topicText3);
//        }
//
//        FileUtils.writeStringToFile(new File(getDocumentsWithTopicsPath()), out.toString(), Charset.forName("Cp1252").displayName());
//    }
//
//    private String[] getNameAndIdTokens(String docName) {
//        String[] nameTokens = docName.split(",");
//        int tokenLimit = nameTokens.length > 2 ? nameTokens.length - 1 : nameTokens.length;
//        String docNameReduced = Arrays.stream(nameTokens).limit(tokenLimit).reduce((c,n) -> c + "," + n).orElse("ERROR");
//        String[] nameAndIdTokens = docNameReduced.split("\\|");
//
//        return nameAndIdTokens;
//    }
//
//    public void trainLDAModel() throws Exception {
//        String trainingFilePath = "data/topic-modeling.train";
//        client.writeCorpusDataToFile(trainingFilePath, null,null, client::getAllDocumentsDataQuery, client::formatForTopicModeling, new SolrClient.NERThrottle());
//
//        Pipe pipeline = getPreprocessPipeline();
//
//        InstanceList instances = new InstanceList (pipeline);
//
//        Reader fileReader = new InputStreamReader(new FileInputStream(new File(trainingFilePath)), "UTF-8");
//        instances.addThruPipe(new CsvIterator (fileReader, Pattern.compile("^(\\S*)[\\s,]*(\\S*)[\\s,]*(.*)$"), 3, 2, 1)); // data, label, name fields
//
//        // Create a model with 100 topics, alpha_t = 0.01, beta_w = 0.01
//        //  Note that the first parameter is passed as the sum over topics, while
//        //  the second is the parameter for a single dimension of the Dirichlet prior.
//        int numTopics = 100;
//        ParallelTopicModel model = new ParallelTopicModel(numTopics, 1.0, 0.01);
//
//        model.addInstances(instances);
//
//        model.setNumThreads(8);
//
//        // Run the model for 50 iterations and stop (this is for testing only,
//        //  for real applications, use 1000 to 2000 iterations)
//        model.setNumIterations(1000);
//        model.estimate();
//        model.write(new File(getModelFilePath()));
//    }
//
//    public String getModelFilePath() {
//        return "data/topic-model.mallet";
//    }
//
//    public String getDocumentsWithTopicsPath() {
//        return "data/docs-with-topics.csv";
//    }
}
