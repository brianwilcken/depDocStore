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
    private static TreeSet<String> words;

    static {
        List<String> wordsList = Arrays.asList(wordsText.split("\\r\\n"));
        words = new TreeSet<>();
        words.addAll(wordsList);
    }


    public static boolean check(String word) {
        if (!Strings.isNullOrEmpty(word)) {
            return words.contains(word);
        }
        return false;
    }

    public static void main(String[] args) {
        SpellChecker.check("");
    }
}
