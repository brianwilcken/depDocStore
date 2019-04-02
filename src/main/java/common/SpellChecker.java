package common;

import com.google.common.base.Strings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

public class SpellChecker {
    private final static Logger logger = LogManager.getLogger(SpellChecker.class);
    private final static String wordsText = Tools.getResource(Tools.getProperty("spellcheck.words"));
    private final static String words58KText = Tools.getResource(Tools.getProperty("spellcheck.words58K"));
    private final static String stopwordsText = Tools.getResource(Tools.getProperty("spellcheck.stopwords"));
    private static TreeSet<String> words;
    private static TreeSet<String> words58K;
    private static TreeSet<String> stopwords;

    static {
        List<String> wordsList = Arrays.asList(wordsText.split("\\r\\n"));
        words = new TreeSet<>();
        words.addAll(wordsList);

        wordsList = Arrays.asList(words58KText.split("\\r\\n"));
        words58K = new TreeSet<>();
        words58K.addAll(wordsList);

        wordsList = Arrays.asList(stopwordsText.split("\\r\\n"));
        stopwords = new TreeSet<>();
        stopwords.addAll(wordsList);
    }


    public static boolean check(String word) {
        if (!Strings.isNullOrEmpty(word)) {
            return words.contains(word);
        }
        return false;
    }

    public static boolean check58K(String word) {
        if (!Strings.isNullOrEmpty(word)) {
            return words58K.contains(word);
        }
        return false;
    }

    public static boolean checkStopwords(String word) {
        if (!Strings.isNullOrEmpty(word)) {
            return stopwords.contains(word);
        }
        return false;
    }

    public static void main(String[] args) {
        SpellChecker.check("");
    }
}
