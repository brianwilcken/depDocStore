package nlp;

import cc.mallet.grmm.inference.Inferencer;
import cc.mallet.pipe.CharSequenceLowercase;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.util.*;
import cc.mallet.types.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.topics.*;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import common.Tools;
import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.springframework.core.io.ClassPathResource;
import solrapi.SolrClient;

import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.util.stream.Collectors;

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
            instanceList = InstanceList.load(new File("data/mallet_model/corpus.mallet"));
            pipe = (SerialPipes)instanceList.getPipe();
            model.addInstances(instanceList);
            model.initializeFromState(new File("data/mallet_model/state.mallet.gz"));
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
                    .readValues(new File("data/mallet_model/topickeys_to_categories.csv"));
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

        topicModeller.addTopicsToDocuments();

//        topicModeller.inferTopic("City of Wichita Consumer Confidence Report 2013 Covering Calendar Year 2012 This brochure is a snapshot of the quality of the water that we provided last year.\n" +
//                "Included are the details about where your water comes from, what it contains, and how it compares to Environmental Protection Agency EPA and state standards.\n" +
//                "We are committed to providing you with information because informed customers are our best allies.\n" +
//                "It is important that customers be aware of the efforts that are made continually to improve their water systems.\n" +
//                "To learn more about your drinking water, please attend any of the regularly scheduled City Council meetings in the Council Chambers at 455 N Main.\n" +
//                "The public is welcome to request time on the agenda for comments about water utility topics.\n" +
//                "Consult our web site www.wichita.gov.\n" +
//                "for further information.\n" +
//                "For more information please contact, Debra Ary at 316-269-4760.\n" +
//                "Our drinking water is supplied from ground water wells and surface water blended before treatment.\n" +
//                "Your water is treated to remove several contaminants and a disinfectant is added to protect you against microbial contaminants.\n" +
//                "The Safe Drinking Water Act SDWA required states to develop a Source Water Assessment SWA for each public water supply that treats and distributes raw source water in order to identify potential contamination sources.\n" +
//                "The state has completed an assessment of our source water.\n" +
//                "For results of the assessment, please contact us or view on-line at http www.kdheks.gov nps swap SWreports.html Some people may be more vulnerable to contaminants in drinking water than the general population.\n" +
//                "Immuno-compromised persons such as those with cancer undergoing chemotherapy, persons who have undergone organ transplants, people with HIV AIDS or other immune system disorders, some elderly, and infants can be particularly at risk from infections.\n" +
//                "These people should seek advice about drinking water from their healthcare providers.\n" +
//                "EPA CDC guidelines on appropriate means to lessen the risk of infection by Cryptosporidium and other microbial contaminants are available from the Safe Drinking Water Hotline 800-426-4791.\n" +
//                "Drinking water, including bottled water, may reasonably be expected to contain at least small amounts of some contaminants.\n" +
//                "The presence of contaminants does not necessarily indicate that water poses a health risk.\n" +
//                "More information about contaminants and potential health effects can be obtained by calling the EPA's Safe Drinking Water Hotline 800-426-4791.\n" +
//                "The sources of drinking water both tap water and bottled water included rivers, lakes, streams, ponds, reservoirs, springs, and wells.\n" +
//                "As water travels over the surface of the land or through the ground, it dissolves naturally occurring minerals and, in some cases, radioactive material, and can pick up substances resulting from the presence of animals or from human activity.\n" +
//                "Contaminants that may be present in sources water before we treat it include Microbial contaminants, such as viruses and bacteria, which may come from sewage treatment plants, septic systems, livestock operations and wildlife.\n" +
//                "Inorganic contaminants, such as salts and metals, which can be naturallyoccurring or result from urban storm water runoff, industrial or domestic wastewater discharges, oil and gas production, mining or farming.\n" +
//                "Pesticides and herbicides, which may come from a variety of sources such as storm water run-off, agriculture, and residential users.\n" +
//                "Radioactive contaminants, which can be naturally occurring or the result of mining activity.\n" +
//                "Organic contaminants, including synthetic and volatile organic chemicals, which are by-products of industrial processes and petroleum production, and also come from gas stations, urban storm water run-off, and septic systems.\n" +
//                "In order to ensure that tap water is safe to drink, EPA prescribes regulation which limits the amount of certain contaminants in water provided by public water systems.\n" +
//                "We treat our water according to EPA's regulations.\n" +
//                "Food and Drug Administration regulations establish limits for contaminants in bottled water, which must provide the same protection for public health.\n" +
//                "Our water system is required to testa minimum of 180 samples per month in accordance with the Total Coliform Rule for microbiological contaminants.\n" +
//                "Coliform bacteria are usually harmless, but their presence in water can be an indication of disease-causing bacteria.\n" +
//                "When coliform bacteria are found, special follow-up tests are done to determine if harmful bacteria are present in the water supply.\n" +
//                "If this limit is exceeded, the water supplier must notify the public.\n" +
//                "Water Quality Data The following tables list all of the drinking water contaminants which were detected during the 2012 calendar year.\n" +
//                "The presence of these contaminants does not necessarily indicate the water poses a health risk.\n" +
//                "Unless noted, the data presented in this table is from the testing done January 1- December 31, 2012.\n" +
//                "The state requires us to monitor for certain contaminants less than once per year because the concentrations of these contaminants are not expected to vary significantly from year to year.\n" +
//                "Some of the data, though representative of the water quality, is more than one year old.\n" +
//                "The bottom line is that the water that is provided to you is safe.\n" +
//                "Terms Abbreviations Maximum Contaminant Level Goal MCLG the Goal is the level of a contaminant in drinking water below which there is no known or expected risk to human health.\n" +
//                "MCLGs allow for a margin of safety.\n" +
//                "Maximum Contaminant Level MCL the Maximum Allowed MCL is the highest level of a contaminant that is allowed in drinking water.\n" +
//                "MCLs are set as close to the MCLGs as feasible using the best available treatment technology.\n" +
//                "Secondary Maximum Contaminant Level SMCL recommended level for a contaminant that is not regulated and has no MCL.\n" +
//                "Action Level AL the concentration of a contaminant that, if exceeded, triggers treatment or other requirements.\n" +
//                "Treatment Technique TT a required process intended to reduce levels of a contaminant in drinking water.\n" +
//                "Maximum Maximum Residual Disinfectant Level MRDL the highest level of a disinfectant allowed in drinking water.\n" +
//                "There is convincing evidence that addition of a disinfectant is necessary for control of microbial contaminants.\n" +
//                "Non-Detects ND lab analysis indicates that the contaminant is not present.\n" +
//                "Parts per Million ppm or milligrams per liter mg l Parts per Billion ppb or micrograms per liter g l Picocuries per Liter pCi L a measure of the radioactivity in water.\n" +
//                "Millirems per Year mrem yr measure of radiation absorbed by the body.\n" +
//                "Monitoring Period Average MPA An average of sample results obtained during a defined time frame, common examples of monitoring periods are monthly, quarterly and yearly.\n" +
//                "Nephelometric Turbidity Unit NTU a measure of the clarity of water.\n" +
//                "Turbidity in excess of 5 NTU is just noticeable to the average person.\n" +
//                "Turbidity is not regulated for groundwater systems.\n" +
//                "Running Annual Average RAA an average of sample results obtained over the most current 12 months and used to determine compliance with MCLs.\n" +
//                "Lead in drinking water is primarily from materials and components associated with service lines and home plumbing.\n" +
//                "Your water system is responsible for providing high quality drinking water, but cannot control the variety of materials used in plumbing components.\n" +
//                "When your water has been sitting for several hours, you can minimize the potential for lead exposure by flushing your tap for 30 seconds to 2 minutes before using water for drinking or cooking.\n" +
//                "If you are concerned about lead in your water, you may wish to have your water tested.\n" +
//                "Information on lead in drinking water, testing methods, and steps you can take to minimize exposure is available from the Safe Drinking Water Hotline or at http www.epa.gov safewater lead.\n" +
//                "Please Note Because of sampling schedules, results may be older than 1 year.");



//        // Show the words and topics in the first instance
//
//        // The data alphabet maps word IDs to strings
//        Alphabet dataAlphabet = model.getAlphabet();
//
//        FeatureSequence tokens = (FeatureSequence) model.getData().get(0).instance.getData();
//        LabelSequence topics = model.getData().get(0).topicSequence;
//
//
//        for (int position = 0; position < tokens.getLength(); position++) {
//            out.format("%s-%d ", dataAlphabet.lookupObject(tokens.getIndexAtPosition(position)), topics.getIndexAtPosition(position));
//        }
//        System.out.println(out);
//
//        // Estimate the topic distribution of the first instance,
//        //  given the current Gibbs state.
//        double[] topicDistribution = model.getTopicProbabilities(0);
//
//        // Get an array of sorted sets of word ID/count pairs
//        ArrayList<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();
//
//        // Show top 5 words in topics with proportions for the first document
//        for (int topic = 0; topic < numTopics; topic++) {
//            Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();
//
//            out = new Formatter(new StringBuilder(), Locale.US);
//            out.format("%d\t%.3f\t", topic, topicDistribution[topic]);
//            int rank = 0;
//            while (iterator.hasNext() && rank < 5) {
//                IDSorter idCountPair = iterator.next();
//                out.format("%s (%.0f) ", dataAlphabet.lookupObject(idCountPair.getID()), idCountPair.getWeight());
//                rank++;
//            }
//            System.out.println(out);
//        }
//
//        // Create a new instance with high probability of topic 0
//        StringBuilder topicZeroText = new StringBuilder();
//        Iterator<IDSorter> iterator = topicSortedWords.get(0).iterator();
//
//        int rank = 0;
//        while (iterator.hasNext() && rank < 5) {
//            IDSorter idCountPair = iterator.next();
//            topicZeroText.append(dataAlphabet.lookupObject(idCountPair.getID()) + " ");
//            rank++;
//        }
//
//        // Create a new instance named "test instance" with empty target and source fields.
//        SerialPipes pipeline = getPreprocessPipeline();
//        InstanceList testing = new InstanceList(pipeline);
//        testing.addThruPipe(new Instance(topicZeroText.toString(), null, "test instance", null));
//
//        TopicInferencer inferencer = model.getInferencer();
//        double[] testProbabilities = inferencer.getSampledDistribution(testing.get(0), 10, 1, 5);
//        System.out.println("0\t" + testProbabilities[0]);
    }

    public TopicModeller(SolrClient client) {
        this.client = client;
    }

    private SerialPipes getPreprocessPipeline() throws IOException {
        ClassPathResource stopwordsResource = new ClassPathResource(Tools.getProperty("spellcheck.stopwords"));

        // Begin by importing documents from text to feature sequences
        //ArrayList<Pipe> pipeList = new ArrayList<>();

        ArrayList<Pipe> pipeList = pipe.pipes();
        pipeList.add(1, new CharSequenceLowercase());
        int size = pipeList.size();
        pipeList.add(size - 1, new TokenSequence2PorterStems());
        size = pipeList.size();
        pipeList.add(size - 1, new TokenSequenceNGrams(new int[] {1, 2, 3}, multiGrams));

        return pipe;

//        pipeList.add(new Target2Label());
//        pipeList.add(new CharSequenceLowercase());
//        pipeList.add(new CharSequence2TokenSequence(Pattern.compile("\\p{L}[\\p{L}\\p{P}]+\\p{L}")));
//        pipeList.add(new TokenSequenceRemoveStopwords(stopwordsResource.getFile(), "UTF-8", false, false, false));
//        //pipeList.add(new TokenSequenceNGrams(new int[] {1, 2, 3}));
//        //pipeList.add(new TokenSequence2PorterStems());
//        pipeList.add(new TokenSequence2FeatureSequence());
//
//        SerialPipes pipeline = new SerialPipes(pipeList);
//        pipeline.setDataAlphabet(pipe.getDataAlphabet());
//
//        return pipeline;
    }

    public String inferTopic(String text) {
        try {
            InstanceList testing = new InstanceList();
            Instance testInstance = pipe.instanceFrom(new Instance(text, null, "test instance", null));
            testing.add(testInstance);

            double[] topicDistribution = inferencer.getSampledDistribution(testing.get(0), 10, 1, 5);
            int[] orderedTopics = Tools.argsort(topicDistribution, false);

            Formatter out = new Formatter(new StringBuilder(), Locale.US);
            for (int topic = 0; topic < 5; topic++) {
                String topicText = getTopicText(topicSortedWords, dataAlphabet, orderedTopics[topic], 100);
                double prob = topicDistribution[orderedTopics[topic]];
                out.format("%.3f\t%s\r\n", prob, topicText);
            }

            System.out.println(out.toString());
            return out.toString();
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

    public void getDocumentsWithTopics() throws Exception {
        Formatter out = new Formatter(new StringBuilder(), Locale.US);
        for (int doc = 0; doc < model.getData().size(); doc++) {
            String docName = model.getData().get(doc).instance.getName().toString();
            String[] nameAndIdTokens = getNameAndIdTokens(docName);
            String id = nameAndIdTokens[0];
            docName = nameAndIdTokens[1];

            double[] topicDistribution = model.getTopicProbabilities(doc);
            int[] orderedTopics = Tools.argsort(topicDistribution, false);
            double prob = topicDistribution[orderedTopics[0]];
            String topicText = getTopicText(topicSortedWords, dataAlphabet, orderedTopics[0], 10);
            out.format("%s,%s,%.3f,%s\r\n", id, docName, prob, topicText);
        }

        FileUtils.writeStringToFile(new File(getDocumentsWithTopicsPath()), out.toString(), Charset.forName("Cp1252").displayName());
    }

    public void addTopicsToDocuments() throws Exception {
        String dataFilePath = "data/topic-modeling.data";
        client.writeCorpusDataToFile(dataFilePath, null, client::getClusteringDataQuery, client::formatForTopicModeling, new SolrClient.NERThrottle());

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

    public String resolveCategoryByTopics(ArrayList<TreeSet<IDSorter>> topicSortedWords, Alphabet dataAlphabet, double[] topicDistribution) {
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
        client.writeCorpusDataToFile(trainingFilePath, null, client::getClusteringDataQuery, client::formatForTopicModeling, new SolrClient.NERThrottle());

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
