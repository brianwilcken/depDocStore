package nlp;

import common.Tools;
import opennlp.tools.cmdline.namefind.NameEvaluationErrorListener;
import opennlp.tools.namefind.*;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NamedEntityRecognizer {

    public List<String> detectNamedEntities(String content, ClassPathResource nerModel) {
        try {
            return detectNamedEntities(content, nerModel.getFile().getPath());
        } catch (IOException e) {
            return null;
        }
    }

    public List<String> detectNamedEntities(String content, String nerModelPath) {
        try {
            TokenNameFinderModel model = NLPTools.getModel(TokenNameFinderModel.class, nerModelPath);
            NameFinderME nameFinder = new NameFinderME(model);

            SentenceModel sentModel = NLPTools.getModel(SentenceModel.class, new ClassPathResource(Tools.getProperty("nlp.sentenceDetectorModel")));
            String[] sentences = NLPTools.detectSentences(sentModel, content);

            TokenizerModel tokenizerModel = NLPTools.getModel(TokenizerModel.class, new ClassPathResource(Tools.getProperty("nlp.tokenizerModel")));

            List<String> namedEntities = new ArrayList<>();

            for (String sentence : sentences) {
                String[] tokens = NLPTools.detectTokens(tokenizerModel, sentence);
                Span[] nameSpans = nameFinder.find(tokens);
                for (Span span : nameSpans) {
                    int start = span.getStart();
                    int end = span.getEnd();
                    String[] entityParts = Arrays.copyOfRange(tokens, start, end);
                    String entity = String.join(" ", entityParts);
                    namedEntities.add(entity);
                }
            }

            return namedEntities;
        } catch (IOException e) {
            return null;
        }
    }

    public void trainNERModel(String trainingFilePath, String type, String modelPath) {
        try {
            ObjectStream<String> lineStream = NLPTools.getLineStreamFromFile(trainingFilePath);

            TokenNameFinderModel model;

            try (ObjectStream<NameSample> sampleStream = new NameSampleDataStream(lineStream)) {
                //Optimize iterations/cutoff using 5-fold cross validation
                NLPTools.TrainingParameterTracker tracker = new NLPTools.TrainingParameterTracker();
                while (tracker.hasNext()) {
                    OptimizationTuple optimizationTuple = tracker.getNext();
                    optimizationTuple.P = crossValidateNERModel(sampleStream, NLPTools.getTrainingParameters(optimizationTuple.i, optimizationTuple.c));
                }

                //Use optimized iterations/cutoff to train model on full dataset
                OptimizationTuple best = tracker.getBest();
                sampleStream.reset();
                model = NameFinderME.train("en", type, sampleStream, NLPTools.getTrainingParameters(best.i, best.c), new TokenNameFinderFactory());
            }

            try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelPath))) {
                model.serialize(modelOut);
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private double crossValidateNERModel(ObjectStream<NameSample> samples, TrainingParameters params) {
        TokenNameFinderEvaluationMonitor[] listeners = { new NameEvaluationErrorListener() };

        TokenNameFinderCrossValidator validator = new TokenNameFinderCrossValidator("en", "hazard", TrainingParameters.defaultParams(), new TokenNameFinderFactory(), listeners);
        try {
            validator.evaluate(samples, 5);
            return validator.getFMeasure().getFMeasure();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return -1;
        }
    }
}
