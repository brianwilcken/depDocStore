package nlp;

import java.io.*;

import common.Tools;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.tokenize.TokenizerModel;
import org.springframework.core.io.ClassPathResource;

public class DocumentCategorizer {

    final static Logger logger = LogManager.getLogger(DocumentCategorizer.class);

    private DoccatModel model;
    private DocumentCategorizerME categorizer;
    private PorterStemmer stemmer;
    private TokenizerModel tokenizerModel;

    public DocumentCategorizer() {
        model = NLPTools.getModel(DoccatModel.class, new ClassPathResource(Tools.getProperty("nlp.doccatModel")));
        categorizer = new DocumentCategorizerME(model);
        stemmer = new PorterStemmer();
        tokenizerModel = NLPTools.getModel(TokenizerModel.class, new ClassPathResource(Tools.getProperty("nlp.tokenizerModel")));
    }

    public String detectCategory(String document) throws IOException {
        String[] docCatTokens = GetDocCatTokens(document);

        //Categorize
        double[] outcomes = categorizer.categorize(docCatTokens);
        String category = categorizer.getBestCategory(outcomes);

        return category;
    }

    private String[] GetDocCatTokens(String document) {
        String normalized = getNormalizedDocCatString(document);
        String[] tokens = NLPTools.detectTokens(tokenizerModel, normalized);

        return tokens;
    }

    private String getNormalizedDocCatString(String document) {
        String docCatStr = document.replace("\r", " ").replace("\n", " ");

        return NLPTools.normalizeText(stemmer, docCatStr);
    }
}

