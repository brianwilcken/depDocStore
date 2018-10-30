package nlp;

import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import java.util.Collection;
import java.util.Properties;

public class InformationExtractor {
    private StanfordCoreNLP pipeline;

    public InformationExtractor() {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse,natlog,openie");
        props.setProperty("threads", "4");
        pipeline = new StanfordCoreNLP(props);
    }

    public void processText(String text) {
        Annotation doc = new Annotation(text);
        pipeline.annotate(doc);

        for (CoreMap sentence : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
            // Get the OpenIE triples for the sentence
            Collection<RelationTriple> triples =
                    sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
            // Print the triples
            for (RelationTriple triple : triples) {
                System.out.println(triple.confidence + "\t|\t" +
                        triple.subjectLemmaGloss() + "\t|\t" +
                        triple.relationLemmaGloss() + "\t|\t" +
                        triple.objectLemmaGloss());
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

        String text2 = "Jordan Valley Water Conservancy District 2006 Annual Report Table of Contents 02 Board of Trustees 03 Message 04 Executive Staff 06 Member Agencies 07 Agencies Served by JVWCD 08 Sources 10 Deliveries 12 Facilities 14 Signifi cant Accomplishments 15 Water Quality 16 Water Supply 17 Southwest Jordan Valley Groundwater Project 18 Conservation 20 Safety 21 Increased Capacity 22 Financial 24 Staff JORDAN VALLEY WATER C O N S E R V A N C Y D I S T R I C T 01 Jordan Valley Water Conservancy District Jordan Valley was created in 1951 under the Water Conservancy Act and is governed by a board of eight trustees who repre- sent seven geographical divisions.\n" +
                "Th ey are nominated by either the Salt Lake County Council or a city council, depending upon the division they represent.\n" +
                "Each trustee is appointed by the Governor for a four-year term.\n" +
                "Jordan Valley is primarily a wholesaler of water to cities and improvement districts within Salt Lake County.\n" +
                "It also has a retail service area in unincorporated areas of the county.\n" +
                "Jordan Valley delivers 88 percent of its mu- nicipal water on a wholesale basis to cities and water dis- tricts, and 12 percent on a retail basis to unincorporated areas of Salt Lake County.\n" +
                "In addition, Jordan Valley treats and delivers water to Metropolitan Water District of Salt Lake Sandy for delivery to Salt Lake City and Sandy City, even though neither city is within Jordan Valley s service boundaries.\n" +
                "Jordan Valley also delivers untreated water to irrigators in Salt Lake and Utah Coun- ties to meet commitments under irrigation exchanges.\n" +
                "Boar Photograph Quinn Farley Photograph Quinn Farley Board Trustee Royce Gibson Mar West Gibson Chair Magna Boar Boar Board Richard Granite Park Holladay Murra Union South Cottonwood Willow Creek South Salt Lake White City Water Improveme District Jeff Draper Lyle Summers West Jordan City Taggart City Trustee Margaret Peterson West Valley City Gibson Committee Chair Lyle Summers Magna West Jordan City diner Chair Steven Taggart verton South Vice Chair West Valley City Swensen ylorsville City Royce Gary Swensen Taylorsville City Royce Finance Kearns Rasmussen Dale Herriman Bluffdale Jordan Board Richard McDonald Granite Park Holladay Murray Gary Swensen Union South Cottonwood Taylorsville City Willow Creek South Salt Lake Royce White City Water Improvement Finance District Kearns Gary Swens Taylorsville City Richard Granite Park Union South Cot Willow Creek Margaret West Valley City Margaret West Valley City Gibson Chair Magna diner Chair Steven verton South Vice Chai West Valley Trustees Mar West Gibson Margaret West Valley City Chair yard Park Holladay Murra South Cottonwood Creek South Salt Lake Gary Swens Taylorsville City lladay Murray tonwood uth Salt Lake lladay Murray tonwood uth Salt Lake Improvement Royce Gibson Finance Committee Kearns Magna Swensen ylorsville City Royce Finance Kearns Richard Granite Park Union South Cot Willow Creek White City Water District West Jordan Finance Kearns Dale Bluffdale Jordan Photograph Quinn City garet Peterson Valley City Lyle West Rasmussen Herriman Dale Bluffdale Jordan Steven Vice Chai West Valley West Steven Taggart Vice Chair West Valley City yard Park Holladay Murra South Cottonwood Creek South Salt Lake City Water Improveme rict yrdan City Jeff Draper Midvale Taggart City garet Peterson Valley City committee Chair Magna diner Chair verton South Board Richard McDonald Granite Park Holladay Murray Gary Swensen Margaret Peterson Union South Cottonwood Taylorsville City West Valley City Willow Creek South Salt Lake Royce Gibson White City Water Improvement Finance Committee Chair Lyle Summers District Kearns Magna West Jordan City Steven Taggart Vice Chair West Valley City rict Jeff Draper Midvale Dale Bluffdale Jordan West Jordan City Taggart City Rasmussen Herriman 03 After our recent experience with fi ve years of drought, we enjoyed a second year of good water supply during 2006.\n" +
                "Th is provided an opportunity for Jordan Valley to rest its wells and allow the underground aquifer reservoir to recover from the eff ects of drought.\n" +
                "Although early data for 2007 indicates below-normal snowpack conditions, we ended calendar year 2006 with a good water supply due to substantial reserves in storage.\n" +
                "Th e retirement of David Ovard as General Manager was a signifi cant event at the end of 2006.\n" +
                "Dave s accomplishments and contributions to Jordan Valley s success are extensive, and are highlighted in this report.\n" +
                "Jordan Valley will miss Dave Th e year 2006 also marked a continuation of rapid population growth in Jordan Valley s western and southern service areas.\n" +
                "Jordan Valley and its member agencies worked together in planning for important new infrastructure to serve these fast- growing areas.\n" +
                "Jordan Valley has made signifi cant progress in infrastructure engineering and construction for these areas, and sees a need for these eff orts to continue in coming years.\n" +
                "As population continues to grow rapidly, Jordan Valley has continued to lead in the implementation of water conserva- tion education and outreach programs.\n" +
                "Th e public responded by achieving nearly a 19 percent reduction in water use by 2005, but in 2006 water usage increased due to perceptions of the drought ending.\n" +
                "Jordan Valley will continue its eff orts to assist its member agencies and customers in the quest to be more water ef- fi cient in coming years.\n" +
                "Among those eff orts in 2007 is the planned expansion of the Conservation Garden Park at Jordan Valley.\n" +
                "Looking Forward David Ovard has been the General Manager of Jordan Valley for the past 18 years.\n" +
                "His accomplishments are many and varied.\n" +
                "He leaves Jordan Valley in excellent condition after 35 years of service.\n" +
                "Richard Bay has been appointed by the Board of Trustees as the new General Manager for Jordan Valley.\n" +
                "With 25 years of experience and having mentored under David for many years, Richard brings experience and foresight to the position.\n" +
                "photos Immediate right is David Ovard, past General Manager far right is Alan Pack- ard, Assistant General Manager Richard Bay, General Manager and Bart Forsyth, Assistant General Manager.\n" +
                "Photograph by Quinn Farley Executive Staff Yvette Amparo-Espinoza Administrative Assistant III Linda Townes CommunicationsPR Specialist Jeff Bryant Water Supply Department Manager Dirk Anderson Distribution Department Manager Debbie Ericksen Human Resources Manager Dave Rice Conservation Programs Coordinator Photography by Quinn Farley Marilyn Payan Executive Assistant Neil Cox Assistant Treasurer Jason Brown IT Department Manager Shazelle Terry Treatment Department Manager Jackie Maas Administrative Assistant III Dave Martin Chief Financial Offi cerController Mark Atencio Engineering Department Manager Reid Lewis Attorney 06 Member Agencies Bluff dale City Mayor Claudia Anderson Draper City Mayor Darrell Smith John F. Hendrickson, City Manager Granger-Hunter Improvement District Gordon W. Evans, Board Chair David Warr, General Manager Herriman City Mayor J. Lynn Crane Rod Brocious, City Engineer Hexcel Corporation Ken Bunkowski, General Manager Kearns Improvement District Rodney Bushman, Board Chair Carl Eriksson, General Manager Magna Water Company Dan Tuttle, Board Chair Ed Hansen, General Manager Midvale City Mayor JoAnn Seghini Kane Loader, City Administrator Riverton City Mayor William Applegarth Lance Blackwood, City Manager Sandy City Mayor Tom Dolan Shane Pace, Public Utilities Director City of South Jordan Mayor Kent Money Rick Horst, City Manager City of South Salt Lake Mayor Robert D. Gray Dennis Peay, Public Works Director Taylorsville-Bennion Improvement District Benjamin Behunin, Board Chair Floyd J. Nielsen, General Manager Utah State Department of Corrections Greg Peay, Director of Facilities and Construction Rick Johnson, Deputy Warden of Support Services WaterPro, Inc. Stephen L. Tripp, President Bruce Cuppett, CEO West Jordan City Mayor David Newton Gary Luebbers, City Manager White City Water Improvement District Paulina Flint, Board Chair Paul Ashton, General Manager Willow Creek Country Club Alex Nicolaidis, General Manager 07 Agencies Served by Jordan Valley Water Conservancy District Receives water from facilities co-owned with and operated by Jordan Valley.\n" +
                "Parts of these areas receive service from Jordan Valley on a retail basis.\n" +
                "08 Sources Pictured above is Bell Canyon Creek, located in Bell Canyon in the southeast area of Salt Lake County.\n" +
                "It is one of the largest sources of mountain stream water to Southeast Regional Water Treatment Plant.\n" +
                "Other major source water for Jordan Valley includes Provo River Upper Provo River reservoirs high Uinta lakes Weber River Duchesne River Local Wasatch streams Groundwater Salt Lake Valley wells Long-term conservation ethics in our community will play an important role in the amount of water avail- able for future generations.\n" +
                "Conservation also aff ects the cost of future water infrastructure and new source development by delaying the need for new sources of water.\n" +
                "Jordan Valley s water supply has increased signifi cantly in the last 18 years.\n" +
                "Page 16 of this report discusses some of the ways that supply has increased.\n" +
                "Continued ingenuity is paramount to providing the public with clean, safe drinking water.\n" +
                "Having pristine sources such as the Provo River also helps.\n" +
                "Pictured above are the Provo Falls off the Mirror Lake Highway.\n" +
                "09 Municipal industrial water supplies 2006 AF 2005 AF Jordanelle Reservoir Central Utah Project a 57,984 24,243 Deer Creek Reservoir Provo River Project b storage 8,286 1,876 extra allotment 0 16,682 Upper Provo River reservoirsa 1,879 1,058 Echo Reservoirc 0 3,367 Provo River direct fl ows 3,337 16,700 Weber River direct fl ows 0 0 Local Wasatch streams 1,812 1,891 Groundwater wells 3,882 8,859 Subtotal for Municipal Industrial supplies 77,241 74,676 Irrigation water supplies Jordanelle Reservoir Central Utah Project a 61 36 Deer Creek Reservoir Provo River Project b storage 7,121 0 extra allotment 0 8,985 Upper Provo River reservoirsa 2,060 0 Echo Reservoirc 2,304 74 Provo River direct fl ows 19,885 9,004 Weber River direct fl ows 0 0 Utah Lake 9 9,883 Subtotal for irrigation 31,440 27,982 TOTAL ALL SUPPLIES 120,747 102,658 Total water treated or transported for other agencies 12,066 8,478 TOTAL ALL WATER 119,614 Municipal Industrial Water S li Irrigation Water Supplies 2006 2005 TOTAL AL SUP LIES 08,681 TOTAL TER 120,747 1 136 a Provo River sources b Weber, Duchesne and Provo River sources c Weber River sources 10 Jordan Valley works to provide each of its 18 wholesale mem- ber agencies with the highest quality water possible, allowing them to then provide their customers with the same.\n" +
                "Jordan Valley also delivers retail water to over 8,500 customers, as well as irrigation and raw water to several agencies in the Salt Lake Valley.\n" +
                "Such large amounts of water require enormous storage capac- ity, not only to satisfy demands, but to provide fi re protection as well.\n" +
                "Shown above is the interior of a 100-million gallon storage reservoir which provides water for much of the west side of the Salt Lake Valley.\n" +
                "Deliveries 11 Wholesale deliveries 2006 AF 2005 AF Bluff dale City 1,488 1,207 Draper City 3,185 2,627 Granger-Hunter Improvement District 16,960 16,920 Herriman City 1,958 1,213 Hexcel Corporation 716 699 Kearns Improvement District 7,959 7,150 Magna Water Company 886 928 Midvale City 143 149 Riverton City 618 620 Sandy City 316 324 City of South Jordan 10,436 9,088 City of South Salt Lake 542 613 Taylorsville-Bennion Improvement District 4,883 5,057 Utah State Department of Correc- tions 576 556 WaterPro, Inc. 1,057 946 West Jordan City 14,957 15,040 White City Water Improvement District 0 0 Willow Creek Country Club 318 296 Subtotal for wholesale 66,999 63,435 JVWCD retail area Holladay, Murray, Sandy, South Salt Lake and unincorporated county 9,721 8,875 JVWCD use 461 970 JVWCD losses 60 1,396 Subtotal for deliveries, use and loss 77,241 74,676 Irrigation raw water 2006 AF 2005 AF Utah State Department of Public Safety 9 17 Staker Parsons Company 61 36 Welby-Jacob Water Users Co. 28,195 26,754 Provo Reservoir Canal losses 3,175 1,175 Subtotal for irrigation raw water 31,440 27,982 Total delivered water 108,681 102,658 MI water treated or trans- ported for other agencies 2006 AF 2005 AF Metropolitan Water District of Salt Lake Sandy 11,748 8,433 Taylorsville-Bennion Improvement District 229 32 West Jordan City 89 13 Subtotal for treated or transported water 12,066 8,478 Total water delivered, treated or transported 120,747 111,136 Wholesale deliveries Irrigation and raw water MI water treated or trans- ported for other agencies 12 Facilities 1. Upper Provo River Reservoirs Once converted to small storage reservoirs, the majority of these Uinta lakes have now been rehabilitated and the storage rights moved to Jordanelle Reservoir.\n" +
                "Jordan Valley is a major stockholder in the water rights for these lakes.\n" +
                "2. WeberProvo Rivers Diversion Th is canal conveys water from the Weber River and Echo Reservoir to Jordan Valley.\n" +
                "It is also used by the Provo River Water Users Association for the diversion of Weber River water to supply Deer Creek Reservoir.\n" +
                "3. Jordanelle Reservoir With a capacity of 320,000 acre feet AF , this reservoir was built by the U.S. Bureau of Reclamation, is operated by Central Utah Water Conservancy District CUWCD , and collects and stores Central Utah Project CUP water rights on the Provo River.\n" +
                "Jordan Valley receives an average of 50,000 AF of CUP water annually.\n" +
                "4. Deer Creek Reservoir Th is reservoir is a feature of the Provo River Project and has a capacity of 152,000 AF.\n" +
                "Jordan Valley is entitled to water stored in this reservoir, which originates from the Provo, Weber and Duchesne rivers.\n" +
                "5. Salt Lake Aqueduct Th is 69-inch diameter pipeline is owned and operated by Metropolitan Water District of Salt Lake Sandy MWDSLS.\n" +
                "It conveys Provo River water from Deer Creek Reservoir to service areas of MWDSLS and Jordan Valley.\n" +
                "6. Southeast Regional Water Treatment Plant Jordan Valley s 20 MGD facility treats water from the Salt Lake Aqueduct and local mountain streams.\n" +
                "7. Little Cottonwood Treatment Plant MWDSLS s 100 MGD plant delivers treated water to Jor- dan Valley and MWDSLS service areas.\n" +
                "8. Well Field Th is aquifer is the source of high-quality groundwater for Jordan Valley and many municipalities.\n" +
                "9. Jordan Aqueduct Th is 78-inch pipeline is operated by Jordan Valley on behalf of itself and MWDSLS.\n" +
                "It conveys water from Deer Creek and Jordanelle reservoirs and the Provo River to Jordan Valley Water Treatment Plant.\n" +
                "10.\n" +
                "Jordan Narrows Pump Station Owned and operated by Jordan Valley, this station pumps Utah Lake water into the Welby and Jacob canals for irriga- tion purposes as part of a large irrigation water exchange.\n" +
                "11.\n" +
                "Jordan Valley Water Treatment Plant Th is 180 MGD plant was built by CUWCD and is operated by Jordan Valley on behalf of itself and MWDSLS.\n" +
                "It sup- plies water to many communities in Salt Lake Valley.\n" +
                "It is the largest water treatment plant in Utah.\n" +
                "12. Equalization Reservoirs Booster Stations Th ese widely-dispersed facilities store and pump water to Jordan Valley s customers.\n" +
                "13.\n" +
                "Jordan Aqueduct Terminal Reservoir At 100 million gallons, this drinking water storage facility has the largest capacity in the state.\n" +
                "14.\n" +
                "Bingham Canyon Water Treatment Plant In cooperation with Kennecott Utah Copper, Jordan Valley receives treated potable water from this reverse osmosis water treatment plant.\n" +
                "Jordan Valley operates and maintains dozens of facili- ties throughout the valley.\n" +
                "District personnel take pride in their upkeep, sus- taining a rigorous schedule of continuous maintenance and improvements.\n" +
                "photo Shown above is the Jordan Narrows Pump Station, described in paragraph number 10. area ean eran ree nee Pontes vee Pontes Pontes ace eee eee ras ree nee rer Poe ras ree nee vee eran poe erran Eaca rer Poe erran Eaca aCe aeenece area ean aCe aeenece ean eee erran 14 Signifi cant Accomplishments 1989 to 2006 During Dave Ovard s tenure as General Manager, Jordan Valley has seen many changes, challenges, and improve- ments.\n" +
                "Th roughout this publication we will highlight some of those accomplishments, sharing with you the tremendous growth and achievements during Dave s 18 years of service as General Manager.\n" +
                "Due to the eff orts highlighted here, Jordan Valley is poised for quality growth and success.\n" +
                "Jordan Valley has been placed on a solid fi nancial foot- ing through sound fi nancial management and planning as evidenced by its 10-year Financial Plan and 10-year Capital Improvements Plan.\n" +
                "As a result, Jordan Valley s bond rating has increased from A- to AA-.\n" +
                "Structure for growth of Jordan Valley and employee successes has been established and maintained through the development of professional administrative policies and procedures.\n" +
                "Th e fi rm water supply of Jordan Valley has been in- creased by 95,000 acre feet including ULS water.\n" +
                "Source capacity has been increased by 48 percent through increased treatment plant and well capacities.\n" +
                "Th rough its Slow the Flow and related conservation programs, Jordan Valley has led the way for water con- servation advances throughout the state of Utah.\n" +
                "Jordan Valley has improved its working relationships with member agencies, sister agencies, state and federal agencies, the public, and the news media.\n" +
                "Jordan Valley has developed a professional staff that is respected throughout the state.\n" +
                "Th e safety record of Jordan Valley has improved sub- stantially.\n" +
                "Jordan Valley has received a number of awards and recognitions acknowledging its excellence.\n" +
                "1989 2006 Total Wholesale Water Deliveries 58,729 AF 66,999 AF Total Water Deliveries 96,679 AF 120,747 AF Municipal Minimum Purchase Contracts 47,373 AF 59,087 AF Total Revenues $12,658,299 $41,216,632 Total OM Expense $8,328,140 $21,929,483 Debt Service $4,420,180 $9,660,314 Long-Term Debt $80,121,031 $132,050,045 Bond Rating A- AA- Total Assets $111,1036,256 $251,964,491 Property Owned Parcels Acres 59 96 280 617 Firm Water Supply 40,000 AF 135,000 AF Including ULS Source Capacity Surface Water Groundwater 118 MGD 153 MGD 149 MGD 229 MGD System Storage 99 MG 171 MG Total Wells 16 30 Total Booster Stations 9 12 ULS, or Utah Lake System, is a future water supply that will be pro- vided to Jordan Valley as part of the Central Utah Project CUP. 15 Water Quality In 1989 Jordan Valley established the Jordan Valley Treatment Plant Laboratory Lab to perform analyses and monitor the water quality of its own sources.\n" +
                "Initially the Lab was capable of per- forming only a handful of basic water quality tests on District sources.\n" +
                "Currently the Lab can test for almost 30 diff erent compounds, and analyzes almost 7500 samples a year for itself and several of its member agencies.\n" +
                "photo Top left in addi- tion to on-line continuous monitoring, Jordan Valley personnel regularly collect grab samples in the system to ensure water quality.\n" +
                "Bottom left shows how regulations have increased since the inception of the Safe Drinking Water Act in 1974.\n" +
                "Water quality regulations have increased signifi cantly over the past 30 years with the 1986 and 1996 amend- ments to the Safe Drinking Water Act of 1974.\n" +
                "During this time EPA has added regulations for surface wa- ter and groundwater involving organic and inorganic compounds, lead, copper, turbidity, disinfectants and disinfection by-products, fi ltration, protozoa, bacteria, viruses, radionuclides, fl uoride, source water protection and unregulated contaminants.\n" +
                "Additional regulations are slated for the coming years.\n" +
                "In order to stay ahead of these regulations and meet cus- tomers increasing expectations, Jordan Valley continu- ally researches and implements new treatment technolo- gies.\n" +
                "Some of the most signifi cant projects impacting water quality completed in the last 18 years include 1995 Design and installation of a pilot plant for various treatment studies at Jordan Valley Water Treatment Plant JVWTP.\n" +
                "2000 Addition of a high-rate clarifi cation process at Southeast Regional Water Treatment Plant SERWTP to allow additional treatment of mountain stream sources.\n" +
                "2001 Completion of a full-scale study to determine the eff ectiveness of chlorine dioxide as the primary disin- fectant at JVWTP to reduce disinfection by-products.\n" +
                "Design of a new chlorine dioxide system at JVWTP will begin in 2007.\n" +
                "2002 A full-scale study of poly-aluminum chloride PACl as the primary coagulant at SERWTP.\n" +
                "PACl is now being used at both SERWTP and JVWTP.\n" +
                "2006 Installation of 70 additional water quality moni- toring stations throughout Jordan Valley s water trans- mission system.0\n" +
                "50 100 150 200 250 2005200019951990198519801974 Re gu la te d Co nt am in an ts a nd P ro ce ss es 16 Water Supply Th ou sa nd A cr e Fe et Jordan Valley s water supply has increased by approximately 95,000 acre feet in the last 18 years.\n" +
                "With these increased sources, the supply of water to much of the Salt Lake Valley has been made secure well into the future.\n" +
                "With improved conserva- tion eff orts, the date for pursuing additional supplies may be even later than currently anticipated.\n" +
                "Even with all the water supply sources utilized by Jordan Valley, it will not be enough for future demands.\n" +
                "In the last 18 years, Jordan Valley has increased its fi rm water supply by approximately 95,000 acre feet, or roughly 385 percent.\n" +
                "With this increased supply and conservation results achieved, the need to develop even more costly future supplies is diminished.\n" +
                "Some supplies that have been developed include additional groundwater sources, imported surface water from irrigation exchanges, and groundwater remediation, such as the South- west Jordan Valley Groundwater Project.\n" +
                "Increase in Water Supply Since 1989 50 100 150 1989 1990 1991 1992 1998 1999 2000 2003 2004 2005 2006 Southwest Jordan Valley Groundwater Project Th e Southwest Jordan Valley Groundwater Project, a joint project with Kennecott Utah Copper, is designed to remediate contaminated water in two groundwater plumes in the southwestern portion of the Salt Lake Val- ley.\n" +
                "In the spring of 2006 Jordan Valley began exploratory drilling of the deep wells in the eastern area of contami- nation known as Zone B. Drilling of three wells provided information regarding the geology for the drilling of seven deep production wells that began later in 2006.\n" +
                "In May of 2006 the Bingham Canyon Water Treatment Plant reverse osmosis treatment plant began operation photo above.\n" +
                "Th is plant was constructed by Kennecott Utah Copper Corporation to treat water from the west- ern Zone A groundwater sulfate plume.\n" +
                "Th is plant has a capacity of 2,400 gallons per minute and produces 3,500 acre feet of water per year.\n" +
                "In the fall Jordan Valley began drilling fi ve deep wells in Zone B. Th ese wells are along 1300 West between 8800 South and 11100 South.\n" +
                "Each well is expected to produce between 300 gpm and 1,000 gpm.\n" +
                "Th e drilling of these wells is to be complete in the spring of 2007.\n" +
                "Engineering design activities during 2007 will focus on wells the Southwest Groundwater Treatment Plant SWGWTP , a reverse osmosis treatment plant and a by-product water pipeline.\n" +
                "Construction activities in 2007 will include well drilling, feed water pipelines, and the SWGWTP.\n" +
                "photo Sampling the water from Bingham Canyon Water Treatment Plant at its startup in 2006 are Dale Gardiner, JVWCD Board Chair Bill Champi- on, KUCC CEO Congres- sional representative Chris Cannon and Lieutenant Governor Gary Herbert.\n" +
                "Th e water was deemed very tasty and has been a successful addition to our existing sources.\n" +
                "Conservation Jordan Valley initiated the Slow Th e Flow conservation campaign in 1999.\n" +
                "Now adopted by the State of Utah, the campaign continues to inspire and educate all water us- ers.\n" +
                "Although precipitation has increased in the last cou- ple of years, the conservation messages and emphasis are focused on effi cient long-term use of our water supply to keep up with growth and increasing water demands.\n" +
                "Th e greatest water conservation potential exists in our land- scapes.\n" +
                "Water use patterns can be reduced signifi cantly if water-wise practices and principles are applied to the landscape.\n" +
                "Changing our ideals now will ensure water for future generations our children, grandchildren, and great-grandchildren.\n" +
                "One element of Jordan Valley s conservation program has been development of the Conservation Garden Park previously known as the Demonstration Garden.\n" +
                "Plans to expand and improve the existing Garden Park are un- derway, with the majority of the funding being provided through a fundraising eff ort.\n" +
                "Jordan Valley is excited to provide enhanced opportuni- ties for the public to learn about conservation through the Garden Park expansion.\n" +
                "Th e expansion will include detailed educational exhibits and interactive, hands-on displays, which will provide improved learning opportu- nities about how to conserve water in the landscape.\n" +
                "Visit www.ConservationGardenPark.org for more informa- tion.\n" +
                "In 2006 the name of the demonstration garden was changed to Conservation Garden Park.\n" +
                "It is hoped that an expansion of the Garden will begin in 2007, with fundraising in progress to help fund expenses.\n" +
                "Expansion design is underway, and some money has already been raised.\n" +
                "We look forward to enhancing the conservation education experience for garden visitors by not only showing them how a fi nished water-effi cient landscape looks, but show and teach them how to get from where they are to where they want to be.\n" +
                "You can get more informa- tion on the garden expan- sion by visiting the web site www.conservationgarden-\n" +
                "park.org.\n" +
                "155 175 195 215 235 255 20102009200820072006200520042003200220012000 JVWCD Goal 25 reduction by 2025 Actual progress line 255 249 229 213 207 230217 227 Jordan Valley s goal to reduce per capita water usage 25 percent by 2025 has already had excellent results.\n" +
                "This chart shows that conservation education efforts are an effective method for reducing outdoor and overall water use.\n" +
                "An nu al p er c ap ita w at er u se in g al lo ns p er d ay Visit the Conservation Garden Park for your own ideas on water-ef- fi cient landscaping.\n" +
                "If we each save a little, we ll all save a lot.\n" +
                "Even though safety is always a priority, it took some coaxing to get every- one at Jordan Valley on board.\n" +
                "A district-wide goal of zero lost-time injuries and zero vehicle incidents caused by employees has been implemented, with promising results.\n" +
                "While only the lost-time injury goal has been met to this point, the number of total injuries has decreased and the cost associated with vehicle incidents has decreased.\n" +
                "Increased employee aware- ness, involvement and ac- countability, departmental and district-wide safety committees, coupled with the implementation of a Safety Incentive Program, have all contributed to the success of the program.\n" +
                "Over the past several years, Jordan Valley has worked to improve its workplace safety program to reduce the number of injuries and other injury-related losses it experiences each year.\n" +
                "Beginning in 2001, eff orts in safety increased signifi cantly, resulting in record improvements.\n" +
                "In 2005 Jordan Valley set an organization record for the lowest number of OSHA recordable injuries ORIs , with a total of three, and made even greater improvements in 2006 with just two ORIs.\n" +
                "Considering that Jordan Valley averaged 17 ORIs per year between 1995 and 2001, this has been a remarkable achievement.\n" +
                "Th e incident rate graph at right shows the improvements Jordan Valley has made regarding ORIs.\n" +
                "Jordan Valley has been able to save signifi cant amounts of money by reducing its Experience Modifi cation e-mod rate.\n" +
                "E-mod rates are calculated each year by the National Council on Compensation Insurance, Inc. and are a Workers Compensation insurance premium modifi er that refl ects the loss experience of a company compared with payroll exposure during the same time period.\n" +
                "Th e modifi er increases or decreases the premium depending on how the actual exposure and losses, for the past three years, compares with expected losses for the same amount of exposure.\n" +
                "An e-mod rate of 1.0 is considered average.\n" +
                "Jordan Valley s e-mod rate for 2007 s premium was 0.75, which is the lowest rate it has ever recorded.\n" +
                "Th is is also the fourth year in a row that Jordan Valley s e-mod rate was below average.\n" +
                "Th is saves Jordan Valley thousands of dollars in Workers Compensation insurance premiums each year.\n" +
                "0 5 10 15 20 200620052004200320022001200019991998199719961995 0.6 0.8 1.0 1.2 200720062005200420032002200120001999 OSHA Recordable Injuries have decreased signifi cantly with the increased push toward safety awareness.\n" +
                "Decreased E-mod rates save Jordan Valley thousands on Workers Compensation insurance premiums.\n" +
                "Safety 21 Increased Capacity Even with conservation eff orts, the demand for water contin- ues to increase.\n" +
                "With that increased demand comes a need for increased transmission and distribution capacity.\n" +
                "From 1989 to 2006, Jordan Valley has seen signifi cant increase in its ability to transmit and distribute water through several system improve- ments.\n" +
                "New major water lines have been installed along 5600 West 20- inch 15000 South 48-inch 13400 South 30-inch and 10200 South 30-inch.\n" +
                "Th ese pipelines provide increased capacity to Herriman, Riverton, South Jordan, West Jordan, Draper and Bluff dale.\n" +
                "A new pipeline on 11800 South 48-inch will be constructed in 2007.\n" +
                "Th ree new booster stations totalling 80 cubic feet per second cfs capacity and new reservoir storage totaling 72 million gallons of storage have been built.\n" +
                "Right-of-way for major transmission pipelines including the Southwest Aqueduct and Wasatch Front Regional Pipeline has also been acquired.\n" +
                "With increased capacity comes the responsibility to monitor additional facilities, so new SCADA Supervisory Control and Data Acquisition and instrumentation improvements were made to monitor and control operation of the water system and treatment plants.\n" +
                "Geographical Information System GIS capabilities have been added to assist with the maintenance of rights-of-way, property easements and facilities.\n" +
                "photo Two new 400-hp pumps during installation at the 13400 South pump station.\n" +
                "Capacity of the pump station was doubled with the addition of these pumps.\n" +
                "photo At right, the instal- lation of a 48-inch pipeline along 15000 South in 2000, which serves Draper and Bluff dale, and pro- vides an interconnection between the transmission systems of Jordan Valley and Metropolitan Water District of Salt Lake Sandy.\n" +
                "22 Financial Balance Sheet Summary as of June 30th 2006 2005 2004 2003 2002 Assets Current Restricted Capital Other $32,551,100 21,029,894 196,355,602 4,265,762 $26,300,207 4,803,665 184,204,967 4,005,189 $22,342,716 24,493,824 167,530,406 1,385,941 $21,409,900 31,022,449 160,037,629 1,545,843 $26,502,386 6,774,534 147,441,526 1,704,751 Total Assets 254,202,358 $219,314,028 $215,752,887 $214,015,821 $182,423,197 Liabilities Current Long-term $10,973,874 127,446,536 $9,961,107 103,793,123 $7,309,806 110,108,510 $6,870,257 113,937,061 $6,132,213 84,705,064 Total Liabilities 115,781,948 113,754,230 117,418,316 120,807,318 90,837,277 Total Fund Net Assets 115,781,948 105,559,798 98,334,571 93,208,503 91,585,920 Total Liabilities Fund Net Assets $254,202,358 $219,314,028 $215,752,887 $214,015,821 $182,423,197 Income Statement Summary for fi scal years ended June 30th 2006 2005 2004 2003 2002 Revenues Operating Property taxes Interest Intergovernmental Non-operating $28,682,559 9,530,648 2,457,744 49,288 472,715 $23,539,333 8,445,006 1,133,168 622,929 553,581 $25,126,680 8,179,174 929,110 202,500 513,820 $23,479,226 7,828,949 1,144,720 200,000 674,477 $23,817,846 7,585,291 1,114,349 200,000 447,150 Total Revenues 41,192,954 34,294,017 34,951,284 33,327,372 33,164,636 Expenses Operating Interest 27,281,756 5,348,526 25,713,008 4,257,749 25,433,860 4,421,356 24,799,873 4,583,010 22,665,230 5,017,842 Total Expenses 32,630,282 29,970,757 29,855,216 29,382,883 27,683,072 Net Income $8,562,672 $4,323,260 $5,096,068 $3,944,489 $5,481,564 Other Cash Flow Information for fi scal years ended June 30th 2006 2005 2004 2003 2002 Capital Projects $16,878,645 $20,987,739 $11,410,037 $19,351,355 $9,890,057 Debt Service Payments $9,601,904 $8,084,840 $8,043,415 $8,046,393 $12,349,224 On a fi nal note, Jordan Valley s fi nancial status in the last 18 years has improved signifi cantly.\n" +
                "Changes include switching to a fi scal year, generating revenues in advance of needs, reworking budgeting policies, defi ning funds purposes, developing a 10- year fi nancial and capital improvements plan, as well as addressing water rates, impact fees and property taxes.\n" +
                "A result of changes in fi nancial management has been an improved bond rating with Fitch, Moody s, and Standard Poors from A- to AA-.\n" +
                "Balance Sheet Summary for fi scal years ended June 30th Income Statement Sum ary fi scal years nded June 30th Other Cash Flow Information r fi scal years nded June 30th 23 M ill io n D ol la rs Total Assets 50 200 250 20022003200420052006 150 100 0 M ill io n D ol la rs 10 40 50 20022003200420052006 30 20 0 300 M ill io n D ol la rs 2 8 10 20022003200420052006 6 4 0 M ill io n D ol la rs 5 20 25 20022003200420052006 15 10 0 Total Revenues Net Income Funds Spent on Capital Projects Staff Administration Richard Bay Marilyn Payan Linda Townes Neil Cox Reid Lewis Catherine Collins Debbie Ericksen Brian Callister Ellen Bolliger Debbie Gates David Martin Perry Widdison Linda MacNeil Abby Patonai-Nelson Jeanette Perry Ann Mecham Margaret Dea Tammy Parker Engineering Alan Packard Yvette Amparo-Espinoza Mark Atencio David McLean Don Olsen Shane Swensen Dave Norman Marcelo Anglade Paul Rowley Denise Goodwin Distribution Dirk Anderson Carolyn Greenwell Karen Marchant Jeff Hilbert Steve Anderson Kirk Oman Robert Squire Frank Montoya Dave Spackman Craig Fahrni Allen Taylor Paul Pierce Mike Astill Chris Egan James Estrada Devin Th edell Greg Mark Jim Bogenschutz Samuel Rogers Steve Schmidt Paul Wanlass Val Cossey Devin Warr Adrian Parra Casey Mascaro Jared Ballard Kevin Crane Neil Duncan Leonard Mascher Larry Love Alan Th ackeray Ken Brown Danny White Kathryn Brown Al Warner Dave Hyde Larry Shipman Chad Steadman Jeff Moulton Gordon Batt Blake Woolsey Ken Butterfi eld Steve Beck Scott Olsen Tracy Timothy Danny Ernest Steve Minch Quintin Rubio Dustin Hamilton Justin Shinsel Mike Sigler Water SupplyWater Quality Bart Forsyth Jackie Maas Jeff King David Rice Clifton Smith Courtney Brown Water Supply Jeff Bryant Karin Terry Mark Winters Wade Tuft Blake Bills Trade Barnett Nathan Talbot Dave Beratto Jarod Moffi tt Andy Adams Information Technology Jason Brown Matt Olsen Kelly Erickson Twila Brantley Lorrie Fox Treatment Shazelle Terry Vickie Hart Lorraine Kirkham Steve Blake Josh Th omas Tweet Johnson Doug Leonard Scott Hermreck Johnny Trimble Duff Turner Gene Anderson Ray Stokes Mike Axelgard Kevin James Dave Mecham Cary Shaw Don Scallions Steve Crawford Brad Mabey Dan Claypool Eduardo Cracchiolo Nick McDonald Steve Hansen Marie Owens Ron Kidd Stan Grundy Deon Whittle Te Phan Savidtri Th anasilp Ron Bown Lorena Purissimo Jackie Buhler JORDAN VALLEY WATER C O N S E R V A N C Y D I S T R I C T Jordan Valley Water Conservancy District 8215 South 1300 West West Jordan, UT 84088 www.jvwcd.org\n" +
                "801 565-4300.";

        InformationExtractor extractor = new InformationExtractor();
        extractor.processText(text2);
    }
}
