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
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.core.io.ClassPathResource;
import solrapi.SolrClient;

import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.*;
import java.io.*;

public class TopicModeller {
    private SolrClient client;

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
        SolrClient client = new SolrClient("http://localhost:8983/solr");
        TopicModeller topicModeller = new TopicModeller(client);

        //topicModeller.addTopicsToDocuments();

        SolrDocumentList docs = client.QuerySolrDocuments("id:3e65efda4c406a777e334379f5b0d6805c812d7d", 1, 0, null, null);
        SolrDocument doc = docs.get(0);
        String parsed = doc.get("parsed").toString();
        int chunkSize = 5;
        List<CoreMap> sentences = NLPTools.detectPOSStanford(parsed);
        List<List<CoreMap>> chunks = Lists.partition(sentences, chunkSize);


        int lineNum = 1;
        for (List<CoreMap> chunk : chunks) {
            String text = NLPTools.redactTextForNLP(chunk, 0.7, 1000);
            String[] categories = topicModeller.inferCategoriesByTopics(text);
            int end = lineNum + (chunkSize - 1);

            String out = "start: " + lineNum + " end: " + end + " categories: " + Arrays.stream(categories).reduce((c, n) -> c + ", " + n).orElse("");
            System.out.println(out);
            lineNum += chunkSize;
        }

//        String[] categories = topicModeller.inferCategoriesByTopics(parsed);
//
//        for (String category : categories) {
//            System.out.println(category);
//        }
    }

    public TopicModeller(SolrClient client) {
        this.client = client;
    }

    private SerialPipes getPreprocessPipeline() throws IOException {
        ArrayList<Pipe> pipeList = pipe.pipes();
        pipeList.add(1, new CharSequenceLowercase());
        int size = pipeList.size();
        pipeList.add(size - 1, new TokenSequence2PorterStems());
        size = pipeList.size();
        pipeList.add(size - 1, new TokenSequenceNGrams(new int[] {1, 2, 3}, multiGrams));

        return pipe;
    }

    public String[] inferCategoriesByTopics(String text) {
        try {
            InstanceList testing = new InstanceList();
            Instance testInstance = getPreprocessPipeline().instanceFrom(new Instance(text, null, "test instance", null));
            testing.add(testInstance);

            double[] topicDistribution = inferencer.getSampledDistribution(testing.get(0), 10, 1, 5);

            String category = resolveCategoryByTopics(topicSortedWords, dataAlphabet, topicDistribution);
            String[] categories = category.split(";");
            return categories;
        } catch (Exception e) {
            return null;
        }
    }

    private String getTopicText(ArrayList<TreeSet<IDSorter>> topicSortedWords, Alphabet dataAlphabet, int topic, int maxRank) {
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

    private String[] getNameAndIdTokens(String docName) {
        String[] nameTokens = docName.split(",");
        int tokenLimit = nameTokens.length > 2 ? nameTokens.length - 1 : nameTokens.length;
        String docNameReduced = Arrays.stream(nameTokens).limit(tokenLimit).reduce((c,n) -> c + "," + n).orElse("ERROR");
        String[] nameAndIdTokens = docNameReduced.split("\\|");

        return nameAndIdTokens;
    }

    public void addTopicsToDocuments() throws Exception {
        String dataFilePath = "data/topic-modeling.data";
        client.writeCorpusDataToFile(dataFilePath, null, null, client::getClusteringDataQuery, client::formatForTopicModeling, new SolrClient.NERThrottle());

        InstanceList instances = new InstanceList (getPreprocessPipeline());

        Reader fileReader = new InputStreamReader(new FileInputStream(new File(dataFilePath)), "UTF-8");
        instances.addThruPipe(new CsvIterator (fileReader, Pattern.compile("^(\\S*)[\\s,]*(\\S*)[\\s,]*(.*)$"), 3, 2, 1)); // data, label, name fields

        //double[][] topicVectors = new double[instances.size()][model.numTopics];

        Formatter out = new Formatter(new StringBuilder(), Locale.US);
        for (int i = 0; i < instances.size(); i++) {
            Instance instance = instances.get(i);
            String docName = instance.getName().toString();
            String[] nameAndIdTokens = getNameAndIdTokens(docName);
            String id = nameAndIdTokens[0];
            docName = nameAndIdTokens[1];

            double[] topicDistribution = inferencer.getSampledDistribution(instance, 10, 1, 5);
            //topicVectors[i] = topicDistribution;
            int[] orderedTopics = Tools.argsort(topicDistribution, false);
            double prob1 = topicDistribution[orderedTopics[0]];
            String topicText1 = getTopicText(topicSortedWords, dataAlphabet, orderedTopics[0], 5);
            double prob2 = topicDistribution[orderedTopics[1]];
            String topicText2 = getTopicText(topicSortedWords, dataAlphabet, orderedTopics[1], 5);
            double prob3 = topicDistribution[orderedTopics[2]];
            String topicText3 = getTopicText(topicSortedWords, dataAlphabet, orderedTopics[2], 5);
            String category = resolveCategoryByTopics(topicSortedWords, dataAlphabet, topicDistribution);
            out.format("%s,%s,%s,%.3f,%s,%.3f,%s,%.3f,%s\r\n", id, docName, category, prob1, topicText1, prob2, topicText2, prob3, topicText3);
        }

        FileUtils.writeStringToFile(new File(getDocumentsWithTopicsPath()), out.toString(), Charset.forName("Cp1252").displayName());
    }

    private String resolveCategoryByTopics(ArrayList<TreeSet<IDSorter>> topicSortedWords, Alphabet dataAlphabet, double[] topicDistribution) {
        List<CategoryWeight> categoryWeights = new ArrayList<>();
        for (int i = 0; i < topicDistribution.length; i++) {
            String topicText = getTopicText(topicSortedWords, dataAlphabet, i, 20);
            TopicCategoryMapping mapping = topicsToCategories.get(topicText);
            double probability = topicDistribution[i];
            mapping.updateCategoryWeight(categoryWeights, probability);
        }
        //normalize category weights
        double weightSum = categoryWeights.stream().map(p -> p.catWeight).mapToDouble(Double::doubleValue).sum();
        categoryWeights.stream().forEach(p -> {
            p.catWeight = p.catWeight / weightSum;
        });
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
        String category;
        if (categoryWeights.get(0).category.equals("Not_Applicable")) {
            final double closeEnough = stdDev * 0.125; // 1/8th standard deviation from the max
            double secondBest = weights[1];
            if (secondBest >= (maxWeight - closeEnough)) {
                category = categoryWeights.stream()
                        .filter(p -> !p.category.equals("Not_Applicable") && p.catWeight >= (maxWeight - closeEnough))
                        .map(p -> p.category)
                        .reduce((c, n) -> c + ";" + n).orElse("");
            } else {
                category = categoryWeights.get(0).category;
            }
        } else {
            //we want the top third by weight of all applicable categories to be attached to the document
            category = categoryWeights.stream()
                    .filter(p -> !p.category.equals("Not_Applicable") && p.catWeight >= (maxWeight - stdDev))
                    .map(p -> p.category)
                    .reduce((c, n) -> c + ";" + n).orElse("");

        }

        return category;
    }

    public void trainLDAModel() throws Exception {
        String trainingFilePath = "data/topic-modeling.train";
        client.writeCorpusDataToFile(trainingFilePath, null,null, client::getClusteringDataQuery, client::formatForTopicModeling, new SolrClient.NERThrottle());

        Pipe pipeline = getPreprocessPipeline();

        InstanceList instances = new InstanceList (pipeline);

        Reader fileReader = new InputStreamReader(new FileInputStream(new File(trainingFilePath)), "UTF-8");
        instances.addThruPipe(new CsvIterator (fileReader, Pattern.compile("^(\\S*)[\\s,]*(\\S*)[\\s,]*(.*)$"), 3, 2, 1)); // data, label, name fields

        // Create a model with 100 topics, alpha_t = 0.01, beta_w = 0.01
        //  Note that the first parameter is passed as the sum over topics, while
        //  the second is the parameter for a single dimension of the Dirichlet prior.
        int numTopics = 100;
        ParallelTopicModel model = new ParallelTopicModel(numTopics, 1.0, 0.01);

        model.addInstances(instances);

        model.setNumThreads(8);

        // Run the model for 50 iterations and stop (this is for testing only,
        //  for real applications, use 1000 to 2000 iterations)
        model.setNumIterations(1000);
        model.estimate();
        model.write(new File(getModelFilePath()));
    }

    public String getModelFilePath() {
        return "data/topic-model.mallet";
    }

    public String getDocumentsWithTopicsPath() {
        return "data/docs-with-topics.csv";
    }
}
