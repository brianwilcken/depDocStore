package nlp;

import common.SpellChecker;
import common.Tools;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.util.CoreMap;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.*;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;
import org.springframework.core.io.ClassPathResource;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public class NLPTools {
    final static Logger logger = LogManager.getLogger(NLPTools.class);

    public static TrainingParameters getTrainingParameters(int iterations, int cutoff) {
        TrainingParameters mlParams = new TrainingParameters();
        mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
        mlParams.put(TrainingParameters.TRAINER_TYPE_PARAM, EventTrainer.EVENT_VALUE);
        mlParams.put(TrainingParameters.ITERATIONS_PARAM, iterations);
        mlParams.put(TrainingParameters.CUTOFF_PARAM, cutoff);

        return mlParams;
    }

    public static <T> T getModel(Class<T> clazz, ClassPathResource modelResource) {
        try (InputStream modelIn = modelResource.getInputStream()) {

            Constructor<?> cons = clazz.getConstructor(InputStream.class);

            T o = (T) cons.newInstance(modelIn);

            return o;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    public static <T> T getModel(Class<T> clazz, String modelFilePath) throws IOException {
        try (InputStream modelIn = new FileInputStream(modelFilePath)) {

            Constructor<?> cons = clazz.getConstructor(InputStream.class);

            T o = (T) cons.newInstance(modelIn);

            return o;
        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException |
                IllegalArgumentException | InvocationTargetException e) {
            logger.fatal(e.getMessage(), e);
        }
        return null;
    }

    public static ObjectStream<String> getLineStreamFromString(final String data)
    {
        ObjectStream<String> lineStream = null;
        try {
            InputStreamFactory factory = new InputStreamFactory() {
                public InputStream createInputStream() throws IOException {
                    return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
                }
            };

            lineStream = new PlainTextByLineStream(factory, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return lineStream;
    }

    public static ObjectStream<String> getLineStreamFromFile(final String filePath)
    {
        ObjectStream<String> lineStream = null;
        try {
            InputStreamFactory factory = new InputStreamFactory() {
                public InputStream createInputStream() throws IOException {
                    return new FileInputStream(filePath);
                }
            };

            lineStream = new PlainTextByLineStream(factory, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return lineStream;
    }

    public static ObjectStream<String> getLineStreamFromMarkableFile(final String filePath)
    {
        ObjectStream<String> lineStream = null;
        try {
            MarkableFileInputStreamFactory factory = new MarkableFileInputStreamFactory(new File(filePath));

            lineStream = new PlainTextByLineStream(factory, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return lineStream;
    }

    public static String[] detectSentences(SentenceModel model, String input) {
        SentenceDetectorME sentenceDetector = new SentenceDetectorME(model);

        String[] sentences = sentenceDetector.sentDetect(input);

        return sentences;
    }

    public static String[] detectSentences(String input) {
        SentenceModel model = getModel(SentenceModel.class, new ClassPathResource(Tools.getProperty("nlp.sentenceDetectorModel")));

        return detectSentences(model, input);
    }

    public static String[] detectTokens(TokenizerModel model, String input) {
        TokenizerME tokenDetector = new TokenizerME(model);

        String[] tokens = tokenDetector.tokenize(input);

        return tokens;
    }

    public static String[] detectTokens(String input) {
        TokenizerModel model = getModel(TokenizerModel.class, new ClassPathResource(Tools.getProperty("nlp.tokenizerModel")));

        return detectTokens(model, input);
    }

    public static List<CoreLabel> detectTokensStanford(String input) {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        Annotation processed = pipeline.process(input);
        List<CoreLabel> tokens = processed.get(CoreAnnotations.TokensAnnotation.class);
        return tokens;
    }

    public static List<CoreMap> detectSentencesStanford(String input) {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        Annotation processed = pipeline.process(input);
        List<CoreMap> sentences = processed.get(CoreAnnotations.SentencesAnnotation.class);
        return sentences;
    }

    public static double similarity(String s1, String s2) {
        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) { // longer should always have greater length
            longer = s2; shorter = s1;
        }
        int longerLength = longer.length();
        if (longerLength == 0) { return 1.0; /* both strings are zero length */ }
        LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
        return (longerLength - levenshteinDistance.apply(longer, shorter)) / (double) longerLength;
    }

    public static String normalizeText(Stemmer stemmer, String text) {
        try {
            //produce a token stream for use by the stopword filters
            StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_4_9);
            TokenStream stream = analyzer.tokenStream("", text);

            //get a handle to the filter that will remove stop words
            StopFilter stopFilter = new StopFilter(Version.LUCENE_4_9, stream, analyzer.getStopwordSet());
            stream.reset();
            StringBuilder str = new StringBuilder();
            //iterate through each token observed by the stop filter
            while(stopFilter.incrementToken()) {
                //get the next token that passes the filter
                CharTermAttribute attr = stopFilter.getAttribute(CharTermAttribute.class);
                //lemmatize the token and append it to the final output
                str.append(stemmer.stem(attr.toString()) + " ");
            }
            analyzer.close();
            stopFilter.end();
            stopFilter.close();
            stream.end();
            stream.close();
            return str.toString();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return null;
    }

    public static void main(String[] args) {
        //tagText("The North Milwaukee Power Plant is a coal-fired steam-turbine based generator with a maximum operating capacity of 250 MW.");

        String text = "JORDAN VALLEY WATER C O N S E R V A N C Y D I S T R I C T 2007-2008of Operations Summary 2 Title Here 1 Title HereContents Definitions 02 Wholesale Deliveries 04 Retail Deliveries 05 Sources 08 Distribution 24 Treatment 37 BUDGET 38 Customer Service 42 Conservation 44 Safety 48 Personnel 50 Information Systems 53 Engineering Property Water Rights AF Acre feet ASR Aquifer storage recovery treated water pumped into the underground aquifer, then retrieved for use at a later date CFS Cubic feet per second cfu ml Colony-forming units bacteria per mil- liliter CT Concentration x time for chlorination Feet Above Below Compromise Utah Lake level above or below Compromise Elevation, established by a 1986 agreement between landowners surrounding Utah Lake and water right owners.\n" +
                "When the Utah Lake level exceeds Compromise Elevation, the radial gates at the Utah Lake Outlet Structures must be fully opened.\n" +
                "FTE Full-time employee FY FYTD Fiscal Year Fiscal year to date HAA Haloacetic acid HPC Heterotropic plate count JVWCD Jordan Valley Water Conservancy District JVWTP Jordan Valley Water Treatment Plant M I Municipal and industrial MG Million gallons MGD Million gallons per day mg L Milligrams per liter MWD MWDSLS Metropolitan Water District of Salt Lake Sandy NTU Nephelomentric turbidity units PRWUA Provo River Water Users Association SCADA Supervisory Control and Data Acquisition a computer-based system for remotely monitoring and controlling water systems SERWTP Southeast Regional Water Treatment Plant TDS Total dissolved solids THM Trihalomethane TOC Total organic carbon UFRV Unit fi lter run volume Contents Defi nitions for this publication Whenever possible, fi scal numbers for 2007 2008 were used in this report.\n" +
                "However, in cases where fi scal numbers were not available or feasible to use, we have listed numbers from calendar year 2007.\n" +
                "2 Title Here All deliveries in acre feet FY 07 08 FY 06 07 FY 05 06 FY 04 05 Bluffdale City.\n" +
                "1,574 1,573 1,420 1,137 Staker Parson Companies.\n" +
                "44 57 Draper City.\n" +
                "3,372 3,590 3,033 2,268 Granger-Hunter Improvement District.\n" +
                "17,411 15,988 18,734 16,575 Herriman City.\n" +
                "2,507 2,023 1,658 802 Hexcel Corporation.\n" +
                "720 700 699 723 Kearns Improvement District.\n" +
                "8,321 8,258 8,264 6,802 Magna Water Company.\n" +
                "881 951 928 828 Midvale City.\n" +
                "172 150 150 176 Riverton City.\n" +
                "639 657 707 622 Sandy City.\n" +
                "315 315 317 324 City of South Jordan.\n" +
                "12,034 11,522 10,427 8,564 City of South Salt Lake.\n" +
                "883 603 705 1,021 Taylorsville-Bennion Improvement District.\n" +
                "5,354 3,955 5,012 5,256 Utah State Department of Corrections.\n" +
                "593 582 642 546 WaterPro, Inc. 1,247 1,252 1,103 1,664 West Jordan City.\n" +
                "17,599 16,233 15,962 13,129 White City Water Improvement District.\n" +
                "0 0 0 69 Willow Creek Country Club.\n" +
                "307 343 342 297 TOTAL WHOLESALE.\n" +
                "73,974 68,754 70,145 60,803 Jordan Valley WCD retail area.\n" +
                "7,913 9,874 9,435 8,671 Holladay, Murray, Sandy, South Salt Lake unincorporated county JVWCD usea.\n" +
                "491 472 1,311 1,062 JVWCD treatment plant lossesb.\n" +
                "690 1,134 1,780 255 SUBTOTAL FOR DELIVERIES, USE LOSS.\n" +
                "83,068 80,234 82,671 70,836 Irrigation raw water delivered Utah State Department of Public Safety.\n" +
                "11 10 16 14 Staker Parson Companiesc.\n" +
                "44 57 47 45 Welby-Jacob Water Users Company.\n" +
                "27,430 31,117 31,345 20,661 PRWUA canal losses.\n" +
                "3,471 3,751 2,597 1,028 SUBTOTAL FOR IRRIGATION RAW WATER.\n" +
                "30,956 34,935 34,005 21,748 TOTAL DELIVERED WATER.\n" +
                "114,024 115,169 116,676 92,539 M I water treated or transported Metropolitan Water District of Salt Lake Sandyd.\n" +
                "5,588 10,572 11,056 8,436 Taylorsville-Bennion Improvement District.\n" +
                "220 255 121 47 West Jordan City.\n" +
                "92 99 477 18 SUBTOTAL FOR TREATED OR TRANSPORTED WATER.\n" +
                "5,900 10,926 11,654 8,501 TOTAL WATER DELIVERED, TREATED OR TRANSPORTED 119,924 126,095 128,330 101,040 a- Hydrant and main line fl ushing, main line breaks, reservoir cleaning and irrigation of landscaping at Jordan Valley sites.\n" +
                "b- Treatment plant losses calculated based on plant use and evaporation for both JVWTP and SERWTP.\n" +
                "c- d- This total includes Jordan Valley water exchanged at 11400 South and east-side water exchanged at 2100 South.\n" +
                "Wholesale Deliveries 3 Title Here 0 10000 20000 30000 40000 50000 60000 70000 80000 90000 Actual Monthly Budget Monthly 0 10000 20000 30000 40000 50000 60000 70000 80000 90000 Actual Budget JunMayAprMarFebJanDecNovOctSepAugJuly Wholesale Deliveries AF Budgeted vs. Actual 0 50 100 150 200 Temp F JunMayAprMarFebJanDecNovOctSepAugJul 0 50 100 150 200 250 Flow MGD Precip x 100 D eg re es F Flow vs Temp vs Precipitation 0 50 100 150 200 250 300 200620072008 DecNovOctSepAugJulJunMayAprMarFebJan C FS Average Daily Demand 0 50 100 150 200 250 300 350 400 200620072008 DecNovOctSepAugJulJunMayAprMarFebJan C FS Peak Daily Demand Daily System Demands Calendar Year 0 5000 10000 15000 20000 200620072008 DecNovOctSepAugJulJunMayAprMarFebJan A cr e Fe et Monthly Contract Deliveries AF Wholesale De iveries 4 Retail Deliveries Total Annual Retail Water Use AF 0 2000 4000 6000 8000 10000 03 0404 0505 0606 0707 08 Average Annual Use Per Connection gallons 0 50000 100000 150000 200000 250000 300000 350000 400000 03 0404 0505 0606 0707 08 0 200 400 600 800 1000 1200 03 0404 0505 0606 0707 08 Average Annual Use Per Connection per day gallons $0 $100 $200 $300 $400 $500 $600 03 0404 0505 0606 0707 08 Average Annual Water Bill $0 $10 $20 $30 $40 $50 03 0404 0505 0606 0707 08 Average Monthly Water Bill 0 2000 4000 6000 8000 10000 03 0404 0505 0606 0707 08 Total Connections fire lines high-usage 900 accounts residential 0 2000 4000 6000 8000 10000 03 0404 0505 0606 0707 08 Total Active Retail Connections 5 Water Supplies Municipal Industrial water supplies acre-feet FY 07 08 FY 06 07 FY 05 06 Jordanelle Reservoir Central Utah Project a. 57,603 56,236 42,236 Deer Creek Reservoir Provo River Project b storage.\n" +
                "3,878 3,544 8,508 extra allotment.\n" +
                "2,955 0 13,699 leases purchases.\n" +
                "0 0 0 temporary Provo River storage.\n" +
                "0 0 0 MWD surplus Little Cottonwood Creek.\n" +
                "1,000 0 Upper Provo River reservoirsa.\n" +
                "0 0 1,879 Echo Reservoirc.\n" +
                "483 873 1,761 Provo River direct fl ows.\n" +
                "8,896 5,991 8,520 Weber River direct fl ows.\n" +
                "0 0 0 Local Wasatch streams.\n" +
                "1,226 1,665 2,915 Groundwater wells.\n" +
                "8,027 10,925 3,153 Subtotal for M I. 83,068 80,234 82,671 Irrigation water supplies Jordanelle Reservoir Central Utah Project a. 44 57 47 Deer Creek Reservoir Provo River Project b storage.\n" +
                "5,409 12,214 1,801 extra allotment.\n" +
                "0 0 8,985 leases purchases.\n" +
                "0 0 0 temporary Provo River storage.\n" +
                "0 0 0 Upper Provo River reservoirsa.\n" +
                "1,550 2,810 0 Echo Reservoirc.\n" +
                "0 3,956 735 Provo River direct fl ows.\n" +
                "14,812 15,888 12,613 Weber River direct fl ows.\n" +
                "0 0 0 Utah Lake.\n" +
                "9,141 10 9,824 Subtotal for irrigation.\n" +
                "30,956 34,935 34,005 TOTAL ALL SUPPLIES.\n" +
                "114,024 115,169 116,676 M I water treated or transported for other agencies.\n" +
                "5,900 10,926 11,654 TOTAL ALL WATER.\n" +
                "119,924 126,095 128,330 a- Provo River sources b- Weber, Duchesne and Provo River sources C- Weber River sources 6 Title Here Sources 07 08 06 07 05 06 04 05 03 04 Deer Creek Reservoir Storage Extra allotment Leases and purchases Temporary Provo River storage Subtotals 9,287 2,955 0 0 12,242 15,758 0 0 0 15,758 10,309 22,684 0 0 32,993 7,122 2,984 0 0 10,106 8,693 0 0 0 9,483 Central Utah Project 57,647 56,293 42,283 25,671 41,056 MWD surplus Little Cottnwd Crk 0 1,000 0 0 790 Provo River 26,177 21,879 21,133 21,799 7,919 Uinta lakes 1,550 2,810 1,879 1,830 2,123 Weber River 0 0 0 0 358 Echo Reservoir 483 4,829 2,496 1,906 3,600 Utah Lake 9,141 10 9,824 14,161 31,013 Groundwater 8,027 10,925 3,153 15,939 11,438 Wasatch mountain streams 1,226 1,665 2,915 1,127 4,245 TOTALS 116,493 115,169 116,676 92,539 111,235 5-Year History of Water Source Supplies 0 10000 20000 30000 40000 50000 60000 Other Groundwater Utah Lake Provo River Central Utah Project Deer Creek 03 0404 0505 0606 0707 08 Uinta lakes, Weber River, Echo Reservoir and Wasatch mountain streams 7 Title HereSources -8 -7 -6 -5 -4 -3 -2 -1 0 1 2007 2006 2005 2004 2003 DecNovOctSepAugJulyJuneMayAprMarFebJan Fe et A b ov e B el o w C o m p ro m is e 5-Year History of Utah Lake Levels 0 is compromise 6125 6150 6175 2007 2006 2005 2004 2003 DecNovOctSepAugJulyJuneMayAprMarFebJan El ev at io n L ev el 5-Year History of Jordanelle Reservoir Levels 5362.0 5389.5 5417.0 2007 2006 2005 2004 2003 DecNovOctSepAugJulyJuneMayAprMarFebJan El ev at io n L ev el 5-Year History of Deer Creek Reservoir Levels Increased water levels in 2005 are due to 140 of normal snowpack.\n" +
                "8 Title Here Distribution Well No.\n" +
                "Location Well Capacity cfs Avg Production cfs Days of Operation Annual Production AF Total Power Cost Average Cost AF Water Level feet above pump Max Min Avg 1 2500 E Creek Rd 5.35 3.36 201.10 1,338.10 $49,309.48 $36.85 92 64 70 2 1787 E Creek Rd 5.01 $1,723.35 148 137 143 3 7751 S 1300 East 4.01 2.40 14.50 69.00 $19,172.53 $277.86 152 141 147 4 7750 S 1000 East 3.11 2.25 9.50 42.70 $4,179.97 $97.89 210 179 197 5 8200 S 1000 East 2.01 $76.14 145 119 139 6 7700 S 700 East 5.57 4.33 40.20 346.20 $27,971.76 $80.80 209 151 185 7 8201 S 700 East 2.23 2.15 6.60 28.10 $3,390.88 $120.67 252 242 247 8 1200 E 9400 South 1.78 $390.69 139 133 135 9 590 W 6400 South 1.67 $544.93 94 94 94 10 8651 S 1300 East 4.00 $262.37 169 159 160 11 8183 S 1300 East Pump and equipment have been pulled from this site.\n" +
                "12 1307 E 6860 South 4.70 4.06 71.30 524.10 $23,202.98 $44.27 186 163 173 13 9125 S 500 West 2.01 $713.39 102 98 100 14 2090 E 8600 South 2.45 $7,599.20 102 98 100 15 1500 E 9400 South 9.50 $1,650.47 172 161 167 16 1530 W 14600 So.\n" +
                "4.46 $1,178.67 153 152 153 17 300 E 4500 South 0.70 $879.03 231 218 221 18 9390 Solena Way 4.80 $265.84 128 109 116 19 2300 E 9800 South 4.12 3.74 1.50 11.30 $8,058.05 $713.10 146 111 135 20 1155 E Webster Dr 6.50 8.70 86.90 1,470.20 $94,049.31 $63.97 171 132 151 21 9000 S Quail Hollow 2.20 2.42 84.30 407.10 $27,944.47 $68.64 213 152 174 22 1600 E Siesta Drive 9.60 8.57 52.10 924.20 $58,053.76 $62.82 207 185 202 23 1526 E 8600 South 8.50 $1,574.54 24 8518 S 960 East 6.00 $5,092.65 232 205 215 25 1159 E 4500 South 2.20 $565.70 237 211 229 26 1850 E Newbury Dr 8.90 $1,081.81 161 128 144 27 275 E Carol Way 2.89 2.66 120.20 633.60 $33,121.32 $52.27 351 248 267 28 4670 S 1590 E 3.78 2.46 46.00 226.00 $15,271.67 $67.57 395 190 243 29 1028 E College Dr 4.01 3.48 79.70 553.30 $26,309.40 $47.55 357 249 278 30 1784 E Creek Rd 7.13 7.71 74.70 1,182.40 $76,882.69 $65.02 329 153 177 31 Prison Well 0.89 0.92 56.30 86.57 N A N A N A Totals Averages 129.19 4.16 63.47 7,756.30 $490,517.05 $60.20 Owned by the Utah State Department of Corrections not included in Totals Averages.\n" +
                "Power costs paid by the Utah State Department of Corrections.\n" +
                "Note Cost per AF and water levels are a fi scal year average.\n" +
                "Well Summary 9 Title Here Location Design Capacity cfs Total Horsepower Average Dynamic Lift feet Production Average cfs Annual Production AF Total Power Cost Average Cost AF Days in Operation 1 4706 Naniloa Drive 12 300 N A $1,997.69 2 4500 S 4800 West 49 1625 200 16.34 3,226.9 $60,323.94 $18.69 116 3 6200 S 3200 West 46 1500 180 15.54 9,830.1 $103,985.66 $10.58 367 4 3600 W 10200 South 45 1900 350 9.51 7,856.2 $245,541.72 $31.25 365 5 5700 W 10200 South 22 750 240 3.91 3,433.9 $85,341.53 $24.85 364 6 5820 S 3800 West 25 650 180 11.00 3,782.4 $60,473.55 $15.99 151 7 110 E 11400 South 24 1200 320 11.26 1,454.8 $24,413.12 $16.78 76 8 11574 S 2580 East 4 170 260 9 15305 S 3200 West 5 200 280 2.75 425.7 $11,565.77 $27.17 364 10 3145 W 11400 South 42 900 110 7.53 5,483.1 $89,147.17 $16.26 217 11 10730 S 1300 East 22 400 100 7.26 344.7 $9,934.36 $28.82 27 12 13400 S 3300 West 30 2400 495 9.78 5,461.1 $214,355.69 $39.25 310 Totals Averages 326 11,995 247 8.62 41,299.0 $907,080.20 $21.92 236 Note Cost per AF is a fi scal year average.\n" +
                "Booster Pump Summary Exact Address Common Name Est.\n" +
                "Com- pletion Completion Status Comments Project Manager 1362 East 6400 South 1362 E. 6400 S. 2008 Site work in construction Complete by Nov 2008 David McLean 8578 Moniter Drive Moniter Dr. 2008 Site work in construction Complete by Nov 2008 David McLean 8148 South 1330 East 1330 E. 2008 Site work in construction Complete by Nov 2008 David McLean 7760 South 1000 East 7800 S. 1000 E. 2009 Site work being designed Bids in winter 2009 David McLean 7618 South 700 East 7618 S. 700 E. 2009 Site work being designed Bids in winter 2009 David McLean 2129 E Murray-Holladay Rd. Murray-Holladay Rd. 2011 Drilling complete Bids in winter 2009 Shane Swensen 7038 S. Cottonwood St Cottonwood St. 2011 Drilling complete Bids in winter 2009 Shane Swensen DW1 8773 S 1300 W DW1 2012 Site work being designed Bids in winter 2009 Jan Erickson DW2 1324 W Polo Lane DW2 2012 Drilling complete Bids in winter 2009 Jan Erickson DW3 9824 South 1300 W. DW3 2012 Drilling complete Bids in winter 2009 Jan Erickson DW4 10621 South 1300 W. DW4 2012 Drilling complete Bids in winter 2009 Jan Erickson DW5 11059 South 1300 W. DW5 2012 Drilling complete Bids in winter 2009 Jan Erickson DW6 9911 South 2700 W. DW6 2012 Drilling complete - fall 2008 Todd Marti DW7 10940 South 2700 W. DW7 2012 Drilling complete - fall 2008 Todd Marti DW8 8300 South 1000 W. DW8 2012 Drilling complete Bids in winter 2009 Jan Erickson 176 East Forbush Avenue Forbush Avenue 2013 Property purchased Water right recently approved Frank Roberts 2911 E. Mount Jordan Rd. Mt. Jordan Rd. 2013 Property purchased Awaiting water rights approval Frank Roberts 449 East 3900 South 500 E 3900 S 2013 Property purchased 2500 East Creek Rd Replacement Willow Creek Dr. 2014 Replacement site identifi ed on WCCC Note DW wells are deep wells for the SWGWTP as compared to shallow wells for the same project.\n" +
                "Future Wells Status Distribution 10 Title Here Distribution Aquifer Storage Recovery and Conjunctive Management Aquifer Recovery Water Levels 0 50 100 150 200 250 300 11 45 7 77 Siesta 15 94 JunMayAprMarFebJanDecNovOctSepAugJul W at er L ev el f ee t ab ov e p u m p This graph shows a year s sample of ground water levels at four District wells.\n" +
                "We have been monitoring well levels to see if the aquifer is recovering.\n" +
                "Natural recovery occurs in the winter, with more drawdown in the summer.\n" +
                "Injected for underground storage 108th So.\n" +
                "north fl ow Total Net Saved a Total Well Production33 System 16 System Jul 478.06 478.06 478.06 1596.12 Aug 115.29 115.29 115.29 1941.96 Sep 651.28 651.28 651.28 1137.14 Oct 778.01 778.01 778.01 725.34 Nov 571.96 571.96 571.96 305.17 Dec 407.66 407.66 407.66 0.00 Jan 931.83 931.83 931.83 205.00 Feb 198.86 198.86 198.86 1449.46 Mar 47.25 658.66 705.91 658.66 135.21 Apr 225.42 196.00 422.61 844.03 648.03 81.39 May 125.48 188.17 569.77 883.42 695.25 122.45 June 89.73 890.46 980.19 890.46 326.44 Yearly Totals 350.90 521.15 6,674.45 7,546.50 7,025.35 8,025.68 ASR Water Quality Summary Monitoring and reporting for the ASR project is regulated by the Division of Water Quality s UIC permitting process.\n" +
                "The water in- jected at each of the injection wells comes from either the JVWTP or SERWTP and meets all drinking water regulations since the water is injected directly from the distribution system.\n" +
                "a 10800 S. 1300 E. is the fl ow control pump station on the 30-inch 1300 East pipeline between 11400 South and 9400 South.\n" +
                "This pipeline and station allow Jordan Valley Water to convey water from either of its treatment plants to areas that before could only be fed by running wells or buying water from MWDSLS.\n" +
                "Any water from the treatment plants serving areas north through this station is considered saved water in Jordan Valley Water s conjunctive man- agement agreement with Central Utah Water Conservancy District.\n" +
                "11 Title HereDistribution Chlorine Residual Monitoring 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 8200 South 1300 East 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 9800 South 2300 East 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n SERWTP 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 10730 South 1300 East 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 4500 South 300 East 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n N o D at a 110 East 11400 South There is an on-line chlorine analyzer monitoring the water leaving each treatment plant and an additional 14 on-line chlorine analyers throughout the distribution system that continually send information back to the SCADA.\n" +
                "This information is used to monitor quality and system integrity.\n" +
                "12 Title Here Chlorine Residual Monitoring 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 9000 South JA-2 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 6200 South 3200 West 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n JVWTP 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 12600 South 3200 West 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n Terminal Reservoir 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 2100 South JA-3 Distribution 13 Title HereDistribution Chlorine Residual Monitoring 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 14600 South W. Frontage Rd 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 14500 South 5600 West 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 5800 West 10200 South 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 4700 South 6000 West 14 Title Here Fluoride Monitoring 9400 South 1200 East 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 8200 South 1300 East 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 10730 South 1300 East 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n SERWTP 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 4500 South 300 East 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 110 East 11400 South In addition to the on-line fl uoride analyzers at each of the fl uoride feed locations, there are an additional eight located throughout the distribution system.\n" +
                "These are used to ensure even distribution of fl uoride and for regulatory reporting purposes.\n" +
                "Distribution 15 Title HereDistribution Fluoride Monitoring 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n JVWTP 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n 6200 South 3200 West 0.0 0.5 1.0 1.5 2.0 Max Avg Min Q4Q3Q2Q1 p ar ts p er m ill io n Terminal Reservoir Additional information about the fl uoride levels at the treatment plants is available in the treatment section of this book.\n" +
                "9000 South JA-2 16 Title Here Equipment Maintenance 0 100 200 300 400 500 EmergencyUnscheduledScheduled JunMayAprMarFebJanDecNovOctSepAugJul H o u rs Facility Maintenance 0 100 200 300 400 500 600 EmergencyUnscheduledScheduled JunMayAprMarFebJanDecNovOctSepAugJul H o u rs Grounds 0 200 400 600 800 1000 EmergencyUnscheduledScheduled JunMayAprMarFebJanDecNovOctSepAugJul H o u rs Specialty Services 0 50 100 150 200 EmergencyUnscheduledScheduled JunMayAprMarFebJanDecNovOctSepAugJul H o u rs Specialty Svcs covered by Facilities Maint Group Jan-May All Sections Combined 0 500 1000 1500 2000 EmergencyUnscheduledScheduled JunMayAprMarFebJanDecNovOctSepAugJul H o u rs In an effort to better track where the Facilities Maintenance group is spending its man hours, we are tracking hours spent on sched- uled, unscheduled and emergency tasks.\n" +
                "This will not only track where our time is being spent but will show that a District-wide Preventive Maintenance program will increase time spent on scheduled projects while decreasing unscheduled and emergency activities.\n" +
                "Specialty Services includes items such as locksmithing, HVAC systems, welding, fabricating and electrical work.\n" +
                "Time hours Equipment Maintenance Totals 5,916 Facility Maintenance Totals 4,986 Grounds Totals 6,757 Specialty Services Totals 1,290 Total 18,949 Facilities Maintenance Distribution 17 Title HereDistribution Main Line Breaks New Retail Connections 0 5 10 15 20 25 30 2005 20062006 20072007 2008 JunMayAprMarFebJanDecNovOctSepAugJul Total main line breaks FYTD for 2007 2008 92 Total main line breaks for 2006 2007 72 Total main line breaks for 2005 2006 66 Total new retail connections for 2007 2008 45 two 8-inch master meter fi reline connections 1 in May and 1 in June Total new retail connections for 2006 2007 91 Total new retail connections for 2005 2006 15 Month 3 4 1 1.5 2 Totals July 1 1 August 6 7 1 1 15 September October 4 9 13 November 3 3 December January February 1 1 March April 3 4 1 8 May 1 1 2 June Totals 17 16 6 1 2 1 43 District connections Contractor connections 18 Title Here Distribution Location 4th quarter Contractor Summary Project Owner Various Condie Construction SW Groundwater Project Work was suspended through the winter months.\n" +
                "In March, Condie mobilized equipment to 2700 West and Gardner Lane.\n" +
                "Completed the installation of the 18 waterline from 10400 to 11059 South 1300 West.\n" +
                "Finished installation of all air vacs and com- pleted misc.\n" +
                "asphalt and cleanup.\n" +
                "GPS shots and pohtos taken of the project.\n" +
                "JVWCD 6945 South 1100 East Condie Construction The Springs of Country Woods Installing 36 sewer line.\n" +
                "Removed our service laterals and 6 line oging to a hydrant for excavation and safety reasons.\n" +
                "Work was completed, services and hydrant were put back in service.\n" +
                "Cottonwood Sewer District 3300 South Granite Park NPL National Pipeline Gas Line Replacement Project Potholed our line on 3300 South to relocate our services and main lines that are in confl ict with the elevation of the installation of their 24 pipeline.\n" +
                "Work has been completed from 500 East to 200 West.\n" +
                "Questar Pony Express 14600 South 300 West Lyndon Jones Construction 30 Line and Meter Vault at Pony Express Completed installing 30 ductile line from our 48 line to a new wholesale meter vault.\n" +
                "JVWCD 10300 to 11000 South 5600 West Harper Construction 20 Relocation at 5600 West Our 20 line was realigned to go around UTA Trax and new homes in Daybreak.\n" +
                "Two new vaults were installed.\n" +
                "Final inspection was done and Harper is now workingon a punch list to complete project.\n" +
                "JVWCD 800 East 4070 South GWT Construction Woodland Cove Subdivision Relocated 70 feet of our 8 mainline pipe to install a sewer line.\n" +
                "Contractor has installed all pipeline, valves, and hydrants in the subdivision.\n" +
                "Pipeline passed both pressure and Bac-T tests.\n" +
                "Still working on services.\n" +
                "Private JVWCD 8000 W. New Bingham Hwy Absolute Constructors Zone D Reservoir A 30 waterline has been installed from the Bingham Canyon meter vault to the Zone D reservoir site.\n" +
                "JVWCD Inspections Locations Summary Jan-Jun 2008 19 Title HereDistribution Blue Stakes Summary Of the 8,063 Blue Stakes requests received by Jordan Valley so far in 2007 2008, 2,011 affected District lines and were responded to.\n" +
                "0 100 200 300 400 500 600 700 800 2004 20052006 20072007 2008 JunMayAprMarFebJanDecNovOctSepAugJul Blue Stakes Requests Responded to FYTD 0 1000 2000 3000 4000 5000 6000 7000 2004 20052005 20062006 20072007 2008 Total Blue Stakes Requests Responded to FYTD Size of Line Linear feet of pipe # of Valves 2 inch 200 77 3 inch - 4 inch 35,707 233 6 inch 384,161 1,243 8 inch 181,997 446 10 inch 38,609 104 12 inch 72,490 142 14 inch 20,869 24 16 inch 131,159 68 18 inch 25,553 16 20 inch - 21 inch 46,333 33 24 inch 117,990 74 27 inch 18,535 3 30 inch 77,698 38 33 inch 83,198 6 36 inch 11,502 1 42 inch 200 13 48 inch 26,059 21 54 inch 5,280 12 60 inch 500 2 66 inch 51,216 3 72 inch 73,920 5 78 inch 79,041 5 Totals 1,482,217 2,569 Total fi re hydrants 1,294 Updated 7 16 08 Update includes Daybreak Zone D Reservoir Zone D Pump Zone C Pump Pipeline Valve Summary 20 Title Here Distribution Vehicle Summary FYTD VEHICLE # YEAR MAKE MODEL E ODOMETER GALLONS USED FY MILES DRIVEN FY MPG FY MAINT.\n" +
                "COSTS FY # 100 - 1997 Chevy Lumina 82,036 325.1 7,026 21.61 $ 161.50 # 101 - 1997 Chevy 4x4 Blazer 76,413 108.2 1,798 16.61 $ 45.75 # 103 - 2008 Chevy 4x4 Trailblazer 8,750 554.0 8,740 15.78 $ 23.00 # 104 - 2006 Toyota Camry 19,073 386.6 10,810 27.96 $ 48.75 # 105 - 2001 Chevy Impala 44,622 291.1 6,188 21.26 $ 68.75 # 106 - 2004 Chevy 4x4 Tahoe 31,337 452.4 6,574 14.53 $ 51.00 # 107 - 2003 Chevy 4x4 Tahoe 92,139 406.1 7,700 18.96 $ 116.33 # 109 - 1999 Ford Taurus 82,198 493.9 11,848 23.99 $ 424.19 # 110 - 1999 Chevy 4x4 Tahoe 68,294 498.8 7,462 14.96 $ 220.00 # 111 - 2005 Chevy Impala 22,354 187.0 4,766 25.49 $ 29.38 # 112 - 1999 Ford Taurus 73,022 290.0 5,874 20.26 $ 137.95 # 113 - 2000 Chevy Impala 58,607 311.8 6,715 21.54 $ 33.50 # 114 - 2000 Chevy 4x4 Blazer 115,354 54.7 833 15.23 $ 0.00 # 115 - 2000 Ford Taurus 67,424 311.7 6,187 19.85 $ 160.59 # 116 - 2000 Ford Taurus 92,839 221.5 4,947 22.33 $ 43.17 # 117 - 2005 Chevy 4x4 Tahoe 55,654 1,195.8 18,865 15.78 $ 57.00 # 118 - 2008 Ford Expedition 4x4 17,424 1,194.6 17,424 14.59 $ 61.34 # 202 - 1999 Chevy 1 2 Ton 4x4 123,496 301.9 4,514 14.95 $ 51.51 # 204 - 1999 Chevy 4x4 Blazer 65,165 248.9 3,933 15.80 $ 82.65 # 205 - 1997 Ford Ranger 89,076 184.9 3,169 17.14 $ 97.33 # 206 - 2004 Ventura Van 54,727 716.7 12,418 17.33 $ 73.60 # 207 - 2000 Chevy 1 2 Ton 4x4 110,382 1,004.5 14,118 14.05 $ 152.77 # 208 - 2000 Chevy 1 2 Ton 4x4 121,325 254.2 3,384 13.31 $ 16.00 # 211 - 2003 Chevy 1 2 Ton pickup 52,544 702.5 8,244 11.74 $ 74.00 # 213 - 1999 Chevy 1 2 Ton 94,470 711.2 9,964 14.01 $ 167.36 # 214 - 1999 Chevy 1 2 Ton 114,246 506.8 7,632 15.06 $ 532.95 # 215 - 1999 Chevy 1 2 Ton 98,499 898.6 11,900 13.24 $ 166.58 # 217 - 2000 Chevy 1 2 Ton 118,299 995.6 16,175 16.25 $ 347.77 # 218 - 2000 Chevy 1 2 Ton 99,998 508.7 7,755 15.24 $ 546.13 # 219 - 2003 Chevy 1 2 Ton Ext 4x4 87,240 1,226.3 16,484 13.44 $ 426.99 # 220 - 1998 Chevy 1 2 Ton 92,982 623.9 7,057 11.31 $ 189.49 # 221 - 2003 Chevy 1 2 Ton Ext 4x4 123,565 1,409.5 18,622 13.21 $ 618.06 # 223 - 2006 Chevy 1 2 Ton Ext 4x4 25,507 986.3 14,854 15.06 $ 52.00 # 225 - 2000 Chevy 1 2 Ton 86,686 547.8 7,034 12.84 $ 132.01 # 226 - 2000 Chevy 1 2 Ton 91,176 789.6 12,272 15.54 $ 122.59 # 227 - 2001 Chevy 1 2 Ton Ext 4x4 79,145 1,262.7 19,311 15.29 $ 200.47 # 228 - 2001 Chevy 1 2 Ton Ext 4x4 107,785 321.8 4,135 12.85 $ 0.00 # 229 - 2001 Chevy 1 2 Ton Ext 4x4 103,830 974.1 12,879 13.22 $ 298.00 # 230 - 2004 Chevy 1 2 Ton Ext 4x4 88,477 1,393.6 22,684 16.28 $ 202.36 # 231 - 2004 Chevy 1 2 Ton Ext 4x4 107,064 2,413.1 35,605 14.75 $ 476.04 # 233 - 2002 Chevy 3 4 Ton Ext 4x4 110,341 1,302.8 16,620 12.76 $ 519.15 # 234 - 2002 Chevy 1 2 Ton Ext 4x4 98,248 769.6 8,462 11.00 $ 56.00 # 235 - 2004 Chevy 1 2 Ton 45,612 910.6 11,703 12.85 $ 54.00 # 236 - 2005 Chevy 3 4 Ton Ext 4x4 26,372 1,386.2 15,658 11.30 $ 93.00 # 237 - 2005 Chevy 1 2 Ton 22,486 446.3 7,182 16.09 $ 51.00 # 238 - 2005 Chevy 1 2 Ton Pickup 42,538 707.7 12,820 18.12 $ 55.00 # 239 - 2005 Chevy Colorado 4x4 47,203 737.0 13.857 18.80 $ 79.00 # 240 - 1997 Ford 1 2 Ton 76,602 216.0 2,356 10.91 $ 65.00 # 241 - 1997 Ford 1 2 Ton 124,807 69.2 649 9.38 $ 26.00 # 242 - 1999 Chevy 3 4 Ton 117,930 1,057.8 11,229 10.62 $ 211.17 # 243 - 1999 Chevy 3 4 Ton 112,993 854.1 9,573 11.21 $ 80.00 # 245 - 2003 Chevy 3 4 CB 4x4 Pickup 64,354 1,107.3 12,202 11.02 $ 60.00 # 250 - 2006 Chevy 1 2 Ton Ext 4x4 28,660 796.5 12,608 15.83 $ 51.00 # 251 - 2006 Chevy 1 Ton 4x4 23,783 896.9 9,412 10.49 $ 56.00 # 252 - 2007 Chevy 3 4 Ton Ext 4x4 2,241 219.1 2,073 9.46 $ 37.00 # 253 - 2007 Chevy 1 2 Ton 7,715 486.5 7,627 15.68 $ 0.00 # 254 - 2007 Chevy 3 4 Ton 4x4 8,266 639.4 7,725 12.08 $ 34.00 # 255 - 2008 Chevy 3 4 Ton Ext 4x4 3,950 316.6 3,850 12.16 $ 34.00 # 256 - 2008 Chevy 3 4 Ton Ext 4x4 6,921 523.5 6,771 12.93 $0.00 # 300 - 2004 Ford F550 DESL 20,615 566.4 4,980 8.79 $ 140.00 #301 - 1996 Ford F350 Service Truck 6,215 866.1 6,055 6.99 $ 52.00 # 302 - 2003 Ford F550 DESL 53,048 1,146.9 10,249 8.94 $ 388.00 # 303 - 1998 Ford F350 DESL 95,455 840.7 7,936 9.44 $ 290.23 # 304 - 1998 Ford F350 DESL 108,452 361.7 3,624 10.02 $ 0.00 # 306 - 2006 Ford F550 DESL 16,256 1,312.4 9,077 6.92 $ 189.00 # 307 - 1998 Dodge W35 DESL 98,017 222.3 2,421 10.89 $ 32.00 # 308 - 2008 Ford F550 Service Truck 4,689 695.2 4,564 6.57 $ 49.00 # 309 - 2006 Ford F550 DESL 30,001 1,250.3 9,253 7.40 $ 243.29 # 310 - 1997 Ford F350 Dump DESL 78,975 638.1 6,975 10.93 $ 830.44 # 312 - 1999 Chevy HD Service DESL 93,386 351.3 3,632 10.34 $ 141.60 # 406 - 2000 INTL 4900 Dump DESL 42,728 971.9 5,914 6.08 $ 308.96 # 407 - 1998 INTL 3900 Dump DESL 72,622 1,028.0 6,241 6.07 $ 1,348.00 # 408 - 1998 INTL 3900 Dump DESL 69,212 740.3 4,802 6.49 $ 270.00 # 409 - 2004 INTL 4400 Dump DESL 13,255 1,687.7 8,505 5.04 $ 429.00 Surplused TOTALS 52,388.8 674,505 12.87 $ 12,981.70 Admin Distribution Treatment Water Supply 21 Title HereDistribution System Equalization Storage Reservoir Summary Steel Reservoirs Concrete Reservoirs 2800 East 9400 South 1 MG, 2 MG 2300 East 9800 South 6 MG 4760 South Naniloa 2 MG no longer in service 3200 West 6200 South 2 MG, 2 MG, 8 MG 5200 West 6200 South 2 MG 6000 West 4700 South 1 MG 6 MG, 2 MG 4800 West 4500 South 1 MG, 2 MG, 5 MG, 5 MG 3600 West 10200 South 3 MG 5700 West 10200 South 3 MG 6920 West 10200 South 3 MG 15305 South 3200 West 8 MG, 1 MG 11574 South 2580 East 1 MG 14271 South State Street 0.2 MG, 0.4 MG 14500 South 5600 West 3 MG 5820 South 3800 West 100 MG steel concrete 2800 East 9400 South 2800 East 9400 South 2300 East 9800 South 1 MG - steel Constructed 1956 Interior paint 1980 Exterior paint 1995 Inspected cleaned 4 2008 2 MG - steel Constructed 1964 Interior paint 1989 Exterior paint 9 2003 Inspected cleaned 4 2008 6 MG - concrete Constructed 1970 Inspected cleaned 3 2006 4760 South Naniloa 4800 West 4500 South 4800 West 4500 South 2 MG - concrete Constructed 1962 Inspected cleaned 1994 no longer in service 1 MG - steel Constructed 1956 Interior paint 1997 Exterior paint 1984 Inspected cleaned 3 2006 2 MG - steel Constructed 1956 Interior paint 9 2008 Exterior paint 1984 Inspected cleaned 4 2006 Inspected cleaned means inspected, repaired and cleaned according to AWWA standards.\n" +
                "22 Title Here Distribution 4800 West 4500 South 4800 West 4500 South 6000 West 4700 South East 5 MG - steel Constructed 1965 Interior paint 1999 Exterior paint 2008 Inspected cleaned 5 2003 West 5 MG - steel Constructed 1969 Interior paint 1993 Exterior paint 2008 Inspected cleaned 5 2008 1 MG - steel Constructed 1956 Interior paint 1980 Exterior paint 1984 Inspected cleaned 12 2001 6000 West 4700 South 6000 West 4700 South 3200 West 6200 South 2 MG - concrete Constructed 1962 Inspected cleaned 1 2003 6 MG - concrete Constructed 1966 Inspected cleaned 7 2002 8 MG - steel Constructed 1968 Interior paint 1996 Exterior paint 1991 Inspected cleaned 4 2004 3200 West 6200 South 3200 West 6200 South 5200 West 6200 South North 2 MG - steel Constructed 1961 Interior paint 2008 Exterior paint 1987 Inspected cleaned 3 2002 South 2 MG - steel Constructed 1964 Interior paint 4 2002 Exterior paint 1983 partial Inspected cleaned 2 2004 2 MG - concrete Constructed 1962 Inspected cleaned 3 2008 3600 West 10200 South 5700 West 10200 South 6920 West 10200 South Old Bingham 3 MG - concrete Constructed 1981 Exterior paint July 2003 Inspected cleaned 11 2007 3 MG - concrete Constructed 1981 Exterior paint July 2003 Inspected cleaned 11 2007 3 MG - concrete Constructed 1976 Inspected cleaned 8 2006 23 Title HereDistribution 5600 West 14500 South 14271 S. Minuteman Dr. 14271 S. Minuteman Dr. 3 MG - concrete Constructed 2000 Inspected cleaned 3 2004 400k - concrete Constructed 1950 Inspected cleaned 3 2004 200k - concrete Constructed 1930 Inspected cleaned 4 2004 These tanks are owned by Utah State Department of Corrections but are operated and maintained by Jordan Valley Water Conservancy District.\n" +
                "JVWTP Finished Water JVWTP Backwash SERWTP 8 MG - concrete Constructed 1974 Inspected cleaned 6 2005 1 MG - steel Constructed 1974 Inspected cleaned 1997 1 MG - concrete Constructed 1983 Inspected cleaned 6 2005 3800 West 5800 South Terminal 100 MG - concrete Constructed 1983 Expanded 1997 Inspected cleaned 5 2005 Franklin Hi-Country Upper Franklin Hi-Country 300k - steel Constructed 1971 Interior paint N A Exterior paint N A Inspected cleaned N A 2 50k - steel N S Both constructed 1994 Interior paint N A Exterior paint N A Inspected cleaned 6 2004 These tanks are owned by Hi-Country Phase I Homeowners Association but are operated and maintained by Jordan Valley Water Conservancy District.\n" +
                "3 MG - concrete Constructed 2003 Inspected cleaned 3 2006 24 Title Here reatm nt JVWTP SERWTP TOTALS General information 07 08 07 08 07 08 Rated capacity in MGD 180 20 200 Maximum daily effl uent fl ow in MGD 169.70 18.27 187.97 Average daily fl ow during operation in MGD 65.43 12.08 77.51 Percent of fi scal year in operation 94 78 Plant production in acre-feet Total fl ow into plant 69,587 10,592 75,179 Plant use loss 690 136 4,446 Total treated water to distribution or injected 68,897 10,456 70,733 Combined total treated water to system acre-feet 70,733 Treatment costs provided year end only Personnel $1,117,146a $375,583 $1,492,729 Chemicals $783,732 $202,593 $986,325 Utilities $214,316 $68,933 $283,249 Other $513,851 $119,572 $633,423 Total treatment expenses $2,629,045 $766,681 $3,395,726 Average cost per acre-foot provided year end a Personnel costs for JVWTP include operators, admin, lab, compliance and maintenance staff.\n" +
                "$48.00 25 Title Herereatm nt Treatment Costs $0 $5 $10 $15 $20 $ AF 06 07 $ AF 07 08 JunMayAprMarFebJanDecNovOctSepAugJul $100k $200k $300k $400k $500k $600k $700k $800k Tot cost FYTD 06 07 Tot cost FYTD 07 08 $0 $1 $2 $3 $4 $5 $6 $7 $8 $ AF 06 07 $ AF 07 08 JunMayAprMarFebJanDecNovOctSepAugJul $0 $10k $20k $30k $40k $50k $60k Tot cost FYTD 06 07 Tot cost FYTD 07 08 $0 $10 $20 $30 $40 $50 $ AF 06 07 $ AF 07 08 JunMayAprMarFebJanDecNovOctSepAugJul $0 $50k $100k $150k $200k Tot cost FYTD 06 07 Tot cost FYTD 07 08 $0 $5 $10 $15 $20 $ AF 06 07 $ AF 07 08 JunMayAprMarFebJanDecNovOctSepAugJul $0 $30k $60k $90k $120k $150k Tot cost FYTD 06 07 Tot cost FYTD 07 08 $0 $2 $4 $6 $8 $10 $ AF 06 07 $ AF 07 08 JunMayAprMarFebJanDecNovOctSepAugJul $0 $5k $10k $15k $20k $25k Tot cost FYTD 06 07 Tot cost FYTD 07 08 $0 $2 $4 $6 $8 $10 $ AF 06 07 $ AF 07 08 JunMayAprMarFebJanDecNovOctSepAugJul $0 $10k $20k $30k $40k $50k $60k Tot cost FYTD 06 07 Tot cost FYTD 07 08 C o st p er A F C o st p er A F C o st p er A F C o st p er A F C o st p er A F C o st p er A F C u m u la ti ve C o st C u m u la ti ve C o st C u m u la ti ve C o st C u m u la ti ve C o st C u m u la ti ve C o st C u m u la ti ve C o st JVWTP Chemical Costs SERWTP Chemical Costs JVWTP Natural Gas Costs SERWTP Natural Gas Costs JVWTP Power Costs SERWTP Power Costs JV- Plant was offl ine during months no cost per acre foot is shown.\n" +
                "Note August and September 2007 costs are elevated due to sustained use of powdered activated carbon to combat taste and odor.\n" +
                "26 Title Here reatm nt Jordan Valley Water Treatment Plant JVWTP is a conventional-process treatment plant with a rated capacity of 180 million gallons per day MGD.\n" +
                "Source water for the treatment plant is conveyed from the Provo River at the Olmsted Diversion, through the Jordan Aque- duct.\n" +
                "Provo River water may also be diverted at the Murdock Diversion near the entrance of Provo Canyon, and conveyed through the Murdock Canal.\n" +
                "JVWTP is operated by Jordan Valley on behalf of itself and Metropolitan Water District of Salt Lake Sandy.\n" +
                "The plant is owned 2 7 by Metro and 5 7 by Jordan Valley.\n" +
                "Gaps in graph data indicate the plant was off-line.\n" +
                "Southeast Regional Water Treatment Plant With a rated capacity of 20 MGD, SERWTP uses a unique process of high rate clarifi cation to quickly settle suspended solids.\n" +
                "The source water for the treatment plant is obtained from multiple sources.\n" +
                "A portion of the water is conveyed through the Salt Lake Aqueduct, with the intake located at the base of Deer Creek Dam.\n" +
                "The remaining portion of source water comes from snow pack runoff collected into the Draper Diversion from fi ve mountain streams South Fork, Middle Fork, Bells Canyon, Rocky Mouth, and Big Willow.\n" +
                "Gaps in graph data indicate the plant was off-line.\n" +
                "Mountain stream fl ows treated through SERWTP were less than in past years because more economical water from Deer Creek was available.\n" +
                "0 500 1000 1500 2000 FYE 2006 FYE 2007 FYE 2008 JunMayAprMarFebJanDecNovOctSepAugJul 0 3000 6000 9000 12000 15000 FYTD 2006 FYTD 2007 FYTD 2008 0 300 600 900 1200 1500 FYE 2006 FYE 2007 FYE 2008 JunMayAprMarFebJanDecNovOctSepAugJul 0 500 1000 1500 2000 2500 3000 FYTD 2006 FYTD 2007 FYTD 2008 0 3000 6000 9000 12000 15000 FYE 2006 FYE 2007 FYE 2008 JunMayAprMarFebJanDecNovOctSepAugJul 0 10000 20000 30000 40000 50000 60000 70000 80000 FYTD 2006 FYTD 2007 FYTD 2008 Total Treated Water M o n th ly V o lu m e A F Y TD V o lu m e A F Total Treated Water M o n th ly V o lu m e A F Y TD V o lu m e A F Mountain Stream Flows M o n th ly V o lu m e A F Y TD V o lu m e A F 27 Title Herereatm nt Turbidity Current regulations for surface water require combined effl uent turbidity to be below 0.3 NTU 95 percent of the time, and to never exceed 1.0 NTU.\n" +
                "There are also requirements for individual fi lters.\n" +
                "The Partnership for Safe Water has set a fi nished water turbidity goal of 0.1 NTU, which JVWTP and SERWTP have adopted and typically meet.\n" +
                "JVWTP 0.0 0.1 0.2 0.3 0.4 Regulatory Limit District Upper Limit Goal Minimum Average Maximum JunMayAprilMarFebJanDecNovOctSepAugJul Fi n is h ed W at er T u rb id it y N TU Fi n is h ed W at er T u rb id it y N TU 0.0 0.1 0.2 0.3 0.4 Regulatory Limit District Upper Limit Goal Minimum Average Maximum JunMayAprMarFebJanDecNovOctSepAugJul SERWTP Maximum turbidity goal 0.10 Avg fi nished water turbidity for the year 0.03 NTU Maximum fi nished water turbidity 0.11 NTU Goal achieved for the year 99.7 Most consecutive days below 0.10 NTU for the year 325 Spike registered at.11 NTU for just six minutes.\n" +
                "Avg fi nished water turbidity for the year 0.06 NTU Maximum fi nished water turbidity 0.17 NTU Goal achieved for the year 98 Most consecutive days below 0.10 NTU for year 63 Maximum turbidity goal 0.10 95 of readings must be below 0.3 NTU Filter Performance Unit Filter Run Volume UFRV is a measure of the volume of water per area of fi lter as a means to determine fi lter effi ciency.\n" +
                "Typically a UFRV of 5000 gal SF or more is considered good.\n" +
                "Operations person- nel are currently working several fi lter surveillance projects to improve overall effi ciency at both the JVWTP and SERWTP.\n" +
                "The graphs below also show a comparison of the average number of fi lter backwashes per month.\n" +
                "Typically higher UFRVs will correspond to fewer backwashes unless the fi lter becomes ineffi cient due to process disruptions, water quality, or other contributing factors.\n" +
                "0 3000 6000 9000 12000 15000 JunMayAprMarFebJanDecNovOctSepAugJul 0 100 200 300 400 500 0 5000 10000 15000 20000 JunMayAprMarFebJanDecNovOctSepAugJul 0 100 200 300 400 500 U n it F ilt er R u n V o lu m e g al S F SERWTP UFRV FYE 2006UFRV FYE 2007 UFRV FYE 2005UFRV FYE 2007UFRV FYE 2008 UFRV FYE 2006 JVWTP U n it F ilt er R u n V o lu m e g al S F FYE 2008 average UFRV 11,172 gal sf FB fi lter backwashes FYE 2007 average UFRV 9,832 gal sf FYE 2006 average UFRV 10,374 gal sf FYE 2008 average UFRV 6,092 gal sf FB fi lter backwashes FYE 2007 average UFRV 5,137 gal sf FYE 2006 average UFRV 6,096 gal sf FB FYE 2007FB FYE 2008 FB FYE 2006 N u m b er o f fi lt er b ac kw as h es N u m b er o f fi lt er b ac kw as h es FB FYE 2007FB FYE 2008 FB FYE 2006 28 Title Here reatm nt 0.0 0.5 1.0 1.5 2.0 2.5 District Upper Limit Goal Regulatory Minimum RequiredMin Avg Max JunMayAprMarFebJanDecNovOctSepAugJul Chlorine Disinfection Concentration x time CT is a measure of disinfection effective- ness which varies with water temperature, pH and disinfectant.\n" +
                "Current regulations require suffi cient CT to achieve 99.9 percent inactivation of Giardia and 99.99 percent inactivation of viruses.\n" +
                "Compliance is determined by a CT ratio which compares the amount of CT achieved to the amount required.\n" +
                "A minimum CT ratio of 1.0 and a chlorine residual of 0.2 mg L is required.\n" +
                "Optimizing disinfection at the two treatment plants includes maintaining an adequate chlorine residual through the treatment process and throughout the distribution system while minimiz- ing the formation of disinfection by-products.\n" +
                "In order to balance these two objectives, Jordan Valley strives to maintain a chlorine residual that meets CT and provides adequate residual throughout the distribution system, but does not exceed 1.3 mg L.\n" +
                "During the winter months this maximum must be increased at SE to 1.5 mg L in order to meet CT requirements in the fi nished treated water.\n" +
                "Modifi cations to the chlorine feed systems at both JVWTP and SERWTP have been completed.\n" +
                "These modifi cations allow for tighter control of the chlorine being fed into the process.\n" +
                "Average residual for the year 1.14 mg L Maximum residual 1.89 mg L Minimum residual 0.39 mg L Goal achieved for the year 84 Average residual for the year 1.07 mg L Maximum residual 2.20 mg L Minimum residual 0.65 mg L Goal achieved for the year 85 Average CT ratio for the year 5.91 mg L Minimum CT ratio for the year 1.47 mg L Average CT ratio for the year 2.36 mg L Minimum CT ratio for the year 1.13 mg L M in im u m C T R at io 0.0 0.5 1.0 1.5 2.0 2.5 District Upper Limit Goal Regulatory Minimum RequiredMin Avg Max JunMayAprMarFebJanDecNovOctSepAugJul 0 5 10 15 20 25 Regulatory MinimumMinimum Ratio JunMayAprMarFebJanDecNovOctSepAugJul JVWTP JVWTP C h lo ri n e Re si d u al m g L Chlorine Residual Minimum CT Ratio SERWTP C h lo ri n e Re si d u al m g L M in im u m C T R at io 0 5 10 15 20 Regulatory MinimumMinimum Ratio JunMayAprMarFebJanDecNovOctSepAugJul SERWTP 29 Title Here JVWTP Fluoride Concentration reatm nt Fluoride 0.0 0.5 1.0 1.5 2.0 Operating Range Max Operating Range Min MIN MAX AVG JunMayAprMarFebJanDecNovOctSepAugJul Pa rt s Pe r M ill io n 0.0 0.5 1.0 1.5 2.0 Operating Range Max Operating Range Min MIN MAX AVG JunMayAprMarFebJanDecNovOctSepAugJul Pa rt s Pe r M ill io n SERWTP Fluoride Concentration Target Range 0.9 - 1.2 mg L Average Concentration 0.95 mg L Maximum Concentration 1.74 mg L Minimum Concentration 0.20 mg L Goal Range Achieved for the year 88 Target Range 0.9 - 1.2 mg L Average Concentration 0.94 mg L Maximum Concentration 1.44 mg L Minimum Concentration 0.20 mg L Goal Range Achieved for the year 98 30 Title Here JVWTP TOC Removal reatm nt Total Organic Carbon TOC Removal Total organic carbon TOC removal is another measure of the overall effectiveness of the treatment process.";

        String text2 = "Hydrant and main line fl ushing, main line breaks, reservoir cleaning and irrigation of landscaping at Jordan Valley sites.";

        String fixed = fixDocumentWordBreaks(text);
        System.out.println(fixed);
    }

    public static String fixDocumentWordBreaks(String text) {
        List<CoreMap> sentences = detectSentencesStanford(text);

        StringBuilder fixedDocument = new StringBuilder();
        for (CoreMap sentence : sentences) {
            List<TreeMap<Integer, String>> resolvedIndices = new ArrayList<>();
            List<CoreLabel> tokens = detectTokensStanford(sentence.toString());
            for (int i = 0; i < tokens.size(); i++) {
                CoreLabel token = tokens.get(i);
                String currentWord = token.word();
                TreeMap<Integer, String> currentMap = new TreeMap<>();
                currentMap.put(i, currentWord);
                if (!SpellChecker.check(currentWord.toLowerCase()) && i <= tokens.size() - 1) {
                    if (!Pattern.compile( "[0-9]" ).matcher(currentWord).find()) {

                        TreeMap<Integer, String> correctedMap = correctSpelling(tokens, i, i, new TreeMap<>(currentMap));
                        if (correctedMap != null) { //spelling correction was successful
                            resolvedIndices.add(correctedMap);
                            i = correctedMap.lastKey();
                        } else { //failed to correct spelling
                            resolvedIndices.add(currentMap);
                        }
                    } else {
                        resolvedIndices.add(currentMap);
                    }
                } else {
                    resolvedIndices.add(currentMap);
                }
            }

            int numMerges;
            do {
                numMerges = mergeWordParts(resolvedIndices);
            } while (numMerges > 0);

            StringBuilder fixedSentence = new StringBuilder();
            for (TreeMap<Integer, String> resolvedWord : resolvedIndices) {
                fixedSentence.append(StringUtils.join(resolvedWord.values(), ""));
                fixedSentence.append(tokens.get(resolvedWord.lastKey()).after());
            }

            fixedDocument.append(fixedSentence.toString());
            fixedDocument.append(System.lineSeparator());
        }

        return fixedDocument.toString();
    }

    public static int mergeWordParts(List<TreeMap<Integer, String>> resolvedIndices) {
        int numMerges = 0;
        for (int i = 0; i < resolvedIndices.size(); i++) {
            Map<Integer, String> resolvedWord = resolvedIndices.get(i);
            for (int j = i + 1; j < resolvedIndices.size(); j++) {
                Map<Integer, String> otherWord = resolvedIndices.get(j);
                if(!Collections.disjoint(resolvedWord.keySet(), otherWord.keySet())) {
                    resolvedWord.putAll(otherWord);
                    resolvedIndices.remove(j);
                    j--;
                    numMerges++;
                }
            }
        }
        return numMerges;
    }

    public static TreeMap<Integer, String> correctSpelling(List<CoreLabel> tokens, int center, int index, TreeMap<Integer, String> spellCorrection) {
        String prevToken = index > 0 ? tokens.get(index - 1).word() : null;
        String currToken = tokens.get(index).word();
        String nextToken = index < tokens.size() - 1 ? tokens.get(index + 1).word() : null;

        spellCorrection.put(index, currToken);

        boolean leftRecurseOK = false;
        if (prevToken != null && !Pattern.compile( "[0-9]" ).matcher(prevToken).find() && !spellCorrection.containsKey(index - 1)) {
            spellCorrection.put(index - 1, prevToken);
            leftRecurseOK = true;
        }

        boolean rightRecurseOK = false;
        if (nextToken != null && !Pattern.compile( "[0-9]" ).matcher(nextToken).find() && !spellCorrection.containsKey(index + 1)) {
            spellCorrection.put(index + 1, nextToken);
            rightRecurseOK = true;
        }

        TreeMap<Integer, String> left = new TreeMap(spellCorrection.headMap(center, true));
        TreeMap<Integer, String> right = new TreeMap(spellCorrection.tailMap(center, true));

        String correctedLeft = StringUtils.join(left.values(), "");
        String correctedRight = StringUtils.join(right.values(), "");
        String correctedAll = StringUtils.join(spellCorrection.values(), "");

        boolean leftPassed = SpellChecker.check(correctedLeft.toLowerCase());
        boolean rightPassed = SpellChecker.check(correctedRight.toLowerCase());
        boolean allPassed = SpellChecker.check(correctedAll.toLowerCase());

        if (rightPassed) {
            //always prefer forward direction
            return right;
        } else if (allPassed) {
            return spellCorrection;
        } else if (leftPassed) {
            //lowest priority is given to backward search
            return left;
        } else {
            if (correctedAll.length() > 30 || spellCorrection.size() == 15) {
                return null;
            } else {
                right = rightRecurseOK ? correctSpelling(tokens, center, index + 1, new TreeMap<>(spellCorrection)) : null;
                left = leftRecurseOK ? correctSpelling(tokens, center, index - 1, new TreeMap<>(spellCorrection)) : null;
                if (right != null) {
                    return right;
                } else {
                    return left;
                }
            }
        }
    }

//    public static String fixDocumentWordBreaks(String text) {
//        List<CoreMap> sentences = detectSentencesStanford(text);
//
//        StringBuilder fixedDocument = new StringBuilder();
//        for (CoreMap sentence : sentences) {
//            StringBuilder fixedSentence = new StringBuilder();
//            List<CoreLabel> tokens = detectTokensStanford(sentence.toString());
//            for (int i = 0; i < tokens.size(); i++) {
//                CoreLabel token = tokens.get(i);
//                if (!SpellChecker.check(token.toString().toLowerCase()) && i <= tokens.size() - 1) {
//                    String current = token.word();
//                    if (!Pattern.compile( "[0-9]" ).matcher(current).find()) {
//                        List<String> corrected = new ArrayList<>();
//                        corrected.add(token.word());
//                        if (correctSpelling(tokens.subList(i + 1, tokens.size()), corrected)) {
//                            List<CoreLabel> fixedTokens = tokens.subList(i, i + corrected.size());
//                            fixedSentence.append(StringUtils.join(fixedTokens, ""));
//                            fixedSentence.append(fixedTokens.get(fixedTokens.size() - 1).after());
//                            i += corrected.size() - 1;
//                        } else { //failed to correct spelling
//                            fixedSentence.append(token.word());
//                            fixedSentence.append(token.after());
//                        }
//                    } else {
//                        fixedSentence.append(token.word());
//                        fixedSentence.append(token.after());
//                    }
//                } else {
//                    fixedSentence.append(token.word());
//                    fixedSentence.append(token.after());
//                }
//            }
//            fixedDocument.append(fixedSentence.toString());
//            fixedDocument.append(System.lineSeparator());
//        }
//
//        return fixedDocument.toString();
//    }

//    public static boolean correctSpelling(List<CoreLabel> tokens, List<String> current) {
//        if (tokens.size() == 0) {
//            return false;
//        }
//        String nextToken = tokens.get(0).word();
//        if (Pattern.compile( "[0-9]" ).matcher(nextToken).find()) {
//            return false;
//        }
//        current.add(nextToken);
//
//        String corrected = StringUtils.join(current, "");
//        if (!SpellChecker.check(corrected.toLowerCase())) {
//            if (corrected.length() > 30 || current.size() == 15) {
//                return false;
//            }
//
//            return correctSpelling(tokens.subList(1, tokens.size()), current);
//        } else {
////            if (corrected.length() < 6 && tokens.size() > 1) { //possible false-positive?
////                String nextToken2 = tokens.get(1).word();
////
////            }
//            return true;
//        }
//    }

    public static List<List<TaggedWord>> tagText(String text) {
        String modelPath = Tools.getProperty("nlp.stanfordPOSTagger");
        MaxentTagger tagger = new MaxentTagger(modelPath);
        List<List<HasWord>> sentences = MaxentTagger.tokenizeText(new BufferedReader(new StringReader(text)));
        List<List<TaggedWord>> taggedSentences = new ArrayList<>();
        for (List<HasWord> sentence : sentences) {
            List<TaggedWord> tSentence = tagger.tagSentence(sentence);
            taggedSentences.add(tSentence);
        }
        return taggedSentences;
    }

}
