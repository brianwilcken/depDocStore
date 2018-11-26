package nlp;

import com.google.common.collect.Lists;
import common.Tools;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class InformationExtractor {
    private final static Logger logger = LogManager.getLogger(InformationExtractor.class);
    private StanfordCoreNLPWithThreadControl pipeline;
    private StanfordCoreNLP sentPipeline;

    public InformationExtractor() {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse,natlog,openie");
        pipeline = new StanfordCoreNLPWithThreadControl(props);
        props.setProperty("annotators", "tokenize,ssplit,pos");
        sentPipeline = new StanfordCoreNLP(props);
    }

    private List<Annotation> getDocumentAnnotations(String text) {
        final int partitionSize = 300;
        final long timeout = 180; //seconds

        Annotation sentenceAnnotations = sentPipeline.process(text);
        List<CoreMap> sentences = sentenceAnnotations.get(CoreAnnotations.SentencesAnnotation.class);
        List<List<CoreMap>> chunks = Lists.partition(sentences, partitionSize);

        List<OpenIETask> tasks = new ArrayList<>();
        int lineStart = 0;
        for (List<CoreMap> chunk : chunks) {
            List<String> chunkSentences = chunk.stream().map(p -> p.toString()).collect(Collectors.toList());
            int sentTotal = chunkSentences.size();

            String chunkText = NLPTools.redactTextForNLP(chunk, 0.7, 500);

            logger.info("OpenIE: queue " + sentTotal + " lines for processing: " + lineStart);
            OpenIETask task = new OpenIETask(pipeline, lineStart);
            task.enqueue(chunkText);
            tasks.add(task);
            lineStart += sentTotal;
        }

        int waitCycles = 0;
        while(tasks.stream().anyMatch(p -> !p.isCancelled() && !p.isDone())) {
            try {
                tasks.stream()
                        .filter(p -> !p.isCancelled() && !p.isDone() && p.hasElapsed(timeout))
                        .forEach(p -> {
                            logger.info("OpenIE: cancelling due to timeout: " + p.getLineStart());
                            p.cancel();
                        });
                Thread.sleep(1000);

                waitCycles++;
                if (waitCycles % 5 == 0) {
                    tasks.stream().filter(p -> p.isActivated() && !p.isCancelled() && !p.isDone()).forEach(p -> {
                        logger.info("OpenIE: awaiting completion of " + p.getLineStart());
                    });

                }
            } catch (Exception e) { }
        }

        List<Annotation> annotationParts = tasks.stream()
                .filter(p -> !p.isCancelled())
                .map(p -> p.getAnnotations())
                .collect(Collectors.toList());

        return annotationParts;
    }

    public List<EntityRelation> getEntityRelations(String text, String docId, List<NamedEntity> entities, List<Coreference> coreferences) {
        final double similarityThreshold = 0.5;
        final double lineNumRange = 5;

        List<Annotation> annotationParts = getDocumentAnnotations(text);

        List<EntityRelation> relatedEntities = new ArrayList<>();

        for (Annotation annotationPart : annotationParts) {
            List<CoreMap> sentences = annotationPart.get(CoreAnnotations.SentencesAnnotation.class);
            for (int i = 0; i < sentences.size(); i++) {
                final int lineNum = i;
                CoreMap sentence = sentences.get(i);
                // Get the OpenIE triples for the sentence
                Collection<RelationTriple> triples =
                        sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);

                logger.info("Now processing sentence: " + (lineNum + 1));
                logger.info("Current sentence contains # triples: " + triples.size());

                for (RelationTriple triple : triples) {
                    String subject = triple.subjectLemmaGloss();
                    String relation = triple.relationLemmaGloss();
                    String object = triple.objectLemmaGloss();

                    logger.info("Analyzing relation triple: <" + subject + "> <" + relation + "> <" + object + ">");

                    //A relation may be established by either direct reference to a named entity or by coreference
                    List<NamedEntity> subjectEntities = entities.stream()
                            .filter(p -> NLPTools.similarity(subject, p.getEntity()) > similarityThreshold && Tools.numericRangeCompare(lineNum, p.getLine(), lineNumRange))
                            .collect(Collectors.toList());
                    if (subjectEntities.size() > 0) {
                        for (NamedEntity subjectEntity : subjectEntities) {
                            logger.info("Subject entity found: " + subjectEntity.getEntity());
                        }
                    }

                    List<NamedEntity> objectEntities = entities.stream()
                            .filter(p -> NLPTools.similarity(object, p.getEntity()) > similarityThreshold && Tools.numericRangeCompare(lineNum, p.getLine(), lineNumRange))
                            .collect(Collectors.toList());
                    if (objectEntities.size() > 0) {
                        for (NamedEntity objectEntity : objectEntities) {
                            logger.info("Object entity found: " + objectEntity.getEntity());
                        }
                    }

                    List<Coreference> subjectCorefs = coreferences.stream()
                            .filter(p -> NLPTools.similarity(subject, p.getCoref()) > similarityThreshold && Tools.numericRangeCompare(lineNum, p.getLine(), lineNumRange))
                            .collect(Collectors.toList());
                    if (subjectCorefs.size() > 0) {
                        for (Coreference subjectCoref : subjectCorefs) {
                            logger.info("Subject coreference found: " + subjectCoref.getCoref() + " -> " + subjectCoref.getNamedEntity().getEntity());
                        }
                    }

                    List<Coreference> objectCorefs = coreferences.stream()
                            .filter(p -> NLPTools.similarity(object, p.getCoref()) > similarityThreshold && Tools.numericRangeCompare(lineNum, p.getLine(), lineNumRange))
                            .collect(Collectors.toList());
                    if (objectCorefs.size() > 0) {
                        for (Coreference objectCoref : objectCorefs) {
                            logger.info("Object coreference found: " + objectCoref.getCoref() + " -> " + objectCoref.getNamedEntity().getEntity());

                        }
                    }

                    if ((subjectEntities.size() > 0 || subjectCorefs.size() > 0) &&
                            (objectEntities.size() > 0 || objectCorefs.size() > 0)) {
                        logger.info("Possible entity relation discovered in sentence: " + (lineNum + 1));
                        logger.info("Subject: " + subject);
                        logger.info("Object: " + object);
                        logger.info("For relation: " + relation);
                        //determine which subject/object entity to use
                        subjectEntities.addAll(subjectCorefs.stream().map(p -> p.getNamedEntity()).collect(Collectors.toList()));
                        objectEntities.addAll(objectCorefs.stream().map(p -> p.getNamedEntity()).collect(Collectors.toList()));

                        //ensure the sets of entities are distinct
                        Map<String, List<NamedEntity>> subjectGroups = subjectEntities.stream().collect(Collectors.groupingBy(NamedEntity::getId));
                        Map<String, List<NamedEntity>> objectGroups = objectEntities.stream().collect(Collectors.groupingBy(NamedEntity::getId));

                        List<NamedEntity> uniqueSubjects = subjectGroups.values().stream().map(p -> p.get(0)).collect(Collectors.toList());
                        List<NamedEntity> uniqueObjects = objectGroups.values().stream().map(p -> p.get(0)).collect(Collectors.toList());

                        //assign the tentative subject and object entities to those with the smallest distance from the current line
                        NamedEntity subjectEntity = uniqueSubjects.stream().min(Comparator.comparingInt(p -> Math.abs(lineNum - p.getLine()))).get();
                        NamedEntity objectEntity = uniqueObjects.stream().min(Comparator.comparingInt(p -> Math.abs(lineNum - p.getLine()))).get();

                        //if the subject and object entities are the same then attempt to find a non-matching pair
                        if (subjectEntity.getId().equals(objectEntity.getId())) {
                            Stack<NamedEntity> testEntities = new Stack<>();
                            if (uniqueSubjects.size() > 1) {
                                testEntities.addAll(uniqueSubjects);
                                subjectEntity = getRelationEntity(subjectEntity, objectEntity, testEntities);
                            } else if (uniqueObjects.size() > 1) {
                                testEntities.addAll(uniqueObjects);
                                objectEntity = getRelationEntity(objectEntity, subjectEntity, testEntities);
                            }
                        }

                        if (subjectEntity != null && objectEntity != null && !subjectEntity.getId().equals(objectEntity.getId())) {
                            logger.info("Entity relation is valid!");
                            logger.info("Subject Referenced by: " + subjectEntity.getEntity());
                            logger.info("Object Referenced by: " + objectEntity.getEntity());
                            EntityRelation entityRelation = new EntityRelation(subjectEntity, objectEntity, relation, lineNum);
                            relatedEntities.add(entityRelation);
                        } else {
                            logger.info("Entity relation is NOT valid.");
                        }
                    }
                }
            }
        }

        return relatedEntities;
    }

    private NamedEntity getRelationEntity(NamedEntity entity, NamedEntity entityTest, Stack<NamedEntity> entities) {
        if (!entity.getId().equals(entityTest.getId())) {
            return entity;
        } else {
            if (!entities.empty()) {
                return getRelationEntity(entities.pop(), entityTest, entities);
            } else {
                return null;
            }
        }
    }

    public static void main(String[] args) {
        String text = "An additional 895 acres on the east bench will not be serviced by the secondary water system.\n" +
                "These are primarily areas that have not been serviced by Mapleton Irrigation Company and therefore have no water rights attached to the properties.\n" +
                "The remaining acreage is considered to be 60 irtigable in the developed condition.\n" +
                "The total 24-hour peak day demand for the portions of the city when completely developed to be served by the secondary water system is 12,500 gallons per minute.\n" +
                "Water for the secondary water system will be supplied from the three existing groundwater wells, the recently piped Mapleton-Springville canal, Maple Creek and Hobble Creek.\n" +
                "In order to avoid spilling excess water from these sources back into Hobble Creek, a storage pond will be an integral part of the secondary water system concept.\n" +
                "Existing water rights in Maple Creek and Hobble Creek can be used in the secondary water system by diverting flow from the Creeks into the proposed storage pond and pumping from the pond into the system.\n" +
                "According to the Central Utah Water Conservancy District CUWCD , the current Mapleton Irrigation allotment of water from the Mapleton-Springville canal is 45 of 50 cfs.\n" +
                "If the City desires to supply the build-out demand from the wells and pressurized canal pipeline without using Maple Creek and Hobble Creek water, this allotment must be increased to 50.\n" +
                "This can be accomplished if trades are made with Springville to increase their usage of Hobble Creek water, allowing Mapleton more capacity in the pressurized canal pipeline.\n" +
                "Secondary Water Master Plan Pipe sizing was determined by ensuring that all areas to be serviced had adequate pressure and that velocities in the pipes were reasonable generally less than 5 feet per second.";

        String text2 = "Water Conservation Plans for Industrial or Mining Use.\n" +
                "a A water conservation plan for industrial or mining uses of water must provide information in response to each of the following elements.\n" +
                "If the plan does not provide information for each requirement, the industrial or mining water user shall include in the plan an explanation of why the requirement is not applicable.\n" +
                "1 a description of the use of the water in the production process, including how the water is diverted and transported from the source's of supply, how the water is utilized in the production process, and the estimated quantity of water consumed in the production process and therefore unavailable for reuse, discharge, or other means of disposal 2 specific, quantified five-year and ten-year targets for water savings and the basis for the development of such goals.\n" +
                "The goals established by industrial or mining water users under this paragraph are not enforceable 3 a description of the device's and or methods within an accuracy of plus or minus 5.0 to be used in order to measure and account for the amount of water diverted from the source of supply 4 leak-detection, repair, and accounting for water loss in the water distribution system 5 application of state-of-the-art equipment and or process modifications to improve water use efficiency and 6 any other water conservation practice, method, or technique which the user shows to be appropriate for achieving the stated goal or goals of the water conservation plan.\n" +
                "b An industrial or mining water user shall review and update its water conservation plan, as appropriate, based on an assessment of previous five-year and tenyear targets and any other new or updated information.\n" +
                "The industrial or mining water user shall review and update the next revision of its water conservation plan every five years to coincide with the regional water planning group.\n" +
                "Adopted November 14, 2012 Effective December 6, 2012 288.4.\n" +
                "Redacted.\n" +
                "Texas Commission on Environmental Quality Page 9 Chapter 288 - Water Conservation Plans, Drought Contingency Plans, Guidelines and Requirements a A water conservation plan for agricultural use of water must provide information in response to the following subsections.\n" +
                "If the plan does not provide information for each requirement, the agricultural water user must include in the plan an explanation of why the requirement is not applicable.\n" +
                "1 For an individual agricultural user other than irrigation A a description of the use of the water in the production process, including how the water is diverted and transported from the source's of supply, how the water is utilized in the production process, and the estimated quantity of water consumed in the production process and therefore unavailable for reuse, discharge, or other means of disposal B specific, quantified five-year and ten-year targets for water savings and the basis for the development of such goals.\n" +
                "The goals established by agricultural water users under this subparagraph are not enforceable C a description of the device's and or methods within an accuracy of plus or minus 5.0 to be used in order to measure and account for the amount of water diverted from the source of supply D leak-detection, repair, and accounting for water loss in the water distribution system E application of state-of-the-art equipment and or process modifications to improve water use efficiency and F any other water conservation practice, method, or technique which the user shows to be appropriate for achieving the stated goal or goals of the water conservation plan.\n" +
                "2 For an individual irrigation user A a description of the irrigation production process which shall include, but is not limited to, the type of crops and acreage of each crop to be irrigated, monthly irrigation diversions, any seasonal or annual crop rotation, and soil types of the land to be irrigated B a description of the irrigation method, or system, and equipment including pumps, flow rates, plans, and or sketches of the system layout Texas Commission on Environmental Quality Page 10 Chapter 288 - Water Conservation Plans, Drought Contingency Plans, Guidelines and Requirements C a description of the device's and or methods, within an accuracy of plus or minus 5.0 , to be used in order to measure and account for the amount of water diverted from the source of supply D specific, quantified five-year and ten-year targets for water savings including, where appropriate, quantitative goals for irrigation water use efficiency and a pollution abatement and prevention plan.\n" +
                "The goals established by an individual irrigation water user under this subparagraph are not enforceable E water-conserving irrigation equipment and application system or method including, but not limited to, surge irrigation, low pressure sprinkler, drip irrigation, and nonleaking pipe F leak-detection, repair, and water-loss control G scheduling the timing and or measuring the amount of water applied for example, soil moisture monitoring H land improvements for retaining or reducing runoff, and increasing the infiltration of rain and irrigation water including, but not limited to, land leveling, furrow diking, terracing, and weed control I tailwater recovery and reuse and J any other water conservation practice, method, or technique which the user shows to be appropriate for preventing waste and achieving conservation.\n" +
                "3 For a system providing agricultural water to more than one user A a system inventory for the supplier's i structural facilities including the supplier's water storage, conveyance, and delivery structures ii management practices, including the supplier's operating rules and regulations, water pricing policy, and a description of practices and or devices used to account for water deliveries and iii a user profile including square miles of the service area, the number of customers taking delivery of water by the system, the types of crops, the Texas Commission on Environmental Quality Page 11 Chapter 288 - Water Conservation Plans, Drought Contingency Plans, Guidelines and Requirements types of irrigation systems, the types of drainage systems, and total acreage under irrigation, both historical and projected B specific, quantified five-year and ten-year targets for water savings including maximum allowable losses for the storage and distribution system.\n" +
                "The goals established by a system providing agricultural water to more than one user under this subparagraph are not enforceable C a description of the practice's and or device's which will be utilized to measure and account for the amount of water diverted from the source's of supply D a monitoring and record management program of water deliveries, sales, and losses E a leak-detection, repair, and water loss control program F a program to assist customers in the development of on-farm water conservation and pollution prevention plans and or measures G a requirement in every wholesale water supply contract entered into or renewed after official adoption of the plan by either ordinance, resolution, or tariff , and including any contract extension, that each successive wholesale customer develop and implement a water conservation plan or water conservation measures using the applicable elements in this chapter.\n" +
                "If the customer intends to resell the water, the contract between the initial supplier and customer must provide that the contract for the resale of the water must have water conservation requirements so that each successive customer in the resale of the water will be required to implement water conservation measures in accordance with applicable provisions of this chapter H official adoption of the water conservation plan and goals, by ordinance, rule, resolution, or tariff, indicating that the plan reflects official policy of the supplier I any other water conservation practice, method, or technique which the supplier shows to be appropriate for achieving conservation and J documentation of coordination with the regional water planning groups, in order to ensure consistency with appropriate approved regional water plans.\n" +
                "b A water conservation plan prepared in accordance with the rules of the United States Department of Agriculture Natural Resource Conservation Service, the Texas Texas Commission on Environmental Quality Page 12 Chapter 288 - Water Conservation Plans, Drought Contingency Plans, Guidelines and Requirements State Soil and Water Conservation Board, or other federal or state agency and substantially meeting the requirements of this section and other applicable commission rules may be submitted to meet application requirements in accordance with a memorandum of understanding between the commission and that agency.\n" +
                "c An agricultural water user shall review and update its water conservation plan, as appropriate, based on an assessment of previous five-year and ten-year targets and any other new or updated information.\n" +
                "An agricultural water user shall review and update the next revision of its water conservation plan every five years to coincide with the regional water planning group.\n" +
                "Adopted November 14, 2012 Effective December 6, 2012 288.5.\n" +
                "Water Conservation Plans for Wholesale Water Suppliers.\n" +
                "A water conservation plan for a wholesale water supplier must provide information in response to each of the following paragraphs.\n" +
                "If the plan does not provide information for each requirement, the wholesale water supplier shall include in the plan an explanation of why the requirement is not applicable.\n" +
                "Redacted.\n" +
                "All water conservation plans for wholesale water suppliers must include the following elements A a description of the wholesaler's service area, including population and customer data, water use data, water supply system data, and wastewater data B specific, quantified five-year and ten-year targets for water savings including, where appropriate, target goals for municipal use in gallons per capita per day for the wholesaler's service area, maximum acceptable water loss, and the basis for the development of these goals.\n" +
                "The goals established by wholesale water suppliers under this subparagraph are not enforceable C a description as to which practice's and or device swill be utilized to measure and account for the amount of water diverted from the source's of supply D a monitoring and record management program for determining water deliveries, sales, and losses E a program of metering and leak detection and repair for the wholesaler's water storage, delivery, and distribution system Texas Commission on Environmental Quality Page 13 Chapter 288 - Water Conservation Plans, Drought Contingency Plans, Guidelines and Requirements F a requirement in every water supply contract entered into or renewed after official adoption of the water conservation plan, and including any contract extension, that each successive wholesale customer develop and implement a water conservation plan or water conservation measures using the applicable elements of this chapter.\n" +
                "If the customer intends to resell the water, then the contract between the initial supplier and customer must provide that the contract for the resale of the water must have water conservation requirements so that each successive customer in the resale of the water will be required to implement water conservation measures in accordance with applicable provisions of this chapter G a reservoir systems operations plan, if applicable, providing for the coordinated operation of reservoirs owned by the applicant within a common watershed or river basin.\n" +
                "The reservoir systems operations plans shall include optimization of water supplies as one of the significant goals of the plan H a means for implementation and enforcement, which shall be evidenced by a copy of the ordinance, rule, resolution, or tariff, indicating official adoption of the water conservation plan by the water supplier and a description of the authority by which the water supplier will implement and enforce the conservation plan and I documentation of coordination with the regional water planning groups for the service area of the wholesale water supplier in order to ensure consistency with the appropriate approved regional water plans.\n" +
                "2 Additional conservation strategies.\n" +
                "Any combination of the following strategies shall be selected by the water wholesaler, in addition to the minimum requirements of paragraph 1 of this section, if they are necessary in order to achieve the stated water conservation goals of the plan.\n" +
                "The commission may require by commission order that any of the following strategies be implemented by the water supplier if the commission determines that the strategies are necessary in order for the conservation plan to be achieved A conservation-oriented water rates and water rate structures such as uniform or increasing block rate schedules, and or seasonal rates, but not flat rate or decreasing block rates B a program to assist agricultural customers in the development of conservation pollution prevention and abatement plans C a program for reuse and or recycling of wastewater and or graywater and Texas Commission on Environmental Quality Page 14 Chapter 288 - Water Conservation Plans, Drought Contingency Plans, Guidelines and Requirements D any other water conservation practice, method, or technique which the wholesaler shows to be appropriate for achieving the stated goal or goals of the water conservation plan.\n" +
                "3 Review and update requirements.\n" +
                "The wholesale water supplier shall review and update its water conservation plan, as appropriate, based on an assessment of previous five-year and ten-year targets and any other new or updated information.\n" +
                "A wholesale water supplier shall review and update the next revision of its water conservation plan every five years to coincide with the regional water planning group.\n" +
                "Adopted November 14, 2012 Effective December 6, 2012 288.6.\n" +
                "Water Conservation Plans for Any Other Purpose or Use.\n" +
                "A water conservation plan for any other purpose or use not covered in this subchapter shall provide information where applicable about those practices, techniques, and technologies that will be used to reduce the consumption of water, prevent or reduce the loss or waste of water, maintain or improve the efficiency in the use of water, increase the recycling and reuse of water, or prevent the pollution of water.\n" +
                "Adopted April 5, 2000 Effective April 27, 2000 288.7.\n" +
                "Plans Submitted With a Water Right Application for New or Additional State Water.\n" +
                "a A water conservation plan submitted with an application for a new or additional appropriation of water must include data and information which 1 supports the applicant's proposed use of water with consideration of the water conservation goals of the water conservation plan 2 evaluates conservation as an alternative to the proposed appropriation and 3 evaluates any other feasible alternative to new water development including, but not limited to, waste prevention, recycling and reuse, water transfer and marketing, regionalization, and optimum water management practices and procedures.\n" +
                "b It shall be the burden of proof of the applicant to demonstrate that no feasible alternative to the proposed appropriation exists and that the requested amount of appropriation is necessary and reasonable for the proposed use.\n" +
                "Redacted.\n" +
                "Redacted.\n" +
                "Dallas.\n" +
                "Redacted.\n" +
                "Redacted.\n" +
                "09 2013 Fiscal Period Begin mm yyyy Period End mm yyyy J Calendar Period Begin mm yyyy Period End mm yyyy Check all of the following that apply to your entity LI Receive financial assistance of $500,000 or more from TWDB Have 3,300 or more retail connections RI Have a water right with TCEQ Page 1 of 9 4 Retail Customer Categories Residential Single Family Residential Multi-family Industrial Commercial Institutional.\n" +
                "Agricultural Recommended Customer Categories for classifying your customerwateruse.\n" +
                "Fordefinitions, refer to d c i M ry Cpn.\n" +
                "7 it 1.\n" +
                "For this reporting period, select the 171 Residential Single Family 171 Residential Multi-family Industrial cate ory's used to classify customer water use I Commercial E1 tien-a - RI gruai 2.\n" +
                "For this reporting period, enter the gallons of metered retail water used by each customer category.\n" +
                "If the Customer Category does not apply, enter zero or leave blank, Residential Single Family - Residential Multi-family Industrial Commercial.\n" +
                "Redacted.\n" +
                "Residential lndustnaL - Commercial Institutional Agricultural Total Retail Water Metered Page 2 of 9 Total Gallons During the Reporting Period Water Produced Water from permitted sources such as rivers, lakes, streams, and wells, Same as line 14 of the 144,321,717,172 water loss audit.\n" +
                "Wholesale Water Imported Purchased wholesale water transferred into the system.\n" +
                "Same as line 15 of the water o loss audit.\n" +
                "Wholesale Water Exported Wholesale water sold or transferred out of the system.\n" +
                "Same as line l6of the 0 water loss audit.\n" +
                "- System Input Total water supplied to system and 144 321 717.172available for retail use.\n" +
                "Produced Imported Exported System Input Total Retail Water Metered 122,768,307,000 Other Authorized Consumption Water that is authorized for other uses such as the following This water may be metered or unmetered.\n" +
                "Same as the total of lines 19, 20, and 21 of the water loss audit.\n" +
                "- back flushing - line flushing I ,.\n" +
                "JLR.J,.\n" +
                "JtL1,JJV - storage tank cleaning - municipal golf courses parks - fire department use - municipal government offices Total Authorized Use All water that has been authorized for use 130,268,847,000 Total Retail Water Other Authorized consumption Total Authorized Use Apparent Losses Water that has been consumed but not properly measured or billed.\n" +
                "Same as line 28 of the water loss audit.\n" +
                "485,103,343 Includes losses due to customer meter accuracy, systematic data discrepancy, unauthorized consumption such as theft Real Losses Physical losses from the distribution system prior to reaching the customer destination.\n" +
                "Same as line 29 of the water loss audit.\n" +
                "873,100.000 Includes phycical losses from system or mains, reported breaks and leaks, or storage overflow Unidentified Water Losses Unreported losses not known 12,694 666 829or quantified.\n" +
                "System Input Total Authorized Use Apparent Losses Real Losses Unidentified Water Losses 14,052,870,172 Total Water Loss Apparent Real Unidcnt5ied Total Water Loss Page 3 of 9 Targets and Goals Provide the specific and quantified five and ten-year targets as listed in your current Water Conservation Plan.\n" +
                "Target dates and numbers should match your current Water Conservation Plan.\n" +
                "1 f Target for Target for Achieve Date T Water l.oss Water Loss Percentagea a expressed in GPCD expressed in percentage Five-year targetdate 196 28 10 2019 Ten-year targetdate 195 27 10 2024 Gallons Per Capita er Day GPCD and Water Loss Provide current GPCD and water loss totals.\n" +
                "To see if you are making progress towards your stated goals, compare these totals to the above targets and goals.\n" +
                "Provide the population and residential water use of your service area.\n" +
                "PermanentTotal System Input in Gallons.\n" +
                "i Total GPCDPopulation 144,321,717,172 2,427,010 163 Water Produced Wholesale Imported - Wholesale Exported System Input Permanent Population - 365 1.\n" +
                "Permanent Population is the total permanent population of the service area, including single Family, multi-family, and group quarter populations.\n" +
                "Residential Use in Gallons Residential 1 Residential GPCD Single Family Multi-family Population 98 43,311 542000 1,213,600 Residential Use - Residential Population 365 1 Residential Population is the total residential population of the service area, including only single family and multifamily populations.\n" +
                "Permanent Water Loss Total Water Loss Population GPCD Percent2 14,052,870,172 2,427,010 16 10 Apparent Real Unidentified Total Water Loss 1 Total Water Loss Permanent Population 365 Water Loss GPCD 2 Total Water Loss Total System Input x 100 Water Loss Percentage Page 4 of 9 rgrjm cz t's As you complete this section, review your utility's water conservation plan to see if you are making progress towards meeting your stated goals.\n" +
                "2014 1.\n" +
                "What year did your entity adopt or revise the most recent Water Conservation Plan 2.\n" +
                "Does The Plan incorporate Benerr nt Prt Yes No 3.\n" +
                "Using the table below select the types of Best Management Practices or water conservation strategies actively administered during this reporting period and estimate the savings incurred in implementing water conservation activities and programs.\n" +
                "Leave fields blank if unknown.\n" +
                "Redacted.\n" +
                "Regulatory and Enforcement Prohibition on Wasting Water Other, please describe 31.929.810.000 Total Gallons of Water Saved - 32,000,456,000 4.\n" +
                "For this reporting period, provide the estimated gallons of direct or indirect reuse activities.\n" +
                "Reuse Activity Estimated Volume in gallons On-site irri ation.\n" +
                "Redacted.\n" +
                "I. Industrial.\n" +
                "Redacted.\n" +
                "55,562,000 A ricultural.\n" +
                "Other, please describe Nonpotable water uses for 4 200 000 000 on-site irrigation, plant Total Volume of Reuse 4,255,562,000 5.\n" +
                "For this reporting period, estimate the savings from water conservation activities and programs.\n" +
                "Gallons Gallons Total Volume of Dollar Value Saved Conserved Recycled Reused Water Saved1 of Water Saved2 32,000,456,000 4,255,562,000 36,256,018,000 $ 25,125,408 1, Estimated Gallons Saved Conserved Estimated Gallons Recycled Reused Total Volume Saved 2 Estimate this value by taking into account water savings, the Cost of treatment or purchase of water and deferred capital costs due to conservation, Page 6 of 9 6.\n" +
                "During this reporting period, did your rates or rate structure change Yes Select the type of rate pricing structures used.\n" +
                "Check all that apply.\n" +
                "QN0 Uniform Rates Flat Rates ZI Inclining Inverted Block Rates Li L Declining Block Rates U Seasonal Rates Water Budget Based Rates Excess Use Rates Drought Demand Rates Tailored Rates Surcharge - usage demand Surcharge - seasonal Surcharge - drought Other, please describe 7, For this reporting period, select the public awareness or educational activities used.\n" +
                "Redacted.\n" +
                "During this reporting period, how many leaks were repaired in the system or at service connections 503 Select the main cause's of water loss in your system.\n" +
                "Leaks and breaks Unmetered utility or city uses Master meter problems Customer meter problems Record and data problems Other Other 2.\n" +
                "For this reporting period, provide the following information regarding meter repair Type of Meter Total Number Total Tested Total Repaired Total Replaced Production 310 018 5 556 5,556 20,075Meters 30,594 1,314 1,314 2,595 MeterslY2or 279,424 4,242 4,242 23,472smaller 3.\n" +
                "Does your system have automated meter reading Yes No Page 8 of 9 1.\n" +
                "in your opinion, how would you rank the effectiveness of your conservation activities Residential Customers Industrial Customers 0 Institutional Customers 0 Commercial Customers Agricultural Customers 2.\n" +
                "During the reporting period, did you implement your Drought Contingency Plan Q Yes No If yes, how many days were water use restrictions in effect If es, check the reason's for implementing your Drought Contingency Plan.\n" +
                "Redacted.\n" +
                "Select the areas for which you would like to receive more technical assistance Best Management Practices Drought Contingency Plans Landscape Irrigation Leak Detection and Equipment Rainwater Harvesting Rate Structures SUBMIT Educational Resources Water Conservation Annual Reports Water Conservation Plans Water 10 Know Your Water Water Loss Audits Recycling and Reuse Customer Classification Less Than Somewhat Highly Effective Effective Effective 0 0 0 a 0 0 Does Not Apply 0 0 0 0 0 Page 9 of 9 Water Conservation Plan Annual Report Wholesale Water Supplier Name of Entity City of Dallas Water Utilities 0570004 Public Water Supply Identification Number PWS ID P0001 CCN Number 12468 etc..\n" +
                "Redacted.\n" +
                "Dallas.\n" +
                "Redacted.\n" +
                "Water Conservation Form Completed By Title 3.11.14 Date orting Period check only one 10 2012 09 2013 C Fiscal Period Begin mm yyyy Period End mm yyyy Calendar Period Begin mm yyyy Period End mm yyyy Check all that apply Received financial assistance of $500,000 or more from TWDB Have 3,300 or more retail connections Have a surface water right with TCEQ Page lof 5 1.\n" +
                "For this reporting period, provide the total volume of wholesale water exported transferred or sold 55741239OOO gallons 2.\n" +
                "For this reporting period, does your billing accounting system have the capability to classify customers into the Wholesale Customer Categories 0 Yes No 3, For this reporting period, select the category's used to calculate wholesale customer water usage Municipal Industrial Commercial Institutional Agricultural 4.\n" +
                "For this reporting year, enter the gallons of WHOLESALE water exported transferred or sold.\n" +
                "Enter zero if a Customer Category does not apply.\n" +
                "Gallons Exported Number of Wholesale Customer Category transferred or sold Customers Municipal 55,741239,OOO Industrial 0 Commercial 0 Institutional 0 Agricultural 0 Total 55741,239,OOO 0 Wholesale Customer Cateaories Municipal Industrial Commercial Institutional Agricultural Recon mended Cud mer C feg r m Fur assy rg c fr e waMr ve.\n" +
                "deb s, refer c 1 Page 2 of 5 Total Gallons During the Reporting Period Water Produced Water from permitted sources such as rivers, lakes, streams, and wells.\n" +
                "142,878,500,000 Wholesale Water Imported Purchased wholesale water transferred into the system.\n" +
                "0 System input Total water supplied to system and available 142,878,500,000 for use.\n" +
                "Produced Imported System Input Wholesale Water Exported Wholesale water sold or transferred out of the system.\n" +
                "55,741,239,000 152,715,7231 Wholesale Water Exported 365 Gallons Per Day Population Estimated total population for municipal customers.\n" +
                "1,213,410 Municipal Gallons Per Capita Per Day 126 Municipal Exported Municipal Population 365 Municipal Gallons Per Capita Per Day Provide the specific and quantified five and ten-year targets as listed in your most current Water Conservation Plan.\n" +
                "Date to Achieve Specified and Quantified Targets Target Five-yeartarget 2019 196 Ten-year target 2024 195 Page 3of5 1.\n" +
                "Water Conservation Plan What year did your entity adopt or revise their most recent Water Conservation Plan 2014 Does The Plan incorporate BetM pntPractic ctice Yes No 2.\n" +
                "Water Conservation Programs Has our entity implemented any type of water conservation activity or program Yes No If yes, select the type's of Best Management Practices or water conservation strategies implemented during this reporting period.\n" +
                "Redacted.\n" +
                "3.\n" +
                "Recycle Reuse Water or Wastewater Effluent For this reporting period, provide direct and indirect reuse activities.\n" +
                "Reuse Activity I Estimated Volume in gallons On-site irrigation Plant washdown Chlorination de-chlorination Industrial Landscape irrgation park golf courses 55562000 Agricultural OtherL please describe Non-potable water uses for on-site irr 4200000000 Estimated Volume of Reuse 4255,562O00 Page 4 of 5 4.\n" +
                "Water Savings For this reporting period, estimate the savings that resulted from water conservation activities and programs.\n" +
                "Redacted.\n" +
                "Program Effectiveness In your opinion, how would you rank the overall effectiveness of your conservation programs and activities Less Than Effective p Somewhat Effective Highly Effective Does Not Apply c 6.\n" +
                "What might your entity do to improve the effectiveness of your water conservation program 7.\n" +
                "Select the areas for which you would like to receive technical assistance Agricultural Best Management Practices Wholesale Best Management Practices Industrial Best Management Practices Drought Contingency Plans Landscape Efficient Systems Leak Detection and Equipment Educational Resources SUBMIT Water Conservation Plans Water lQ Know Your Water Water Loss Audits Rainwater Harvesting Systems Recycling and Reuse PageS of 5 TCEQ-20646 rev. 09-18-2013 Page 2 of 11 Please check all of the following that apply to your entity A surface water right holder of 1,000 acre-feet year or more for non-irrigation uses A surface water right holder of 10,000 acre-feet year or more for irrigation uses Important If your entity meets the following description, please skip page 3 and go directly to page 4.\n" +
                "Your entity is a Wholesale Public Water Supplier that ONLY provides wholesale water services for public consumption.\n" +
                "For example, you only provide wholesale water toother municipalities or water districts.\n" +
                "TCEQ-20646 rev. 09-18-2013 Page 3 of 11 Fields that are gray are entered by the user.\n" +
                "Select fields that are white and press F9 to updated fields.\n" +
                "Water Use Accounting Retail Water Sold All retail water sold for public use and human consumption.\n" +
                "Helpful Hints There are two options available for you to provide the requested information.\n" +
                "Both options ask the same information however, the level of detail and breakdown of information differs between the two options.\n" +
                "Please select just one option that works best for your entity and fill in the fields as completely as possible.\n" +
                "For the five-year reporting period, enter the gallons of RETAIL water sold in each major water use category.\n" +
                "Use only one of the following options.\n" +
                "Redacted.\n" +
                "Redacted.\n" +
                "Redacted.\n" +
                "Single Family Multi-Family 136,360,220,000 Commercial Please select all of the sectors that your account for as Commercial.\n" +
                "Commercial Multi-Family Industrial Institutional 174,853,715,000 Industrial Please select all of the sectors that your account for as Industrial.\n" +
                "Industrial Commercial Institutional 25,321,252,000 Other Please select all of the sectors that your account for as Other.\n" +
                "Commercial Multi-Family Industrial Institutional 287,066,794,000 TOTAL Retail Water Sold 1 Total Billed Volume 623,601,981,000.00 1.\n" +
                "Res Com Ind Other Retail Water Sold TCEQ-20646 rev. 09-18-2013 Page 4 of 11 Wholesale Water Exported Wholesale water sold or transferred out of the distribution system.\n" +
                "For the five-year reporting period, enter the gallons of WHOLESALE water exported to each major water use category.\n" +
                "1.\n" +
                "Mun Agr Ind Com Ins Wholesale Water Exported Water Use Category Gallons of Exported Wholesale Water Municipal Customers 0 Agricultural Customers Industrial Customers Commercial Customers Institutional Customers TOTAL Wholesale Water Exported 1 0.00 TCEQ-20646 rev. 09-18-2013 Page 5 of 11 System Data Total Gallons During the Five-Year Reporting Period Water Produced Volume produced from own sources 733,356,000000 Wholesale Water Imported Purchased wholesale water imported from other sources into the distribution system 0 Wholesale Water Exported Wholesale water sold or transferred out of the distribution system Insert Total Volume calculated on Page 4 0 TOTAL System Input Total water supplied to the infrastructure 733,356,000,000.00 Produced Imported Exported System Input Retail Water Sold All retail water sold for public use and human consumption Insert Total Residential Use from Option 1 or Option 2 calculated on Page 3 623,601,981,000 Other Consumption Authorized for Use but not Sold - back flushing water-line flushing - storage tank cleaning - golf courses - fire department use - parks - municipal government offices 46,231,500,000 TOTAL Authorized Water Use All water that has been authorized for use or consumption.\n" +
                "669,833,481,000.00 Retail Water Sold Other Consumption Total Authorized Apparent Losses Water that has been consumed but not properly measured Includes customer meter accuracy, systematic data discrepancy, unauthorized consumption such as theft 1,559,004,953 Real Losses Physical losses from the distribution system prior to reaching the customer destination Includes physical losses from system or mains, reported breaks and leaks, storage overflow 3,490,000,000 Unidentified Water Losses 58,473,514,047.00 System Input- Total Authorized - Apparent Losses - Real Losses Unidentified Water Losses TOTAL Water Loss 63,522,519,000.00 Apparent Real Unidentified Total Water Loss Fields that are gray are entered by the user.\n" +
                "Select fields that are white and hit F9 to updated fields.\n" +
                "TCEQ-20646 rev. 09-18-2013 Page 6 of 11 In the table below, please provide the specific and quantified five and ten-year targets for water savings listed in your water conservation plan.\n" +
                "Date Target for Total GPCD Target for Water Loss expressed in GPCD Target for Water Loss Percentage expressed in Percentage Five-year target date 9 30 2019 203 20 10 Ten-year target date 9 30 2024 195 20 10 Are targets in the water conservation plan being met Yes No If these targets are not being met, provide an explanation as to why, including any progress on these targets Click hereto enter text.\n" +
                "Gallons per Capita per Day GPCD and Water Loss Compare your current gpcd and water loss to the above targets and goals set in your previous water conservation plan.\n" +
                "Redacted.\n" +
                "This includes single family, multi-family, and group quarter populations.\n" +
                "Total Residential Use Permanent Population Residential GPCD 224,713,902,900 1,213,600 101.46 Residential Use Residential Population 5 365 Residential Population is the total residential population of the service area including single multi-family population.\n" +
                "Fields that are gray are entered by the user.\n" +
                "Select fields that are white and hit F9 to update fields.\n" +
                "TCEQ-20646 rev. 09-18-2013 Page 7 of 11 Fields that are gray are entered by the user.\n" +
                "Select fields that are white and hit F9 to updated fields.\n" +
                "Total Water Loss Total System Input in Gallons Permanent Population Water Loss calculated in GPCD 1 Percent 2 63,522,519,000 Apparent Real Unidentified Total Water Loss 623,601,981,000.00 Water Produced Wholesale Imported - Wholesale Exported 1,213,600 28.68 10 1.\n" +
                "Redacted.\n" +
                "Total Water Loss Total System Input x 100 Water Loss Percentage Water Conservation Programs and Activities As you complete this section, please review your water conservation plan to see if you are making progress towards meeting your stated goals.\n" +
                "1.\n" +
                "Water Conservation Plan What year did your entity adopt, or revise, their most recent water conservation plan 2010 Does the plan incorporate Best Management Practices Yes No 2.\n" +
                "Water Conservation Programs For the reporting period, please select the types of activities and programs that have been actively administered, and estimate the expense and savings that incurred in implementing the conservation activities and programs for the past five years.\n" +
                "Redacted.\n" +
                "Reuse Water or Wastewater Effluent For the reporting period, please provide the following data regarding the types of direct and indirect reuse activities that were administered for the past five years Reuse Activity Estimated Volume in gallons On-site irrigation Plant washdown Chlorination de-chlorination Industrial Landscape irrigation parks, golf courses 311,039,067 Agricultural Other, please describe Non-potable water uses for onsite irrigation, plant washdown and other processes.\n" +
                "18,510,000,000 TCEQ-20646 rev. 09-18-2013 Page 9 of 11 Estimated Volume of Recycled or Reuse 18,821,039,067 4.\n" +
                "Water Savings For the five-year reporting period, estimate the total savings that resulted from your overall water conservation activities and programs Estimated Gallons Saved Total from Conservation Programs Table Estimated Gallons Recycled or Reused Total from Reuse Table Total Volume of Water Saved 1 Dollar Value of Water Saved 2 116,862,000,000 18,821,039,067 135,683,039,067 $105,055,000 1.\n" +
                "Estimated Gallons Saved Estimated Gallons Recycled or Reused Total Volume Saved 2.\n" +
                "Estimate this value by taking into account water savings, the cost of treatment or purchase of your water, and any deferred capital costs due to conservation.\n" +
                "5.\n" +
                "Conservation Pricing Conservation Rate Structures During the five-year reporting period, have your rates or rate structure changed Yes No Please indicate the type of rate pricing structures that you use Uniform rates Water Budget Based rates Surcharge - seasonal Flat rates Excess Use Rates Surcharge - drought Inclining Inverted Block rates Drought Demand rates Surcharge - usage demand Declining Block rates Tailored rates Seasonal rates 6.\n" +
                "Public Awareness and Education Program For the five-year reporting period, please check the appropriate boxes regarding any public awareness and educational activities that your entity has provided Implemented Number Unit Example Brochures Distributed 10,000 year Example Educational School Programs 50 students month Brochures Distributed 319,351 Messages Provided on Utility Bills 15,948,000 Press Releases 24 TV Public Service Announcements 16,055 Radio Public Service Announcements 13,537 Educational School Programs 80,422 participants Displays, Exhibits, and Presentations 1,047 TCEQ-20646 rev. 09-18-2013 Page 10 of 11 Community Events 283 Social Media campaigns 14 Facility Tours 5 Other Print Advertisements 323 7.\n" +
                "Leak Detection During the five-year reporting period, how many leaks were repaired in the system or at service connections 66,106 Please check the appropriate boxes regarding the main cause of water loss in your system during the reporting period Leaks and breaks Un-metered utility or city uses Master meter problems Customer meter problems Record and data problems Other Other 8.\n" +
                "Universal Metering and Meter Repair For the five-year reporting period, please provide the following information regarding meter repair Total Number Total Tested Total Repaired Total inInIReplaced Production Meters 310,018 41,017 41,017 82,034 Meters larger than 1 30,594 7,872 7,872 15,744 Meters 1 or smaller 279,424 33,145 33,145 66,290 Does your system have automated meter reading Yes No TCEQ-20646 rev. 09-18-2013 Page 11 of 11 9.\n" +
                "Conservation Communication Effectiveness In your opinion, how would you rank the effectiveness of your conservation activities in reaching the following types of customers for the past five years 10.\n" +
                "Drought Contingency and Emergency Water Demand Management During the five-year reporting period, did you implement your Drought Contingency Plan Yes No If yes, indicate the number of days that your water use restrictions were in effect 133 If yes, please check all the appropriate reasons for your drought contingency efforts going into effect.\n" +
                "Water Supply Shortage Equipment Failure High Seasonal Demand Impaired Infrastructure Capacity Issues Other If you have any questions on how to fill out this form or about the Water Conservation program, please contact us at 512 239-4691.\n" +
                "Individuals are entitled to request and review their personal information that the agency gathers on its forms.\n" +
                "They may also have any errors in their information corrected.\n" +
                "To review such information, contact us at 512-239-3282.\n" +
                "Do not have activities or programs that target this type customer.\n" +
                "Less Than Effective Somewhat Effective Highly Effective Residential Customers Industrial Customers Institutional Customers Commercial Customers Agricultural Customers";

        InformationExtractor extractor = new InformationExtractor();
        extractor.getEntityRelations(text2, null, null, null);
    }
}
