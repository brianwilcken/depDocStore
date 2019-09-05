package nlp;

import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.ObjectStream;

import java.io.IOException;

public class DocumentSampleStreamWithFilenames extends DocumentSampleStream {
    public DocumentSampleStreamWithFilenames(ObjectStream<String> samples) {
        super(samples);
    }

    @Override
    public DocumentSample read() throws IOException {
        String sampleString = samples.read();

        if (sampleString != null) {

            String[] sep = sampleString.split(NLPTools.CORPUS_DATA_DELIMITER);
            String cat = sep[0];
            String filename = sep[1];
            String text = sep[2];
            String corpusData = cat + text;

            // Whitespace tokenize entire string
            String[] tokens = WhitespaceTokenizer.INSTANCE.tokenize(corpusData);

            DocumentSampleWithFilename sample;

            if (tokens.length > 1) {
                String category = tokens[0];
                String[] docTokens = new String[tokens.length - 1];
                System.arraycopy(tokens, 1, docTokens, 0, tokens.length - 1);

                sample = new DocumentSampleWithFilename(filename, category, docTokens);
            }
            else {
                throw new IOException("Empty lines, or lines with only a category string are not allowed!");
            }

            return sample;
        }

        return null;
    }
}
