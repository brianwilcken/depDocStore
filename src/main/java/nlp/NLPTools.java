package nlp;

import common.Tools;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

public class NLPTools {
    public static TrainingParameters getTrainingParameters(int iterations, int cutoff) {
        TrainingParameters mlParams = new TrainingParameters();
        mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
        mlParams.put(TrainingParameters.TRAINER_TYPE_PARAM, EventTrainer.EVENT_VALUE);
        mlParams.put(TrainingParameters.ITERATIONS_PARAM, iterations);
        mlParams.put(TrainingParameters.CUTOFF_PARAM, cutoff);

        return mlParams;
    }

    public static final class TrainingParameterTracker {
        private int iStart = 25; //Starting iterations
        private int iStep = 5; //Iteration step size
        private int iStop = 225; //Max iterations
        private int iSize = (iStop - iStart)/iStep + 1;

        private int cStart = 1; //Starting cutoff
        private int cStep = 1; //Cutoff step size
        private int cStop = 6; //Max cutoff
        private int cSize = (cStop - cStart)/cStep + 1;

        private NLPTools.TrainingParameterTracker.Tuple current;
        private int coordI;
        private int coordC;

        private NLPTools.TrainingParameterTracker.Tuple[][] grid; //keeps track of performance measures for each i/c pair

        public final class Tuple {
            int i;
            int c;
            double P;

            public Tuple(int i, int c) {
                this.i = i;
                this.c = c;
                this.P = 0;
            }
        }

        private void makeGrid() {
            grid = new NLPTools.TrainingParameterTracker.Tuple[iSize][cSize];

            for (int i = 0; i < iSize; i++) {
                for (int c = 0; c < cSize; c++) {
                    int iParam = iStart + (iStep * i);
                    int cParam = cStart + (cStep * c);
                    grid[i][c] = new NLPTools.TrainingParameterTracker.Tuple(iParam, cParam);
                }
            }
        }

        public TrainingParameterTracker() {
            makeGrid();
            coordI = 0;
            coordC = 0;
            current = null;
        }

        public Boolean hasNext() {
            if (current == null) {
                return true;
            }
            return coordI <= (iSize - 1) || coordC <= (cSize - 1);
        }

        private void testLimitForOptimization() {
            double threshold = 0.01;
            int prevI = coordI - 1;
            int prevI2 = coordI - 2;
            double dPdi = 0;
            Boolean iCalc = false;
            int prevC = coordC - 1;
            int prevC2 = coordC - 2;
            double dPdc = 0;
            Boolean cCalc = false;


            if (prevI >= 0 && prevI2 >= 0) {
                //calculate the 1st derivative dP/di.
                NLPTools.TrainingParameterTracker.Tuple prev = grid[prevI][coordC];
                NLPTools.TrainingParameterTracker.Tuple prev2 = grid[prevI2][coordC];
                dPdi = (prev.P - prev2.P)/prevI;
                iCalc = true;

            }

            if (prevC >= 0 && prevC2 >= 0) {
                //calculate the 1st derivative dP/dc.
                NLPTools.TrainingParameterTracker.Tuple prev = grid[coordI][prevC];
                NLPTools.TrainingParameterTracker.Tuple prev2 = grid[coordI][prevC2];
                dPdc = (prev.P - prev2.P)/prevC;
                cCalc = true;
            }

            //A negative derivative here is a good indication of overfitting.
            if (iCalc && cCalc) {
                //calculate the 2nd 2D derivative d2P/didc
                double d2Pdidc = dPdi * dPdc;
                //If the 2nd 2D derivative is less than threshold this is a good indication that further
                //optimization will yield diminishing returns.  It's time to stop optimizing.
                if (d2Pdidc < 0 || d2Pdidc < threshold) {
                    coordI = iSize;
                    coordC = cSize;
                }
            }
            else if (iCalc && !cCalc) {
                //If dP/di<threshold then increment coordC and reset coordI to 0.
                if (dPdi < 0 || dPdi < threshold) {
                    ++coordC;
                    coordI = 0;
                }
            }
        }

        public NLPTools.TrainingParameterTracker.Tuple getNext() {
            testLimitForOptimization();
            if (coordI <= (iSize - 1)) {
                current = grid[coordI++][coordC];
            } else if (++coordC <= (cSize - 1)){
                coordI = 0;
                current = grid[coordI][coordC];
            }
            return current;
        }

        public NLPTools.TrainingParameterTracker.Tuple getBest() {
            NLPTools.TrainingParameterTracker.Tuple best = null;

            for (int i = 0; i < iSize; i++) {
                for (int c = 0; c < cSize; c++) {
                    if (best == null) {
                        best = grid[i][c];
                        continue;
                    }
                    NLPTools.TrainingParameterTracker.Tuple current = grid[i][c];
                    if (best.P < current.P) {
                        best = current;
                    }
                }
            }
            return best;
        }
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
