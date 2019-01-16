package nlp;

import com.google.common.io.Files;
import common.Tools;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.namefind.*;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.lang.ArrayUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.core.io.ClassPathResource;
import solrapi.SolrClient;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NamedEntityRecognizer {

    final static Logger logger = LogManager.getLogger(NamedEntityRecognizer.class);

//    public static void main(String[] args) {
//        SolrClient client = new SolrClient("http://localhost:8983/solr");
//        NamedEntityRecognizer namedEntityRecognizer = new NamedEntityRecognizer(client);
//
//        autoAnnotateAllForCategory(client, namedEntityRecognizer, "Wastewater");
//
////        try {
////            namedEntityRecognizer.trainNERModel("Electricity");
////        } catch (InsufficientTrainingDataException e) {
////            e.printStackTrace();
////        }
//    }

    private static void autoAnnotateAllForCategory(SolrClient client, NamedEntityRecognizer namedEntityRecognizer, List<String> categories) {
        try {
            SolrDocumentList docs = client.QuerySolrDocuments("categories:" + categories.stream().reduce((p1, p2) -> p1 + ", " + p2).orElse("") + " AND -annotated:*", 1000, 0, null, null);
            for (SolrDocument doc : docs) {
                String document = (String)doc.get("parsed");
                List<NamedEntity> entities = namedEntityRecognizer.detectNamedEntities(document, categories, 0.5);
                String annotated = NLPTools.autoAnnotate(document, entities);
                if (doc.containsKey("annotated")) {
                    doc.replace("annotated", annotated);
                } else {
                    doc.addField("annotated", annotated);
                }
                //FileUtils.writeStringToFile(new File("data/annotated.txt"), annotated, Charset.forName("Cp1252").displayName());

                client.indexDocument(doc);
            }
        } catch (SolrServerException | IOException e) {
            e.printStackTrace();
        }
    }

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
    //private StanfordCoreNLP pipeline;

    public NamedEntityRecognizer(SolrClient client) {
//        Properties props = new Properties();
//        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
//        props.setProperty("threads", "4");
//        pipeline = new StanfordCoreNLP(props);

        sentModel = NLPTools.getModel(SentenceModel.class, new ClassPathResource(Tools.getProperty("nlp.sentenceDetectorModel")));
        //tokenizerModel = NLPTools.getModel(TokenizerModel.class, new ClassPathResource(Tools.getProperty("nlp.tokenizerModel")));
        this.client = client;
    }

//    public List<NamedEntity> detectNamedEntitiesStanford(String document) {
//        Annotation annotation = pipeline.process(document);
//        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
//        List<NamedEntity> namedEntities = new ArrayList<>();
//
//        boolean inEntity = false;
//        String currentEntity = "";
//        String currentEntityType = "";
//        for (int i = 0; i < sentences.size(); i++) {
//            CoreMap sentence = sentences.get(i);
//            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
//                String ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
//
//                if (!inEntity) {
//                    if (!"O".equals(ne)) {
//                        inEntity = true;
//                        currentEntity = "";
//                        currentEntityType = ne;
//                    }
//                }
//                if (inEntity) {
//                    if ("O".equals(ne)) {
//                        inEntity = false;
//                        switch (currentEntityType) {
//                            case "PERSON":
//                                System.out.println("Extracted Person - " + currentEntity.trim());
//                                break;
//                            case "ORGANIZATION":
//                                namedEntities.add(new NamedEntity(currentEntity.trim(), null, i));
//                                System.out.println("Extracted Organization - " + currentEntity.trim());
//                                break;
//                            case "LOCATION":
//                                namedEntities.add(new NamedEntity(currentEntity.trim(), null, i));
//                                System.out.println("Extracted Location - " + currentEntity.trim());
//                                break;
//                            case "DATE":
//                                System.out.println("Extracted Date " + currentEntity.trim());
//                                break;
//                        }
//                    } else {
//                        currentEntity += " " + token.originalText();
//                    }
//
//                }
//            }
//        }
//
//        return namedEntities;
//    }

    public List<NamedEntity> detectNamedEntities(String document, List<String> categories, double threshold) throws IOException {
        List<CoreMap> sentences = NLPTools.detectSentencesStanford(document);
        List<NamedEntity> entities = detectNamedEntities(sentences, categories, threshold);
        return entities;
    }

    public List<NamedEntity> detectNamedEntities(List<CoreMap> sentences, List<String> categories, double threshold, int... numTries) {
        List<NamedEntity> namedEntities = new ArrayList<>();
        try {
            for (String category : categories) {
                String modelFile = getModelFilePath(category);
                TokenNameFinderModel model = NLPTools.getModel(TokenNameFinderModel.class, modelFile);
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
                            NamedEntity namedEntity = new NamedEntity(entity, span, s);
                            curateNamedEntityType(category, namedEntity);
                            namedEntities.add(namedEntity);
                        }
                    }
                }
            }
            return namedEntities;
        } catch (IOException e) {
            if(numTries.length == 0) {
                try {
                    for (String category : categories) {
                        trainNERModel(category); //model may not yet exist, but maybe there is data to train it...
                    }
                } catch (IOException e1) {
                    logger.error(e.getMessage(), e1);
                }
                return detectNamedEntities(sentences, categories, threshold, 1);
            } else {
                //no model training data available...
                logger.error(e.getMessage(), e);
                return namedEntities; //this collection will be empty
            }
        }
    }

    public void curateNamedEntityType(String category, NamedEntity namedEntity) {
        if (dictionaries.containsKey(category)) {
            List<String> dict = dictionaries.get(category);
            //Case 1: the category-specific dictionary contains a token combination that matches at least part of the entity name
            if (!dictionaryMatchesEntity(namedEntity, dict)) {
                //Case 2: some other category dictionary may contain a term that matches part of the entity name
                for (String key : dictionaries.keySet()) {
                    if (!key.equals(category)) {
                        List<String> otherDict = dictionaries.get(key);
                        if (dictionaryMatchesEntity(namedEntity, otherDict)) {
                            Span span = namedEntity.getSpan();
                            Span newSpan = new Span(span.getStart(), span.getEnd(), key, span.getProb());
                            namedEntity.setSpan(newSpan);
                        }
                    }
                }
            }
        }
    }

    private boolean dictionaryMatchesEntity(NamedEntity namedEntity, List<String> dict) {
        long matches = dict.stream().filter(p -> namedEntity.getEntity().toLowerCase().contains(p.toLowerCase())).count();

        return matches > 0;
    }

    public String deepCleanText(String document) {
        String document1 = document.replace("\r\n", " ");
        String document2 = document1.replace("(", " ");
        String document3 = document2.replace(")", " ");
        String document4 = document3.replaceAll("\\P{Print}", " ");
        //String document4a = Tools.removeAllNumbers(document4);
        //document = Tools.removeSpecialCharacters(document);
        String document5 = document4.replaceAll("[\\\\%-*/:-?{-~!\"^_`\\[\\]+]", " ");
        String document6= document5.replaceAll(" +\\.", ".");
        String document7 = document6.replaceAll("\\.{2,}", ". ");
        String document8 = document7.replaceAll(" {2,}", " ");
        String document9 = NLPTools.fixDocumentWordBreaks(document8);
        String document10 = document9.replaceAll("(?<=[a-z])-\\s(?=[a-z])", "");
        String document11 = document10.replaceAll("\\b\\ss\\s\\b", "'s ");

        return document11;
    }

    public String[] detectSentences(String document) {
        document = deepCleanText(document);
        String[] sentences = NLPTools.detectSentences(sentModel, document);

        return sentences;
    }

    public void trainNERModel(String category) throws IOException {
        String trainingFile = getTrainingFilePath(category);
        String modelFile = getModelFilePath(category);

        client.writeTrainingDataToFile(trainingFile, category, client.getCategorySpecificDataQuery(category), client::formatForNERModelTraining);
        ObjectStream<String> lineStream = NLPTools.getLineStreamFromMarkableFile(trainingFile);

        TokenNameFinderModel model;

        TrainingParameters params = new TrainingParameters();
        params.put(TrainingParameters.ITERATIONS_PARAM, 300);
        params.put(TrainingParameters.CUTOFF_PARAM, 1);

        Map<String, Object> resources = new HashMap<>();
        resources.put("ner-dict", getTrainingDictionary(category));

        try (ObjectStream<NameSample> sampleStream = new NameSampleDataStream(lineStream)) {
            model = NameFinderME.train("en", null, sampleStream, params,
                    TokenNameFinderFactory.create(null, getFeatureGeneratorBytes(), resources, new BioCodec()));
        }

        try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelFile))) {
            model.serialize(modelOut);
        }
    }

    private String getModelFilePath(String category) {
        String modelFile = "data/" + category + ".bin";
        return modelFile;
    }

    private String getTrainingFilePath(String category) {
        String trainingFile = "data/" + category + ".train";
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

    private byte[] getFeatureGeneratorBytes() {
        try {
            ClassPathResource resource = new ClassPathResource("nlp/ner-features.xml");
            byte[] featureGeneratorBytes = Files.toByteArray(resource.getFile());

            return featureGeneratorBytes;
        } catch (IOException e) {
            return null;
        }
    }

    public static void main(String[] args) {
        NamedEntityRecognizer recognizer = new NamedEntityRecognizer(null);
        byte[] featureGeneratorBytes = recognizer.getFeatureGeneratorBytes();
        System.out.println(featureGeneratorBytes.length);
    }

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
