package nlp;

import common.Tools;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.common.SolrDocument;

import java.util.*;
import java.util.stream.Collectors;

public class InformationExtractor {
    private final static Logger logger = LogManager.getLogger(InformationExtractor.class);
    private StanfordCoreNLP pipeline;

    public InformationExtractor() {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse,natlog,openie");
        props.setProperty("threads", "4");
        pipeline = new StanfordCoreNLP(props);
    }

    public List<EntityRelation> getEntityRelations(String text, String docId, List<NamedEntity> entities, List<Coreference> coreferences) {
        final double similarityThreshold = 0.5;
        final double lineNumRange = 5;
        Annotation document = new Annotation(text);
        pipeline.annotate(document);

        List<EntityRelation> relatedEntities = new ArrayList<>();
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
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

        String text2 = "Jordan Valley Water Conservancy District Annual Report Table of Contents Board of Trustees Message Executive Staff Member Agencies Agencies Served by JVWCD Sources Deliveries Facilities Significant Accomplishments Water Quality Water Supply Southwest Jordan Valley Groundwater Project Conservation Safety Increased Capacity Financial Staff JORDAN VALLEY WATER CONSERVANCY DISTRICT Jordan Valley Water Conservancy District Jordan Valley was created in under the Water Conservancy Act and is governed by a board of eight trustees who represent seven geographical divisions.\n" +
                "They are nominated by either the Salt Lake County Council or a city council depending upon the division they represent.\n" +
                "Each trustee is appointed by the Governor for a four year term.\n" +
                "Jordan Valley is primarily a wholesaler of water to cities and improvement districts within Salt Lake County.\n" +
                "It also has a retail service area in unincorporated areas of the county.\n" +
                "Jordan Valley delivers percent of its municipal water on a wholesale basis to cities and water districts and percent on a retail basis to unincorporated areas of Salt Lake County.\n" +
                "In addition Jordan Valley treats and delivers water to Metropolitan Water District of Salt Lake Sandy for delivery to Salt Lake City and Sandy City even though neither city is within Jordan Valleys service boundaries.\n" +
                "Jordan Valley also delivers untreated water to irrigators in Salt Lake and Utah Counties to meet commitments under irrigation exchanges.\n" +
                "Gary Swensen Taylorsville City Board of Trustees Richard W. McDonald Granite Park Holladay Murray Union South Cottonwood Willow Creek South Salt Lake White City Water Improvement District and unincorporated areas.\n" +
                "B. Jeff Rasmussen Draper Herriman and Midvale Margaret K. Peterson West Valley City Lyle C. Summers West Jordan City Steven L. Taggart Vice Chair West Valley City Royce A. Gibson Finance Committee Chair Kearns and Magna Dale F. Gardiner Chair Bluff dale Riverton and South Jordan Photograph by Quinn Farley After our recent experience with five years of drought we enjoyed a second year of good water supply during.\n" +
                "This provided an opportunity for Jordan Valley to rest its wells and allow the underground aquifer reservoir to recover from the effects of drought.\n" +
                "Although early data for indicates below normal snowpack conditions we ended calendar year with a good water supply due to substantial reserves in storage.\n" +
                "The retirement of David Ovard as General Manager was a significant event at the end of.\n" +
                "Dave s accomplishments and contributions to Jordan Valleys success are extensive and are highlighted in this report.\n" +
                "Jordan Valley will miss Dave The year also marked a continuation of rapid population growth in Jordan Valleys western and southern service areas.\n" +
                "Jordan Valley and its member agencies worked together in planning for important new infrastructure to serve these fast growing areas.\n" +
                "Jordan Valley has made significant progress in infrastructure engineering and construction for these areas and sees a need for these efforts to continue in coming years.\n" +
                "As population continues to grow rapidly Jordan Valley has continued to lead in the implementation of water conservation education and outreach programs.\n" +
                "The public responded by achieving nearly a percent reduction in water use by but in water usage increased due to perceptions of the drought ending.\n" +
                "Jordan Valley will continue its efforts to assist its member agencies and customers in the quest to be more water efficient in coming years.\n" +
                "Among those efforts in is the planned expansion of the Conservation Garden Park at Jordan Valley.\n" +
                "Looking Forward David Ovard has been the General Manager of Jordan Valley for the past years.\n" +
                "His accomplishments are many and varied.\n" +
                "He leaves Jordan Valley in excellent condition after years of service.\n" +
                "Richard Bay has been appointed by the Board of Trustees as the new General Manager for Jordan Valley.\n" +
                "With years of experience and having mentored under David for many years Richard brings experience and foresight to the position.\n" +
                "photos Immediate right is David Ovard past General Manager far right is Alan Pack ard Assistant General Manager Richard Bay General Manager and Bart Forsyth Assistant General Manager.\n" +
                "Photograph by Quinn Farley Executive Staff Yvette Amparo Espinoza Administrative Assistant III Linda Townes CommunicationsPR Specialist Jeff Bryant Water Supply Department Manager Dirk Anderson Distribution Department Manager Debbie Ericksen Human Resources Manager Dave Rice Conservation Programs Coordinator Photography by Quinn Farley Marilyn Payan Executive Assistant Neil Cox Assistant Treasurer Jason Brown IT Department Manager Shazelle Terry Treatment Department Manager Jackie Maas Administrative Assistant III Dave Martin Chief Financial Offi cerController Mark Atencio Engineering Department Manager Reid Lewis Attorney Member Agencies Bluff dale City Mayor Claudia Anderson Draper City Mayor Darrell Smith John F. Hendrickson City Manager Granger Hunter Improvement District Gordon W. Evans Board Chair David Warr General Manager Herriman City Mayor J. Lynn Crane Rod Brocious City Engineer Hexcel Corporation Ken Bunkowski General Manager Kearns Improvement District Rodney Bushman Board Chair Carl Eriksson General Manager Magna Water Company Dan Tuttle Board Chair Ed Hansen General Manager Midvale City Mayor JoAnn Seghini Kane Loader City Administrator Riverton City Mayor William Applegarth Lance Blackwood City Manager Sandy City Mayor Tom Dolan Shane Pace Public Utilities Director City of South Jordan Mayor Kent Money Rick Horst City Manager City of South Salt Lake Mayor Robert D. Gray Dennis Peay Public Works Director Taylorsville Bennion Improvement District Benjamin Behunin Board Chair Floyd J. Nielsen General Manager Utah State Department of Corrections Greg Peay Director of Facilities and Construction Rick Johnson Deputy Warden of Support Services WaterPro Inc..\n" +
                "Stephen L. Tripp President Bruce Cuppett CEO West Jordan City Mayor David Newton Gary Luebbers City Manager White City Water Improvement District Paulina Flint Board Chair Paul Ashton General Manager Willow Creek Country Club Alex Nicolaidis General Manager Agencies Served by Jordan Valley Water Conservancy District Receives water from facilities co owned with and operated by Jordan Valley.\n" +
                "Parts of these areas receive service from Jordan Valley on a retail basis.\n" +
                "Sources Pictured above is Bell Canyon Creek located in Bell Canyon in the southeast area of Salt Lake County.\n" +
                "It is one of the largest sources of mountain stream water to Southeast Regional Water Treatment Plant.\n" +
                "Other major source water for Jordan Valley includes Provo River Upper Provo River reservoirs high Uinta lakes Weber River Duchesne River Local Wasatch streams Groundwater Salt Lake Valley wells Long term conservation ethics in our community will play an important role in the amount of water avail able for future generations.\n" +
                "Conservation also affects the cost of future water infrastructure and new source development by delaying the need for new sources of water.\n" +
                "Jordan Valleys water supply has increased significantly in the last years.\n" +
                "Page of this report discusses some of the ways that supply has increased.\n" +
                "Continued ingenuity is paramount to providing the public with clean safe drinking water.\n" +
                "Having pristine sources such as the Provo River also helps.\n" +
                "Pictured above are the Provo Falls off the Mirror Lake Highway.\n" +
                "Municipal industrial water supplies AF AF Jordanelle Reservoir Central Utah Project a Deer Creek Reservoir Provo River Project b storage extra allotment Upper Provo River reservoirsa Echo Reservoirc Provo River direct flows Weber River direct flows Local Wasatch streams Groundwater wells Subtotal for Municipal Industrial supplies Irrigation water supplies Jordanelle Reservoir Central Utah Project a Deer Creek Reservoir Provo River Project b storage extra allotment Upper Provo River reservoirsa Echo Reservoirc Provo River direct flows Weber River direct flows Utah Lake Subtotal for irrigation TOTAL ALL SUPPLIES Total water treated or transported for other agencies TOTAL ALL WATER Municipal Industrial Water S li Irrigation Water Supplies TOTAL AL SUP LIES TOTAL TER a Provo River sources b Weber Duchesne and Provo River sources c Weber River sources Jordan Valley works to provide each of its wholesale member agencies with the highest quality water possible allowing them to then provide their customers with the same.\n" +
                "Jordan Valley also delivers retail water to over customers as well as irrigation and raw water to several agencies in the Salt Lake Valley.\n" +
                "Such large amounts of water require enormous storage capacity not only to satisfy demands but to provide fire protection as well.\n" +
                "Shown above is the interior of a million gallon storage reservoir which provides water for much of the west side of the Salt Lake Valley.\n" +
                "Deliveries Wholesale deliveries AF AF Bluff dale City Draper City Granger Hunter Improvement District Herriman City Hexcel Corporation Kearns Improvement District Magna Water Company Midvale City Riverton City Sandy City City of South Jordan City of South Salt Lake Taylorsville Bennion Improvement District Utah State Department of Corrections WaterPro Inc..\n" +
                "West Jordan City White City Water Improvement District Willow Creek Country Club Subtotal for wholesale JVWCD retail area Holladay Murray Sandy South Salt Lake and unincorporated county JVWCD use JVWCD losses Subtotal for deliveries use and loss Irrigation raw water AF AF Utah State Department of Public Safety Staker Parsons Company Welby Jacob Water Users Co..\n" +
                "Provo Reservoir Canal losses Subtotal for irrigation raw water Total delivered water MI water treated or trans ported for other agencies AF AF Metropolitan Water District of Salt Lake Sandy Taylorsville Bennion Improvement District West Jordan City Subtotal for treated or transported water Total water delivered treated or transported Wholesale deliveries Irrigation and raw water MI water treated or trans ported for other agencies Facilities.\n" +
                "Upper Provo River Reservoirs Once converted to small storage reservoirs the majority of these Uinta lakes have now been rehabilitated and the storage rights moved to Jordanelle Reservoir.\n" +
                "Jordan Valley is a major stockholder in the water rights for these lakes.\n" +
                "WeberProvo Rivers Diversion This canal conveys water from the Weber River and Echo Reservoir to Jordan Valley.\n" +
                "It is also used by the Provo River Water Users Association for the diversion of Weber River water to supply Deer Creek Reservoir.\n" +
                "Jordanelle Reservoir With a capacity of acre feet AF this reservoir was built by the U.S. Bureau of Reclamation is operated by Central Utah Water Conservancy District CUWCD and collects and stores Central Utah Project CUP water rights on the Provo River.\n" +
                "Jordan Valley receives an average of AF of CUP water annually.\n" +
                "Deer Creek Reservoir This reservoir is a feature of the Provo River Project and has a capacity of AF.\n" +
                "Jordan Valley is entitled to water stored in this reservoir which originates from the Provo Weber and Duchesne rivers.\n" +
                "Salt Lake Aqueduct This inch diameter pipeline is owned and operated by Metropolitan Water District of Salt Lake Sandy MWDSLS.\n" +
                "It conveys Provo River water from Deer Creek Reservoir to service areas of MWDSLS and Jordan Valley.\n" +
                "Southeast Regional Water Treatment Plant Jordan Valleys MGD facility treats water from the Salt Lake Aqueduct and local mountain streams.\n" +
                "Little Cottonwood Treatment Plant MWDSLS s MGD plant delivers treated water to Jor dan Valley and MWDSLS service areas.\n" +
                "Well Field This aquifer is the source of high quality groundwater for Jordan Valley and many municipalities.\n" +
                "Jordan Aqueduct This inch pipeline is operated by Jordan Valley on behalf of itself and MWDSLS.\n" +
                "It conveys water from Deer Creek and Jordanelle reservoirs and the Provo River to Jordan Valley Water Treatment Plant.\n" +
                "Jordan Narrows Pump Station Owned and operated by Jordan Valley this station pumps Utah Lake water into the Welby and Jacob canals for irrigation purposes as part of a large irrigation water exchange.\n" +
                "Jordan Valley Water Treatment Plant This MGD plant was built by CUWCD and is operated by Jordan Valley on behalf of itself and MWDSLS.\n" +
                "It supplies water to many communities in Salt Lake Valley.\n" +
                "It is the largest water treatment plant in Utah.\n" +
                "Equalization Reservoirs Booster Stations These widely dispersed facilities store and pump water to Jordan Valleys customers.\n" +
                "Jordan Aqueduct Terminal Reservoir At million gallons this drinking water storage facility has the largest capacity in the state.\n" +
                "Bingham Canyon Water Treatment Plant In cooperation with Kennecott Utah Copper Jordan Valley receives treated potable water from this reverse osmosis water treatment plant.\n" +
                "Jordan Valley operates and maintains dozens of facilities throughout the valley.\n" +
                "District personnel take pride in their upkeep sustaining a rigorous schedule of continuous maintenance and improvements.\n" +
                "photo Shown above is the Jordan Narrows Pump Station described in paragraph number.\n" +
                "ane ree nee rer Poe poe rer Poe erran eran eee eee ree nee eee ane erran Significant Accomplishments to During Dave Ovard s tenure as General Manager Jordan Valley has seen many changes challenges and improvements.\n" +
                "Throughout this publication we will highlight some of those accomplishments sharing with you the tremendous growth and achievements during Dave s years of service as General Manager.\n" +
                "Due to the efforts highlighted here Jordan Valley is poised for quality growth and success.\n" +
                "Jordan Valley has been placed on a solid financial footing through sound financial management and planning as evidenced by its year Financial Plan and year Capital Improvements Plan.\n" +
                "As a result Jordan Valleys bond rating has increased from A to AA.\n" +
                "Structure for growth of Jordan Valley and employee successes has been established and maintained through the development of professional administrative policies and procedures.\n" +
                "The firm water supply of Jordan Valley has been in creased by acre feet including ULS water.\n" +
                "Source capacity has been increased by percent through increased treatment plant and well capacities.\n" +
                "Through its Slow the Flow and related conservation programs Jordan Valley has led the way for water conservation advances throughout the state of Utah.\n" +
                "Jordan Valley has improved its working relationships with member agencies sister agencies state and federal agencies the public and the news media.\n" +
                "Jordan Valley has developed a professional staff that is respected throughout the state.\n" +
                "The safety record of Jordan Valley has improved substantially.\n" +
                "Jordan Valley has received a number of awards and recognitions acknowledging its excellence.\n" +
                "Total Wholesale Water Deliveries AF AF Total Water Deliveries AF AF Municipal Minimum Purchase Contracts AF AF Total Revenues Total OM Expense Debt Service Long Term Debt Bond Rating A AA Total Assets Property Owned Parcels Acres Firm Water Supply AF AF Including ULS Source Capacity Surface Water Groundwater MGD MGD MGD MGD System Storage MG MG Total Wells Total Booster Stations ULS or Utah Lake System is a future water supply that will be provided to Jordan Valley as part of the Central Utah Project CUP.\n" +
                "Water Quality In Jordan Valley established the Jordan Valley Treatment Plant Laboratory Lab to perform analyses and monitor the water quality of its own sources.\n" +
                "Initially the Lab was capable of per forming only a handful of basic water quality tests on District sources.\n" +
                "Currently the Lab can test for almost different compounds and analyzes almost samples a year for itself and several of its member agencies.\n" +
                "photo Top left in addition to online continuous monitoring Jordan Valley personnel regularly collect grab samples in the system to ensure water quality.\n" +
                "Bottom left shows how regulations have increased since the inception of the Safe Drinking Water Act in.\n" +
                "Water quality regulations have increased significantly over the past years with the and amendments to the Safe Drinking Water Act of.\n" +
                "During this time EPA has added regulations for surface water and groundwater involving organic and inorganic compounds lead copper turbidity disinfectants and disinfection by products filtration protozoa bacteria viruses radionuclides fluoride source water protection and unregulated contaminants.\n" +
                "Additional regulations are slated for the coming years.\n" +
                "In order to stay ahead of these regulations and meet customers increasing expectations Jordan Valley continually researches and implements new treatment technologies.\n" +
                "Some of the most significant projects impacting water quality completed in the last years include Design and installation of a pilot plant for various treatment studies at Jordan Valley Water Treatment Plant JVWTP.\n" +
                "Addition of a high rate clarification process at Southeast Regional Water Treatment Plant SERWTP to allow additional treatment of mountain stream sources.\n" +
                "Completion of a full scale study to determine the effectiveness of chlorine dioxide as the primary disinfectant at JVWTP to reduce disinfection by products.\n" +
                "Design of a new chlorine dioxide system at JVWTP will begin in.\n" +
                "A full scale study of poly aluminum chloride PACl as the primary coagulant at SERWTP.\n" +
                "PACl is now being used at both SERWTP and JVWTP.\n" +
                "Installation of additional water quality monitoring stations throughout Jordan Valleys water trans mission system.\n" +
                "Regulated Contaminants and Processes Water Supply Thousand Acre Feet Jordan Valleys water supply has increased by approximately acre feet in the last years.\n" +
                "With these increased sources the supply of water to much of the Salt Lake Valley has been made secure well into the future.\n" +
                "With improved conservation efforts the date for pursuing additional supplies may be even later than currently anticipated.\n" +
                "Even with all the water supply sources utilized by Jordan Valley it will not be enough for future demands.\n" +
                "In the last years Jordan Valley has increased its firm water supply by approximately acre feet or roughly percent.\n" +
                "With this increased supply and conservation results achieved the need to develop even more costly future supplies is diminished.\n" +
                "Some supplies that have been developed include additional groundwater sources imported surface water from irrigation exchanges and groundwater remediation such as the South west Jordan Valley Groundwater Project.\n" +
                "Increase in Water Supply Since Southwest Jordan Valley Groundwater Project The Southwest Jordan Valley Groundwater Project a joint project with Kennecott Utah Copper is designed to remediate contaminated water in two groundwater plumes in the southwestern portion of the Salt Lake Val ley.\n" +
                "In the spring of Jordan Valley began exploratory drilling of the deep wells in the eastern area of contamination known as Zone B. Drilling of three wells provided information regarding the geology for the drilling of seven deep production wells that began later in.\n" +
                "In May of the Bingham Canyon Water Treatment Plant reverse osmosis treatment plant began operation photo above.\n" +
                "This plant was constructed by Kennecott Utah Copper Corporation to treat water from the west ern Zone A groundwater sulfate plume.\n" +
                "This plant has a capacity of gallons per minute and produces acre feet of water per year.\n" +
                "In the fall Jordan Valley began drilling five deep wells in Zone B. These wells are along West between South and South.\n" +
                "Each well is expected to produce between gpm and gpm.\n" +
                "The drilling of these wells is to be complete in the spring of.\n" +
                "Engineering design activities during will focus on wells the Southwest Groundwater Treatment Plant SWGWTP a reverse osmosis treatment plant and a by product water pipeline.\n" +
                "Construction activities in will include well drilling feed water pipelines and the SWGWTP.\n" +
                "photo Sampling the water from Bingham Canyon Water Treatment Plant at its startup in are Dale Gardiner JVWCD Board Chair Bill Champion KUCC CEO Congressional representative Chris Cannon and Lieutenant Governor Gary Herbert.\n" +
                "The water was deemed very tasty and has been a successful addition to our existing sources.\n" +
                "Conservation Jordan Valley initiated the Slow The Flow conservation campaign in.\n" +
                "Now adopted by the State of Utah the campaign continues to inspire and educate all water users.\n" +
                "Although precipitation has increased in the last couple of years the conservation messages and emphasis are focused on efficient long term use of our water supply to keep up with growth and increasing water demands.\n" +
                "The greatest water conservation potential exists in our landscapes.\n" +
                "Water use patterns can be reduced significantly if water wise practices and principles are applied to the landscape.\n" +
                "Changing our ideals now will ensure water for future generations our children grandchildren and great grandchildren.\n" +
                "One element of Jordan Valleys conservation program has been development of the Conservation Garden Park previously known as the Demonstration Garden.\n" +
                "Plans to expand and improve the existing Garden Park are underway with the majority of the funding being provided through a fundraising effort.\n" +
                "Jordan Valley is excited to provide enhanced opportunities for the public to learn about conservation through the Garden Park expansion.\n" +
                "The expansion will include detailed educational exhibits and interactive hands on displays which will provide improved learning opportunities about how to conserve water in the landscape.\n" +
                "Visit www.ConservationGardenPark.org for more information.\n" +
                "In the name of the demonstration garden was changed to Conservation Garden Park.\n" +
                "It is hoped that an expansion of the Garden will begin in with fundraising in progress to help fund expenses.\n" +
                "Expansion design is underway and some money has already been raised.\n" +
                "We look forward to enhancing the conservation education experience for garden visitors by not only showing them how a finished water efficient landscape looks but show and teach them how to get from where they are to where they want to be.\n" +
                "You can get more information on the garden expansion by visiting the web site www.conservationgarden\n" +
                "park.org.\n" +
                "JVWCD Goal reduction by Actual progress line Jordan Valleys goal to reduce per capita water usage percent by has already had excellent results.\n" +
                "This chart shows that conservation education efforts are an effective method for reducing outdoor and overall water use.\n" +
                "Annual per capita water use in gallons per day Visit the Conservation Garden Park for your own ideas on water efficient landscaping.\n" +
                "If we each save a little well all save a lot.\n" +
                "Even though safety is always a priority it took some coaxing to get every one at Jordan Valley on board.\n" +
                "A district wide goal of zero lost time injuries and zero vehicle incidents caused by employees has been implemented with promising results.\n" +
                "While only the lost time injury goal has been met to this point the number of total injuries has decreased and the cost associated with vehicle incidents has decreased.\n" +
                "Increased employee aware ness involvement and ac countability departmental and district wide safety committees coupled with the implementation of a Safety Incentive Program have all contributed to the success of the program.\n" +
                "Over the past several years Jordan Valley has worked to improve its workplace safety program to reduce the number of injuries and other injury related losses it experiences each year.\n" +
                "Beginning in efforts in safety increased significantly resulting in record improvements.\n" +
                "In Jordan Valley set an organization record for the lowest number of OSHA recordable injuries ORIs with a total of three and made even greater improvements in with just two ORIs.\n" +
                "Considering that Jordan Valley averaged ORIs per year between and this has been a remarkable achievement.\n" +
                "The incident rate graph at right shows the improvements Jordan Valley has made regarding ORIs.\n" +
                "Jordan Valley has been able to save significant amounts of money by reducing its Experience Modification e mod rate.\n" +
                "E mod rates are calculated each year by the National Council on Compensation Insurance Inc. and area Workers Compensation insurance premium modifier that reflects the loss experience of a company compared with payroll exposure during the same time period.\n" +
                "The modifier increases or decreases the premium depending on how the actual exposure and losses for the past three years compares with expected losses for the same amount of exposure.\n" +
                "Ane mod rate of.\n" +
                "is considered average.\n" +
                "Jordan Valleys e mod rate for s premium was.\n" +
                "which is the lowest rate it has ever recorded.\n" +
                "This is also the fourth year in a row that Jordan Valleys e mod rate was below average.\n" +
                "This saves Jordan Valley thousands of dollars in Workers Compensation insurance premiums each year.\n" +
                "OSHA Recordable Injuries have decreased significantly with the increased push toward safety awareness.\n" +
                "Decreased E mod rates save Jordan Valley thousands on Workers Compensation insurance premiums.\n" +
                "Safety Increased Capacity Even with conservation efforts the demand for water continues to increase.\n" +
                "With that increased demand comes a need for increased transmission and distribution capacity.\n" +
                "From to Jordan Valley has seen significant increase in its ability to transmit and distribute water through several system improvements.\n" +
                "New major water lines have been installed along West inch South inch South inch and South inch.\n" +
                "These pipelines provide increased capacity to Herriman Riverton South Jordan West Jordan Draper and Bluff dale.\n" +
                "A new pipeline on South inch will be constructed in.\n" +
                "Th ree new booster stations totalling cubic feet per second cfs capacity and new reservoir storage totaling million gallons of storage have been built.\n" +
                "Right of way for major transmission pipelines including the Southwest Aqueduct and Wasatch Front Regional Pipeline has also been acquired.\n" +
                "With increased capacity comes the responsibility to monitor additional facilities so new SCADA Supervisory Control and Data Acquisition and instrumentation improvements were made to monitor and control operation of the water system and treatment plants.\n" +
                "Geographical Information System GIS capabilities have been added to assist with the maintenance of rights of way property easements and facilities.\n" +
                "photo Two new hp pumps during installation at the South pump station.\n" +
                "Capacity of the pump station was doubled with the addition of these pumps.\n" +
                "photo At right the instal lation of a inch pipeline along South in which serves Draper and Bluff dale and provides an interconnection between the transmission systems of Jordan Valley and Metropolitan Water District of Salt Lake Sandy.\n" +
                "Financial Balance Sheet Summary as of June th Assets Current Restricted Capital Other Total Assets Liabilities Current Long term Total Liabilities Total Fund Net Assets Total Liabilities Fund Net Assets Income Statement Summary for fiscal years ended June th Revenues Operating Property taxes Interest Intergovernmental Non operating Total Revenues Expenses Operating Interest Total Expenses Net Income Other Cash Flow Information for fiscal years ended June th Capital Projects Debt Service Payments On a final note Jordan Valleys financial status in the last years has improved significantly.\n" +
                "Changes include switching to a fiscal year generating revenues in advance of needs reworking budgeting policies defi ning funds purposes developing a year financial and capital improvements plan as well as addressing water rates impact fees and property taxes.\n" +
                "A result of changes in financial management has been an improved bond rating with Fitch Moody s and Standard Poors from A to AA.\n" +
                "Balance Sheet Summary for fiscal years ended June th Income Statement Sum ary fiscal years nded June th Other Cash Flow Information r fiscal years nded June th Million Dollars Total Assets Million Dollars Million Dollars Million Dollars Total Revenues Net Income Funds Spent on Capital Projects Staff Administration Richard Bay Marilyn Payan Linda Townes Neil Cox Reid Lewis Catherine Collins Debbie Ericksen Brian Callister Ellen Bolliger Debbie Gates David Martin Perry Widdison Linda MacNeil Abby Patonai Nelson Jeanette Perry Ann Mecham Margaret Dea Tammy Parker Engineering Alan Packard Yvette Amparo Espinoza Mark Atencio David McLean Don Olsen Shane Swensen Dave Norman Marcelo Anglade Paul Rowley Denise Goodwin Distribution Dirk Anderson Carolyn Greenwell Karen Marchant Jeff Hilbert Steve Anderson Kirk Oman Robert Squire Frank Montoya Dave Spackman Craig Fahrni Allen Taylor Paul Pierce Mike Astill Chris Egan James Estrada Devin Th edell Greg Mark Jim Bogenschutz Samuel Rogers Steve Schmidt Paul Wanlass Val Cossey Devin Warr Adrian Parra Casey Mascaro Jared Ballard Kevin Crane Neil Duncan Leonard Mascher Larry Love Alan Th ackeray Ken Brown Danny White Kathryn Brown Al Warner Dave Hyde Larry Shipman Chad Steadman Jeff Moulton Gordon Batt Blake Woolsey Ken Butterfi eld Steve Beck Scott Olsen Tracy Timothy Danny Ernest Steve Minch Quintin Rubio Dustin Hamilton Justin Shinsel Mike Sigler Water SupplyWater Quality Bart Forsyth Jackie Maas Jeff King David Rice Clifton Smith Courtney Brown Water Supply Jeff Bryant Karin Terry Mark Winters Wade Tuft Blake Bills Trade Barnett Nathan Talbot Dave Beratto Jarod Moffi tt Andy Adams Information Technology Jason Brown Matt Olsen Kelly Erickson Twila Brantley Lorrie Fox Treatment Shazelle Terry Vickie Hart Lorraine Kirkham Steve Blake Josh Th omas Tweet Johnson Doug Leonard Scott Hermreck Johnny Trimble Duff Turner Gene Anderson Ray Stokes Mike Axelgard Kevin James Dave Mecham Cary Shaw Don Scallions Steve Crawford Brad Mabey Dan Claypool Eduardo Cracchiolo Nick McDonald Steve Hansen Marie Owens Ron Kidd Stan Grundy Deon Whittle Te Phan Savidtri Th anasilp Ron Bown Lorena Purissimo Jackie Buhler JORDAN VALLEY WATER CONSERVANCY DISTRICT Jordan Valley Water Conservancy District South West West Jordan UT www.jvwcd.org\n";

        InformationExtractor extractor = new InformationExtractor();
        extractor.getEntityRelations(text2, null, null, null);
    }
}
