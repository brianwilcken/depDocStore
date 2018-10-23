package nlp;

import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.common.SolrDocument;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class CoreferenceResolver {
    private final static Logger logger = LogManager.getLogger(CoreferenceResolver.class);
    private StanfordCoreNLP pipeline;
    private Annotation document;

    public CoreferenceResolver() {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,coref");
        pipeline = new StanfordCoreNLP(props);
    }

    public void resolve(String text) {
        document = new Annotation(text);
        pipeline.annotate(document);
    }

    public Map<Integer, CorefChain> getCoreferences() {
        if (document != null) {
            Map<Integer, CorefChain> corefs = (Map<Integer, CorefChain>)document.get(CorefCoreAnnotations.CorefChainAnnotation.class).values();
            return corefs;
        } else {
            return null;
        }
    }

    public List<CoreMap> getSentences() {
        if (document != null) {
            List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
            return sentences;
        } else {
            return null;
        }
    }

    public List<SolrDocument> getCoreferencesFromDocument(String text, String docId, List<NamedEntity> entities) {
        resolve(text);
        Map<Integer, CorefChain> corefs = getCoreferences();
        for (Map.Entry<Integer, CorefChain> entry : corefs.entrySet()) {
            CorefChain chain = entry.getValue();
            CorefChain.CorefMention mention = chain.getRepresentativeMention();

        }

        return null;
    }

    public static void main(String[] args) throws Exception {
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

        CoreferenceResolver resolver = new CoreferenceResolver();
        resolver.resolve(text);
        Map<Integer, CorefChain> corefs = resolver.getCoreferences();
        List<CoreMap> sentences = resolver.getSentences();

        System.out.println("---");
        System.out.println("coref chains");
        for (CorefChain cc : corefs.values()) {
            System.out.println("\t" + cc);
        }
        for (CoreMap sentence : sentences) {
            System.out.println("---");
            System.out.println("mentions");
            for (Mention m : sentence.get(CorefCoreAnnotations.CorefMentionsAnnotation.class)) {
                System.out.println("\t" + m);
            }
        }
        System.out.println("");
    }
}
