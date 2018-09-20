package nlp;

import opennlp.tools.ml.EventTrainer;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    public static <T> T getModel(Class<T> clazz, String modelFilePath) {
        try (InputStream modelIn = new FileInputStream(modelFilePath)) {

            Constructor<?> cons = clazz.getConstructor(InputStream.class);

            T o = (T) cons.newInstance(modelIn);

            return o;
        } catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException |
                IllegalArgumentException | InvocationTargetException | IOException e) {
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

    public static String[] detectTokens(TokenizerModel model, String input) {
        TokenizerME tokenDetector = new TokenizerME(model);

        String[] tokens = tokenDetector.tokenize(input);

        return tokens;
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

}
