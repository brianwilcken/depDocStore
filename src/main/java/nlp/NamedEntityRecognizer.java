package nlp;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import common.Tools;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import neo4japi.Neo4jClient;
import neo4japi.domain.Facility;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.namefind.*;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.featuregen.BrownCluster;
import opennlp.tools.util.featuregen.WordClusterDictionary;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.ClassPathResource;
import solrapi.NERThrottle;
import solrapi.SolrClient;
import webapp.models.GeoNameWithFrequencyScore;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NamedEntityRecognizer {

    final static Logger logger = LogManager.getLogger(NamedEntityRecognizer.class);

//    public static void main(String[] args) {
//        SolrClient client = new SolrClient("http://localhost:8983/solr");
//        NamedEntityRecognizer namedEntityRecognizer = new NamedEntityRecognizer(client);
//
////        try {
////            namedEntityRecognizer.trainNERModel("Electricity");
////        } catch (InsufficientTrainingDataException e) {
////            e.printStackTrace();
////        }
//    }

//    private static void autoAnnotateAllForCategory(SolrClient client, NamedEntityRecognizer namedEntityRecognizer, List<String> categories) {
//        try {
//            SolrDocumentList docs = client.QuerySolrDocuments("categories:" + categories.stream().reduce((p1, p2) -> p1 + ", " + p2).orElse("") + " AND -annotated:*", 1000, 0, null, null);
//            for (SolrDocument doc : docs) {
//                String document = (String)doc.get("parsed");
//                List<NamedEntity> entities = namedEntityRecognizer.detectNamedEntities(document, categories, 0.05);
//                String annotated = NLPTools.autoAnnotate(document, entities);
//                if (doc.containsKey("annotated")) {
//                    doc.replace("annotated", annotated);
//                } else {
//                    doc.addField("annotated", annotated);
//                }
//                //FileUtils.writeStringToFile(new File("data/annotated.txt"), annotated, Charset.forName("Cp1252").displayName());
//
//                client.indexDocument(doc);
//            }
//        } catch (SolrServerException | IOException e) {
//            e.printStackTrace();
//        }
//    }

    private static final Map<String, List<String>> dictionaries;
    static
    {
        dictionaries = new HashMap<>();
        try {
            String dictDir = "nlp/ner-dict";
            ClassPathResource resource = new ClassPathResource(dictDir);
            File[] dictFiles = resource.getFile().listFiles();

            for (int i = 0; i < dictFiles.length; i++) {
                File dictFile = dictFiles[i];
                String category = FilenameUtils.getBaseName(dictFile.getName());
                String wordsText = Tools.getResource("nlp/ner-dict/" + category + ".txt");
                List<String> dictionary = Arrays.asList(wordsText.split("\\n"));
                dictionaries.put(category, dictionary);
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private SentenceModel sentModel;
    //private TokenizerModel tokenizerModel;
    private SolrClient client;
    private Neo4jClient neo4jClient;
    private WordVectorizer vectorizer;

    public NamedEntityRecognizer(SolrClient client) {
        sentModel = NLPTools.getModel(SentenceModel.class, new ClassPathResource(Tools.getProperty("nlp.sentenceDetectorModel")));
        //tokenizerModel = NLPTools.getModel(TokenizerModel.class, new ClassPathResource(Tools.getProperty("nlp.tokenizerModel")));
        this.client = client;
        neo4jClient = new Neo4jClient();
        vectorizer = new WordVectorizer(client);
    }

    public List<NamedEntity> detectNamedEntitiesStanford(String document) {
        List<CoreMap> sentences = NLPTools.detectNERStanford(document);
        List<NamedEntity> namedEntities = new ArrayList<>();

        boolean inEntity = false;
        String currentEntity = "";
        String currentEntityType = "";
        for (int i = 0; i < sentences.size(); i++) {
            CoreMap sentence = sentences.get(i);
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);

                if (!inEntity) {
                    if (!"O".equals(ne)) {
                        inEntity = true;
                        currentEntity = "";
                        currentEntityType = ne;
                    }
                }
                if (inEntity) {
                    if ("O".equals(ne)) {
                        inEntity = false;
                        if (!currentEntityType.equals("DATE")) {
                            namedEntities.add(new NamedEntity(currentEntity.trim(), null, i, "StanfordNLP"));
                        }
                    } else {
                        currentEntity += " " + token.originalText();
                    }

                }
            }
        }

        return namedEntities;
    }

    public List<NamedEntity> detectNamedEntities(String document, List<String> categories, List<GeoNameWithFrequencyScore> geoNames, double threshold) throws IOException {
        List<CoreMap> sentences = NLPTools.detectSentencesStanford(document);
        List<NamedEntity> entities = detectNamedEntities(sentences, categories, threshold);

        Map<String, List<Facility>> facilities = new HashMap<>();
        for (String category : categories) {
            if (category.contains(":")) {
                String[] categoryAndVersion = category.split(":");
                category = categoryAndVersion[0];
            }
            facilities.putAll(neo4jClient.getFacilitiesInArea(geoNames, category));
        }

        for (String facilityType : facilities.keySet()) {
            List<Facility> typeFacilities = facilities.get(facilityType);
            //Map<Facility, String> facilityRegexs = typeFacilities.stream().collect(Collectors.toMap(p -> p, p -> Tools.escapeRegex(p.getName())));
            //String regex = typeFacilities.stream().map(p -> p.getName()).reduce((c, n) -> c + "|" + n).orElse("");
            //regex = Tools.escapeRegex(regex);

            for (Facility facility : typeFacilities) {
                String regex = Tools.escapeRegex(facility.getName());
                if (!Strings.isNullOrEmpty(regex)) {
                    Pattern pattern;
                    try {
                        pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                    } catch (Exception e) {
                        continue;
                    }
                    for (int s = 0; s < sentences.size(); s++) {
                        String sentence = sentences.get(s).toString();
                        List<CoreLabel> tokens = NLPTools.detectTokensStanford(sentence);
                        Matcher facilityMatcher = pattern.matcher(sentence);
                        while(facilityMatcher.find()) {
                            int facStart = facilityMatcher.start();
                            int facEnd = facilityMatcher.end();
                            List<CoreLabel> spanTokens = tokens.stream()
                                    .filter(p -> (facStart == 0 || p.beginPosition() >= facStart) && p.endPosition() <= facEnd)
                                    .collect(Collectors.toList());
                            String[] entityTokensArr = spanTokens.stream().map(p -> p.toString()).toArray(String[]::new);
                            String entity = String.join(" ", entityTokensArr);
                            if (pattern.matcher(entity).matches()) {
                                int spanStart = spanTokens.get(0).get(CoreAnnotations.TokenEndAnnotation.class) - 1;
                                int spanEnd = spanTokens.get(spanTokens.size() - 1).get(CoreAnnotations.TokenEndAnnotation.class);
                                Span span = new Span(spanStart, spanEnd, facilityType);
                                NamedEntity namedEntity = new NamedEntity(entity, span, s, "AHA");
                                boolean storeEntity = true;
                                for (NamedEntity storedEntity : entities) {
                                    if (storedEntity.getLine() == s && storedEntity.getSpan().intersects(span)) {
                                        storeEntity = false;
                                    }
                                }
                                if (storeEntity) {
                                    entities.add(namedEntity);
                                }
                            }
                        }
                    }
                }
            }
        }

        return entities;
    }

    public List<NamedEntity> detectNamedEntities(List<CoreMap> sentences, List<String> categories, double threshold) {
        List<NamedEntity> namedEntities = new ArrayList<>();
        for (String category : categories) {
            String modelFile;
            if (category.contains(":")) {
                String[] categoryAndVersion = category.split(":");
                String categoryOnly = categoryAndVersion[0];
                String version = categoryAndVersion[1];
                modelFile = getModelFilePath(getModelDir(categoryOnly, version));
            } else {
                modelFile = getModelFilePath(getModelDir(category, false));
            }
            TokenNameFinderModel model;
            try {
                model = NLPTools.getModel(TokenNameFinderModel.class, modelFile);
            } catch (IOException e) {
                logger.warn("No NER model exists for category: " + category);
                continue;
            }
            NameFinderME nameFinder = new NameFinderME(model);

            for (int s = 0; s < sentences.size(); s++) {
                String sentence = sentences.get(s).toString();
                List<CoreLabel> tokens = NLPTools.detectTokensStanford(sentence);
                String[] tokensArr = tokens.stream().map(p -> p.toString()).toArray(String[]::new);
                Span[] nameSpans = nameFinder.find(tokensArr);
                double[] probs = nameFinder.probs(nameSpans);
                for (int i = 0; i < nameSpans.length; i++) {
                    double prob = probs[i];
                    Span span = nameSpans[i];
                    int start = span.getStart();
                    int end = span.getEnd();
                    String[] entityParts = Arrays.copyOfRange(tokensArr, start, end);
                    String entity = String.join(" ", entityParts);
                    if (prob > threshold) {
                        NamedEntity namedEntity = new NamedEntity(entity, span, s, category);
                        //curateNamedEntityType(category, namedEntity);
                        final int currLine = s;
                        //resolve duplicate entities that may overlap between different model categories
                        List<NamedEntity> sameEntities = namedEntities.stream().filter(p -> p.getLine() == currLine &&
                                (p.getEntity().contains(entity) || entity.contains(p.getEntity())) &&
                                p.getSpan().intersects(span)).collect(Collectors.toList());
                        if (sameEntities.size() > 0) {
                            NamedEntity sameEntity = sameEntities.get(0);
                            if (sameEntity.getSpan().getProb() < namedEntity.getSpan().getProb()) {
                                //favor the entity with the higher probability
                                namedEntities.remove(sameEntity);
                                namedEntities.add(namedEntity);
                            }
                        } else {
                            namedEntities.add(namedEntity);
                        }
                    }
                }
            }
        }
        return namedEntities;
    }

//    public void curateNamedEntityType(String category, NamedEntity namedEntity) {
//        if (dictionaries.containsKey(category)) {
//            List<String> dict = dictionaries.get(category);
//            //Case 1: the category-specific dictionary contains a token combination that matches at least part of the entity name
//            if (!dictionaryMatchesEntity(namedEntity, dict)) {
//                //Case 2: some other category dictionary may contain a term that matches part of the entity name
//                for (String key : dictionaries.keySet()) {
//                    if (!key.equals(category)) {
//                        List<String> otherDict = dictionaries.get(key);
//                        if (dictionaryMatchesEntity(namedEntity, otherDict)) {
//                            Span span = namedEntity.getSpan();
//                            Span newSpan = new Span(span.getStart(), span.getEnd(), key, span.getProb());
//                            namedEntity.setSpan(newSpan);
//                        }
//                    }
//                }
//            }
//        }
//    }

    private boolean dictionaryMatchesEntity(NamedEntity namedEntity, List<String> dict) {
        long matches = dict.stream().filter(p -> namedEntity.getEntity().toLowerCase().contains(p.toLowerCase())).count();

        return matches > 0;
    }

    public String[] detectSentences(String document) {
        document = NLPTools.deepCleanText(document);
        String[] sentences = NLPTools.detectSentences(sentModel, document);

        return sentences;
    }

    public String retrieveCorpusData(String category) throws IOException {
        String trainingFile = getTrainingFilePath(category);
        client.writeCorpusDataToFile(trainingFile, null, null, category, client.getCategorySpecificNERModelTrainingDataQuery(category), client::formatForNERCorpusReview, new NERThrottle());
        String corpus = FileUtils.readFileToString(new File(trainingFile), Charset.defaultCharset());

        return corpus;
    }

    public void trainNERModel(String category) throws IOException {
        if (category.contains(":")) {
            category = category.split(":")[0];
        }
        String trainingFile = getTrainingFilePath(category);
        String modelDir = getModelDir(category, true);
        String modelFile = getModelFilePath(modelDir);

        client.writeCorpusDataToFile(trainingFile, null, null, category, client.getCategorySpecificNERModelTrainingDataQuery(category), client::formatForNERModelTraining, new NERThrottle());
        ObjectStream<String> lineStream = NLPTools.getLineStreamFromMarkableFile(trainingFile);

        if (lineStream.read() == null) {
            return;
        }
        lineStream.reset();

        TokenNameFinderModel model;

        TrainingParameters params = new TrainingParameters();
        params.put(TrainingParameters.ITERATIONS_PARAM, 300);
        params.put(TrainingParameters.CUTOFF_PARAM, 1);

        Map<String, Object> resources = new HashMap<>();
        resources.put("ner-dict", getTrainingDictionary(category));
        resources.put("word2vec.cluster", getWordClusterDictionary(category, 1));
        resources.put("brownCluster", getBrownClusterDictionary(category, 1));
        resources.put("clark.cluster", getClarkClusterDictionary(category, 1));

        int lineNum = 0;
        try (ObjectStream<NameSample> sampleStream = new NameSampleDataStream(lineStream)) {
            while(sampleStream.read() != null) {
                ++lineNum;
            }
            sampleStream.reset();
            model = NameFinderME.train("en", null, sampleStream, params,
                    TokenNameFinderFactory.create(null, getFeatureGeneratorBytes(), resources, new BioCodec()));
        }

        try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelFile))) {
            model.serialize(modelOut);
        }

        NERModelEvaluation evaluationReport = evaluateNERModel(category);
        String stats = evaluationReport.getStats();
        stats += System.lineSeparator() + "Number of model sentences: " + lineNum;
        String reportFile = getReportFilePath(modelDir);
        String refsFile = getReferencesFilePath(modelDir);
        String predsFile = getPredictionsFilePath(modelDir);
        FileUtils.writeStringToFile(new File(reportFile), stats, Charset.defaultCharset());
        FileUtils.writeStringToFile(new File(refsFile), evaluationReport.getRefLines(), Charset.defaultCharset());
        FileUtils.writeStringToFile(new File(predsFile), evaluationReport.getPredLines(), Charset.defaultCharset());
    }

    public NERModelEvaluation evaluateNERModel(String category) {
        try {
            String testFile = getTestFilePath(category);

            client.writeCorpusDataToFile(testFile, null, null, category, client.getCategorySpecificNERModelTestingDataQuery(category), client::formatForNERModelTraining, new NERThrottle());
            ObjectStream<String> lineStream = NLPTools.getLineStreamFromMarkableFile(testFile);

            if (lineStream.read() == null) {
                return null;
            }
            lineStream.reset();

            String modelFile = getModelFilePath(getModelDir(category, false));
            TokenNameFinderModel model = NLPTools.getModel(TokenNameFinderModel.class, modelFile);
            NameFinderME nameFinder = new NameFinderME(model);

            ByteArrayOutputStream reportStream = new ByteArrayOutputStream();
            TokenNameFinderFineGrainedReportListener reportListener = new TokenNameFinderFineGrainedReportListener(new BioCodec(), reportStream);
            TokenNameFinderEvaluator evaluator = new TokenNameFinderEvaluator(nameFinder, reportListener);

            try (ObjectStream<NameSample> samples = new NameSampleDataStream(lineStream)) {
                evaluator.evaluate(samples);
            }

            reportListener.writeReport();
            String report = reportStream.toString();

            NERModelEvaluation nerModelEvaluation = new NERModelEvaluation(report, reportListener.getReferenceLines(), reportListener.getPredictionLines());

            return nerModelEvaluation;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    public List<NERModelData> getModelListing(String category) {
        String modelPath = getModelDir(category, false);
        File modelDir = new File(modelPath);
        File categoryDir = modelDir.getParentFile();
        File[] dirs = categoryDir.listFiles();
        List<NERModelData> modelListing = new ArrayList<>();
        for (File dir : dirs) {
            if (dir.isDirectory()) {
                try {
                    NERModelData data = new NERModelData(dir);
                    modelListing.add(data);
                } catch (IOException e) {
                    continue;
                }
            }
        }
        return modelListing;
    }

    public NERModelEvaluation getModelEvaluation(String category, String version) {
        String modelDir = getModelDir(category, version);
        String refsPath = getReferencesFilePath(modelDir);
        String predsPath = getPredictionsFilePath(modelDir);
        File refsFile = new File(refsPath);
        File predsFile = new File(predsPath);
        try {
            String refs = refsFile.exists() ? Files.toString(refsFile, Charsets.UTF_8) : "";
            String preds = predsFile.exists() ? Files.toString(predsFile, Charsets.UTF_8) : "";
            NERModelEvaluation eval = new NERModelEvaluation("", refs, preds);
            return eval;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    private String getModelFilePath(String modelDir) {
        String modelFile = modelDir + "/model.bin";
        return modelFile;
    }

    private String getReportFilePath(String modelDir) {
        String modelFile = modelDir + "/report.txt";
        return modelFile;
    }

    private String getReferencesFilePath(String modelDir) {
        String modelFile = modelDir + "/refs.txt";
        return modelFile;
    }

    private String getPredictionsFilePath(String modelDir) {
        String modelFile = modelDir + "/preds.txt";
        return modelFile;
    }

    private String getModelDir(String category, boolean increment) {
        Integer maxDirNum = getCurrentMaximumModelDir(category);

        if (increment) {
            maxDirNum++;
        }

        File modelDir = new File("data/ner/" + category + "/" + maxDirNum);
        modelDir.mkdirs();
        return modelDir.getPath();
    }

    private String getModelDir(String category, String version) {
        File modelDir = new File("data/ner/" + category + "/" + version);
        modelDir.mkdirs();
        return modelDir.getPath();
    }

    private Integer getCurrentMaximumModelDir(String category) {
        File dirs = new File("data/ner/" + category);
        dirs.mkdirs();
        Integer maxDirNum = Arrays.stream(dirs.listFiles())
                .filter(p -> p.isDirectory())
                .map(p -> Integer.parseInt(p.getName()))
                .max(Integer::compareTo).orElse(0);

        return maxDirNum;
    }

    private String getTrainingFilePath(String category) {
        String trainingFile = "data/" + category + ".train";
        return trainingFile;
    }

    private String getTestFilePath(String category) {
        String trainingFile = "data/" + category + ".test";
        return trainingFile;
    }

    private Dictionary getTrainingDictionary(String category) {
        try {
            ClassPathResource resource = new ClassPathResource("nlp/ner-dict/" + category + ".xml");
            InputStream in = resource.getInputStream();
            Dictionary dict = new Dictionary(in);
            return dict;
        } catch (IOException e) {
            return new Dictionary();
        }
    }

    private WordClusterDictionary getWordClusterDictionary(String category, int numAttempts) throws IOException {
        try {
            String clusterFilePath = vectorizer.getXMeansClusterFilePath(category);
            FileInputStream fin = new FileInputStream(new File(clusterFilePath));
            WordClusterDictionary dict = new WordClusterDictionary(fin);
            return dict;
        } catch (IOException e) {
            try {
                if (numAttempts < 2) {
                    vectorizer.generateWordClusterDictionary(category);
                    return getWordClusterDictionary(category, ++numAttempts);
                } else {
                    throw e;
                }
            } catch (IOException e1) {
                throw e1;
            }
        }
    }

    private WordClusterDictionary getClarkClusterDictionary(String category, int numAttempts) throws IOException {
        String clusterFilePath = vectorizer.getClarkClusterFilePath(category);
        try {
            FileInputStream fin = new FileInputStream(new File(clusterFilePath));
            WordClusterDictionary dict = new WordClusterDictionary(fin);
            return dict;
        } catch (IOException e) {
            try {
                if (numAttempts < 2) {
                    //unable to programmatically generate Clark clusters (this must be done manually)
                    //in case a valid clustering file does not yet exist generate a blank stub to permit model training to continue
                    FileUtils.writeStringToFile(new File(clusterFilePath), "", StandardCharsets.UTF_8);
                    return getWordClusterDictionary(category, ++numAttempts);
                } else {
                    throw e;
                }
            } catch (IOException e1) {
                throw e1;
            }
        }
    }

    private BrownCluster getBrownClusterDictionary(String category, int numAttempts) throws IOException {
        try {
            String clusterFilePath = vectorizer.getBrownClusterFilePath(category);
            FileInputStream fin = new FileInputStream(new File(clusterFilePath));
            BrownCluster dict = new BrownCluster(fin);
            return dict;
        } catch (IOException e) {
            try {
                if (numAttempts < 2) {
                    vectorizer.generateWordClusterDictionary(category);
                    return getBrownClusterDictionary(category, ++numAttempts);
                } else {
                    throw e;
                }
            } catch (IOException e1) {
                throw e1;
            }
        }
    }

    private byte[] getFeatureGeneratorBytes() {
        try {
            ClassPathResource resource = new ClassPathResource("nlp/ner-features.xml");
            byte[] featureGeneratorBytes = Files.toByteArray(resource.getFile());

            return featureGeneratorBytes;
        } catch (IOException e) {
            return null;
        }
    }

//    public static void main(String[] args) {
//        String doc = "Jordan Valley Water Conservancy District Protect Treat Deliver 01 Board of Trustees 02 General Managers Message 03 Service Area 04 Wholesale Member Agencies 06 Sources 07 Deliveries 08 The Water We Drink Southwest Groundwater Treatment Plant Treatment Department 14 Water Treatment source to tap 16 Chlorine Dioxide Results 17 Financial Stewardship 18 Executive Staff 20 Outstanding Employees On the front While Jordan Valley Water Conservancy District's responsibilities are many and varied, our main responsibility is to treat and deliver water that meets the highest quality standards.\n" +
//                "Although EPA imposes strict standards for drinking water, we at Jordan Valley Water require even stricter standards for ourselves.\n" +
//                "During 2012 we accomplished this in many ways, including the completion of the Southwest Groundwater Treatment Plant SWGWTP , the final piece to our decades-long groundwater remediation project.\n" +
//                "We also focus attention on our Treatment Department, which completed many amazing projects during the past year.\n" +
//                "We are fortunate in Utah to have high-quality water sources for drinking purposes, but additional treatment processes are needed to further refine the finished product to the highest quality.\n" +
//                "Three of Jordan Valley Water's values directly emphasize our commitment to quality Safety We are committed to employee and public safety.\n" +
//                "Without high-quality drinking water, public safety would be compromised.\n" +
//                "We take very seriously our charge to provide this essential public service.\n" +
//                "Service We care about our customers needs and strive to fulfill them.\n" +
//                "Our dedicated service shines through, whether repairing a broken water pipeline at 2 a.m. in the middle of winter, helping our customers resolve technical issues, or installing new water infrastructure to accomodate population and economic growth.\n" +
//                "Leadership Our passion for quality drives us to employ innovative practices.\n" +
//                "In addition to the completion of the new high-tech reverse osmosis SWGWTP, the Treatment Department also brought a new chlorine dioxide feed system online at the Jordan Valley Water Treatment Plant.\n" +
//                "This process addition decreases disinfection by-products and provides a solid barrier against microbial contamination.\n" +
//                "We are dedicated to the pursuit of quality in every facet of our organization.\n" +
//                "From our General Managers Jordan Valley Water's service area encompasses much of the Salt Lake Valley, including the most rapidly-growing areas in the state.\n" +
//                "Sources of water include the Provo, Weber and Duchesne rivers, groundwater, and local mountain streams.\n" +
//                "More information about our sources can be found on page 6.\n" +
//                "It pumps mining-contaminated water to the reverse osmosis SWGWTP in West Jordan, where it will be remediated to provide high-quality drinking water.\n" +
//                "Jordan Valley Water's 17 member agencies provide our water, sometimes blended with their own sources, to more than 600,000 people every day.\n" +
//                "Right Backfilling over the by-product pipeline and finished water pipline at 8000 S. 1300 W., West Jordan, near TRAX.\n" +
//                "0707 The water we treat 08 The treatment process is just one step in providing clean drinking water to our customers.\n" +
//                "After traveling through a complex network of reservoirs, rivers, and aqueducts, untreated surface water reaches the water treatment plant.\n" +
//                "Jordan Valley Water has three water treatment plants, each using a unique set of processes to clean your water and ensure the highest-quality drinking water available.\n" +
//                "Jordan Valley Water Treatment Plant is the largest water treatment plant in the state of Utah and can treat up to gallons of water per day.\n" +
//                "This plant treats water from the Provo River system using what is known in the industry as conventional treatment illustrated on pg 15.\n" +
//                "Southeast Regional Water Treatment Plant can treat up to gallons of water per day.\n" +
//                "It uses a high-rate clarification process known as Actiflo , which enables it to treat water from several local mountain streams as well as the Provo River system.\n" +
//                "The third of Jordan Valley Water's treatment plants is a new plant that was completed this past year and will come on line in a few months.\n" +
//                "A reverse osmosis facility, the Southwest Groundwater Treatment Plant is part of a joint project with Rio Tinto Kennecott Utah Copper to clean up groundwater contaminated from a century of mining activities.\n" +
//                "The current phase of this plant can treat up to gallons of water per day.\n" +
//                "Strawberry Reservoir, feeding the ULS system to be utilized by Jordan Valley Water as one of its future sources, stores more than a million acre-feet of water.\n" +
//                "On average, gallons of water are treated every day in winter.\n" +
//                "In summer, that number increases to gallons every day.\n" +
//                "Southwest Groundwater Treatment Plant This project is one of the most noteworthy groundwater remediation projects ever completed in the United States, but we didnt make it happen on our own.\n" +
//                "Through the combined efforts of Jordan Valley Water and Rio Tinto Kennecott, this project will clean up water contaminated from more than a century of mining and put it to beneficial use as highly-purified drinking water.\n" +
//                "It will also prevent the contamination from spreading toother sources of water in the Salt Lake Valley.\n" +
//                "In 2012, more than 20 years of negotiation, litigation, design and construction involved in making this project happen finally reached fruition with the completion of this treatment plant.\n" +
//                "The Southwest Groundwater Treatment Plant is one of the largest inland reverse osmosis plants in the country.\n" +
//                "Opposite page The Southwest Groundwater Treatment Plant soon after completion.\n" +
//                "This page, top left Mike installs filters in the cartridge vessels upstream of the reverse osmosis units.\n" +
//                "This page, top right Tim and Alan working in the chemicals room.\n" +
//                "This page, bottom Treatment staff installs reverse osmosis membrane elements in a special preserving agent to await the startup of the SWGWTP.\n" +
//                "1 Drinking water should be clean, clear, and healthy.\n" +
//                "From the beginning of the treatment process to delivery at your tap, Jordan Valley Water's employees work hard to deliver quality water and services every day.\n" +
//                "This year, we highlight the efforts of our Treatment Department, whose work takes the millions of gallons of untreated water and purifies it to some of the best-tasting water in the country.\n" +
//                "As you can see from the pictures on these pages, what they do is varied and complex.\n" +
//                "We appreciate their hard work and dedication and recognize the vital role they play in providing a life-sustaining product to more than half a million residents in the Salt Lake Valley.\n" +
//                "Thirty-two employees make up the department and provide all the water treatment operations and maintenance, water quality monitoring and compliance, and laboratory services.\n" +
//                "Seven maintenance employees keep the equipment running and the facilities and grounds maintained.\n" +
//                "Five employees are responsible for water sample collection, water quality monitoring and reporting to ensure compliance with all federal, state, and county regulations.\n" +
//                "Four laboratory employees run analyses for both Jordan Valley Water and our member agencies in our state-certified laboratory.\n" +
//                "Far right Cleaning out the JVWTP sedimentation basins shows all the things that our treatment processes remove from drinking water.\n" +
//                "Opposite page, top Dave proudly displays the AWWA second-place award for best-tasting water, won by Southeast Regional Water Treatment Plant in 2012.\n" +
//                "Opposite page, bottom right Treatment staff suit up for annual chlorine safety training.\n" +
//                "Opposite page, bottom left Ed works on a flocculator motor at JVWTP.\n" +
//                "Photo by Linda Townes.12 Water treated at Southeast Regional Water Treatment Plant won second place for taste in a 2012 national taste-test.\n" +
//                "This doesnt mean it's a perfect process when chlorine combines with naturally-occurring organic compounds in the water, disinfection byproducts DBPs are the result.\n" +
//                "In the late 1990s, EPA passed the Stage 1 Disinfection By-Product Rule to regulate DBPs known as trihalomethanes THMs and haloacetic acids HAAs.\n" +
//                "Public water systems that added chlorine to their water treatment process were required to monitor and comply with established limits as a systemwide running annual average.\n" +
//                "In 2012 public water systems had to start complying with the second, more stringent phase of this rule.\n" +
//                "Jordan Valley Water was in compliance with Stage 1 and would most likely be able to comply with Stage 2.\n" +
//                "However, because DBPs tend to increase the longer water is in the pipe, we recognized that many of our member agencies would have difficulty meeting the Stage 2 requirements if our treatment process remained unchanged.\n" +
//                "After investigating several alternatives, Treatment Department staff began a process of pilot and full-scale testing of chlorine dioxide.\n" +
//                "Construction of the new feed system began at Jordan Valley Water Treatment Plant in December of 2010, and came on line in February 2012.\n" +
//                "The graph below shows an example of the project's success.\n" +
//                "Chlorine Dioxide Chlorine dioxide, a bold-yellow chemical, has made the treatment process at Jordan Valley Water Treatment Plant more succesful in removing disinfection by-products.\n" +
//                "The change to chlorine dioxide has allowed for better compliance with Stage 2 of the Disinfection By-Product Rule.\n" +
//                "This page, left A Distribution construction crew Jarod, Glen, Clint, and Calin vacuums water from an excavation in preparation for fixing a mainline break.\n" +
//                "This page, right Glen gives Jarod an assist.\n";
//
//        NamedEntityRecognizer recognizer = new NamedEntityRecognizer(null);
//        recognizer.detectNamedEntitiesStanford(doc);
//    }

//    public static void main(String[] args) {
//        String annotated = "This includes all of the <START:FAC> City of Dallas <END> , 23 major wholesale treated water customers, and 4 wholesale raw water customers located in the metropolitan area surrounding Dallas.\n" +
//                "Dallas has actively procured water supplies, constructed reservoirs, and developed water treatment facilities which make it possible for DWU to provide water toits customers.\n" +
//                "In Fiscal Year FY 2012-2013, DWU delivered over 143 billion gallons of treated water.\n" +
//                "As the regional population grows, so grows water demand.\n" +
//                "To meet demand, DWU must plan for increasing the available water supply and expanding its transmission, treatment, and distribution facilities.\n" +
//                "DWU considers water conservation an integral part of this planning process.\n" +
//                " <START:FAC> The City of Dallas <END> has had a water conservation program since the early 1980s.\n" +
//                "In 2001, Dallas increased its conservation efforts with the amendment of CHAPTER 49, WATER AND WASTEWATER, of the <START:FAC> Dallas City Code <END> to include, CONSERVATION MEASURES RELATING TO LAWN AND LANDSCAPE IRRIGATION.\n" +
//                "In 2010, DWU updated its Water Conservation Five-Year Strategic Plan Strategic Plan that included phased implementation of best management practices BMPs under the following major elements 1 City Leadership and <START:FAC> Commitment Education <END> and <START:FAC> Outreach Initiatives Rebate <END> and <START:FAC> Incentive Programs The Water Conservation Plan <END> contained herein incorporates data obtained in the 2010 update of the <START:FAC> Five-Year Strategic Plan <END> .\n" +
//                "1.1 State of Texas Requirements The Texas Administrative Code Title 30, Chapter 288 30 TAC 288 requires holders of an existing permit, certified filing, or certificate of adjudication for the appropriation of surface water in the amount of 1,000 acre-feet a year or more for municipal, industrial, and other nonirrigation uses to develop, submit, and implement a water conservation plan and to update it according to a specified schedule.\n" +
//                "As such, DWU is subject to this requirement.\n" +
//                "Because DWU provides water as a municipal public and wholesale water supplier, DWU's Water Conservation 1 Alan Plummer Associates, Inc. in association with Amy Vickers Associates, Inc., CP Y, Inc., Miya Water and <START:FAC> BDS Technologies <END> , Inc., Water Conservation Five-Year Strategic Plan Update, prepared for <START:FAC> City of Dallas <END> , June 2010.\n" +
//                " <START:FAC> City of Dallas Water Conservation Plan <END> 5 Plan must include information necessary to comply with <START:FAC> Texas <END> Commission on Environmental Quality TCEQ requirements for each of these designations.2 The requirements of Subchapter A that must be included in the <START:FAC> City of Dallas Water Conservation Plan <END> are summarized below.\n" +
//                "Minimum Requirements for Municipal Public and Wholesale Water Suppliers Utility Profile Includes information regarding population and customer data, water use data including total gallons per capita per day GPCD and residential <START:FAC> GPCD <END> , water supply system data, and wastewater system data.\n" +
//                "Sections 3 and 4 Appendix A Description of the <START:FAC> Wholesaler <END> 's Service Area Includes population and customer data, water use data, water supply system data, and wastewater data.\n" +
//                "Figure 3-1 Goals Specific quantified five-year and ten-year targets for water savings to include goals for water loss programs and goals for municipal and residential use, in GPCD.\n" +
//                "The goals established by a public water supplier are not enforceable under this subparagraph.\n" +
//                "Sections 2.2 and 2.3 Accurate Metering Devices The TCEQ requires metering devices with an accuracy of plus or minus 5 percent for measuring water diverted from source supply.\n" +
//                "Section 5.1 Universal Metering, Testing, Repair, and Replacement The TCEQ requires that there be a program for universal metering of both customer and public uses of water for meter testing and repair, and for periodic meter replacement.\n" +
//                "Section 5.2 Leak Detection, Repair, and Control of Unaccounted for Water The regulations require measures to determine and control unaccounted-for water.\n" +
//                "Measures may include periodic visual inspections along distribution lines and periodic audits of the water system for illegal connections or abandoned services.\n" +
//                "Sections 5.3 and 5.4 Continuing Public Education Program TCEQ requires a continuing public education and information program regarding water conservation.\n" +
//                "Section 5.5 Non-Promotional Rate Structure Chapter 288 requires a water rate structure that is costbased and which does not encourage the excessive use of water.\n" +
//                "Section 5.8 and <START:FAC> Appendix A Reservoir Systems Operational Plan <END> This requirement is to provide a coordinated operational structure for operation of reservoirs owned by the water supply entity within a common watershed or river basin in order to optimize available water supplies.\n" +
//                "Section 5.10 Wholesale Customer Requirements The water conservation plan must include a requirement in every water supply contract entered into or renewed after official adoption of the <START:FAC> Water Conservation Plan <END> , and including any contract extension, that each 2 DWU also holds water rights to provide water for industrial use.\n" +
//                "However, since DWU uses these rights to provide water to TXU Electric as a wholesale supplier, a water conservation plan for industrial or mining use is not required.\n" +
//                " <START:FAC> City of Dallas Water Conservation Plan <END> 6 successive wholesale customer develop and implement a water conservation plan or water conservation measures using the applicable elements of Title 30 TAC Chapter 288.\n" +
//                "Section 5.9 A Means of Implementation and Enforcement The regulations require a means to implement and enforce the <START:FAC> Water Conservation Plan <END> , as evidenced by an ordinance, resolution, or tariff, and a description of the authority by which the conservation plan is enforced.\n" +
//                "Sections 5.0 through 5.17 Coordination with Regional Water Planning Groups The water conservation plan should document the coordination with the <START:FAC> Regional Water Planning Group <END> for the service area of the public water supplier to demonstrate consistency with the appropriate approved regional water plan.\n" +
//                "Section 5.12 Additional Requirements for Cities of More than 5,000 People Program for Leak Detection, Repair, and Water Loss Accounting The plan must include a description of the program of leak detection, repair, and water loss accounting for the water transmission, storage, delivery, and distribution system.\n" +
//                "Sections 5.3 and 5.4 Record Management System The plan must include a record management system to record water pumped, water deliveries, water sales and water losses which allows for the desegregation of water sales and uses into the following user classes residential commercial public and institutional and industrial.\n" +
//                "Sections 5.4 and 5.14 Requirements for Wholesale Customers The plan must include a requirement in every wholesale water supply contract entered into or renewed after official adoption of the plan by either ordinance, resolution, or tariff, and including any contract extension, that each successive wholesale customer develop and implement a water conservation plan or water conservation measures using the applicable elements in 30 TAC 288.\n" +
//                "If the customer intends to resell the water, the contract between the initial supplier and customer must provide that the contract for the resale of the water must have water conservation requirements so that each successive customer in the resale of the water will be required to implement water conservation measures in accordance with the provisions of 30 TAC 288.\n" +
//                "Section 5.9 Additional Conservation Strategies TCEQ Rules also list additional optional but not required conservation strategies which may be adopted by suppliers.\n" +
//                "The following optional strategies are included in this plan o Conservation-Oriented Water Rates.\n" +
//                "Section 5.8 and <START:FAC> Appendix A <END> and water rate structures such as uniform or increasing block rate schedules, and or seasonal rates, but not flat rate or decreasing block rates o Ordinances, Plumbing Codes and or Rules on Water Conservation Fixtures.\n" +
//                "Section 5.14 o Fixture Replacement Incentive Programs.\n" +
//                "Sections 5.7.1 through 5.7.3 o Reuse and or Recycling of Wastewater and or Gray Water.\n" +
//                "Sections 5.16 through 5.16.3 o Ordinance and or Programs for Landscape Water Management Sections 5.5.4 and 5.14.\n" +
//                "o Method for Monitoring the Effectiveness of the Plan.\n" +
//                " <START:FAC> City of Dallas Water Conservation Plan <END> 7 This Water Conservation Plan sets forth a program of long-term measures under which the <START:FAC> City of Dallas <END> can improve the overall efficiency of water use and conserve its water resources.";
//
//        String parsed = "This includes all of the City of Dallas, 23 major wholesale treated water customers, and 4 wholesale raw water customers located in the metropolitan area surrounding Dallas.\n" +
//                "Dallas has actively procured water supplies, constructed reservoirs, and developed water treatment facilities which make it possible for DWU to provide water toits customers.\n" +
//                "In Fiscal Year FY 2012-2013, DWU delivered over 143 billion gallons of treated water.\n" +
//                "As the regional population grows, so grows water demand.\n" +
//                "To meet demand, DWU must plan for increasing the available water supply and expanding its transmission, treatment, and distribution facilities.\n" +
//                "DWU considers water conservation an integral part of this planning process.\n" +
//                "The City of Dallas has had a water conservation program since the early 1980s.\n" +
//                "In 2001, Dallas increased its conservation efforts with the amendment of CHAPTER 49, WATER AND WASTEWATER, of the Dallas City Code to include, CONSERVATION MEASURES RELATING TO LAWN AND LANDSCAPE IRRIGATION.\n" +
//                "In 2010, DWU updated its Water Conservation Five-Year Strategic Plan Strategic Plan that included phased implementation of best management practices BMPs under the following major elements 1 City Leadership and Commitment Education and Outreach Initiatives Rebate and Incentive Programs The Water Conservation Plan contained herein incorporates data obtained in the 2010 update of the Five-Year Strategic Plan.\n" +
//                "1.1 State of Texas Requirements The Texas Administrative Code Title 30, Chapter 288 30 TAC 288 requires holders of an existing permit, certified filing, or certificate of adjudication for the appropriation of surface water in the amount of 1,000 acre-feet a year or more for municipal, industrial, and other nonirrigation uses to develop, submit, and implement a water conservation plan and to update it according to a specified schedule.\n" +
//                "As such, DWU is subject to this requirement.\n" +
//                "Because DWU provides water as a municipal public and wholesale water supplier, DWU's Water Conservation 1 Alan Plummer Associates, Inc. in association with Amy Vickers Associates, Inc., CP Y, Inc., Miya Water and BDS Technologies, Inc., Water Conservation Five-Year Strategic Plan Update, prepared for City of Dallas, June 2010.\n" +
//                "City of Dallas Water Conservation Plan 5 Plan must include information necessary to comply with Texas Commission on Environmental Quality TCEQ requirements for each of these designations.2 The requirements of Subchapter A that must be included in the City of Dallas Water Conservation Plan are summarized below.\n" +
//                "Minimum Requirements for Municipal Public and Wholesale Water Suppliers Utility Profile Includes information regarding population and customer data, water use data including total gallons per capita per day GPCD and residential GPCD , water supply system data, and wastewater system data.\n" +
//                "Sections 3 and 4 Appendix A Description of the Wholesaler's Service Area Includes population and customer data, water use data, water supply system data, and wastewater data.\n" +
//                "Figure 3-1 Goals Specific quantified five-year and ten-year targets for water savings to include goals for water loss programs and goals for municipal and residential use, in GPCD.\n" +
//                "The goals established by a public water supplier are not enforceable under this subparagraph.\n" +
//                "Sections 2.2 and 2.3 Accurate Metering Devices The TCEQ requires metering devices with an accuracy of plus or minus 5 percent for measuring water diverted from source supply.\n" +
//                "Section 5.1 Universal Metering, Testing, Repair, and Replacement The TCEQ requires that there be a program for universal metering of both customer and public uses of water for meter testing and repair, and for periodic meter replacement.\n" +
//                "Section 5.2 Leak Detection, Repair, and Control of Unaccounted for Water The regulations require measures to determine and control unaccounted-for water.\n" +
//                "Measures may include periodic visual inspections along distribution lines and periodic audits of the water system for illegal connections or abandoned services.\n" +
//                "Sections 5.3 and 5.4 Continuing Public Education Program TCEQ requires a continuing public education and information program regarding water conservation.\n" +
//                "Section 5.5 Non-Promotional Rate Structure Chapter 288 requires a water rate structure that is costbased and which does not encourage the excessive use of water.\n" +
//                "Section 5.8 and Appendix A Reservoir Systems Operational Plan This requirement is to provide a coordinated operational structure for operation of reservoirs owned by the water supply entity within a common watershed or river basin in order to optimize available water supplies.\n" +
//                "Section 5.10 Wholesale Customer Requirements The water conservation plan must include a requirement in every water supply contract entered into or renewed after official adoption of the Water Conservation Plan, and including any contract extension, that each 2 DWU also holds water rights to provide water for industrial use.\n" +
//                "However, since DWU uses these rights to provide water to TXU Electric as a wholesale supplier, a water conservation plan for industrial or mining use is not required.\n" +
//                "City of Dallas Water Conservation Plan 6 successive wholesale customer develop and implement a water conservation plan or water conservation measures using the applicable elements of Title 30 TAC Chapter 288.\n" +
//                "Section 5.9 A Means of Implementation and Enforcement The regulations require a means to implement and enforce the Water Conservation Plan, as evidenced by an ordinance, resolution, or tariff, and a description of the authority by which the conservation plan is enforced.\n" +
//                "Sections 5.0 through 5.17 Coordination with Regional Water Planning Groups The water conservation plan should document the coordination with the Regional Water Planning Group for the service area of the public water supplier to demonstrate consistency with the appropriate approved regional water plan.\n" +
//                "Section 5.12 Additional Requirements for Cities of More than 5,000 People Program for Leak Detection, Repair, and Water Loss Accounting The plan must include a description of the program of leak detection, repair, and water loss accounting for the water transmission, storage, delivery, and distribution system.\n" +
//                "Sections 5.3 and 5.4 Record Management System The plan must include a record management system to record water pumped, water deliveries, water sales and water losses which allows for the desegregation of water sales and uses into the following user classes residential commercial public and institutional and industrial.\n" +
//                "Sections 5.4 and 5.14 Requirements for Wholesale Customers The plan must include a requirement in every wholesale water supply contract entered into or renewed after official adoption of the plan by either ordinance, resolution, or tariff , and including any contract extension, that each successive wholesale customer develop and implement a water conservation plan or water conservation measures using the applicable elements in 30 TAC 288.\n" +
//                "If the customer intends to resell the water, the contract between the initial supplier and customer must provide that the contract for the resale of the water must have water conservation requirements so that each successive customer in the resale of the water will be required to implement water conservation measures in accordance with the provisions of 30 TAC 288.\n" +
//                "Section 5.9 Additional Conservation Strategies TCEQ Rules also list additional optional but not required conservation strategies which may be adopted by suppliers.\n" +
//                "The following optional strategies are included in this plan o Conservation-Oriented Water Rates.\n" +
//                "Section 5.8 and Appendix A and water rate structures such as uniform or increasing block rate schedules, and or seasonal rates, but not flat rate or decreasing block rates o Ordinances, Plumbing Codes and or Rules on Water Conservation Fixtures.\n" +
//                "Section 5.14 o Fixture Replacement Incentive Programs.\n" +
//                "Sections 5.7.1 through 5.7.3 o Reuse and or Recycling of Wastewater and or Gray Water.\n" +
//                "Sections 5.16 through 5.16.3 o Ordinance and or Programs for Landscape Water Management Sections 5.5.4 and 5.14.\n" +
//                "o Method for Monitoring the Effectiveness of the Plan.\n" +
//                "City of Dallas Water Conservation Plan 7 This Water Conservation Plan sets forth a program of long-term measures under which the City of Dallas can improve the overall efficiency of water use and conserve its water resources.";
//
//        List<NamedEntity> entities = NLPTools.extractNamedEntities(annotated);
//        String reannotated = NLPTools.autoAnnotate(parsed, entities);
//
//        System.out.println(reannotated);
//    }
}
