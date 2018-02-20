package nlp;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.spell.PlainTextDictionary;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class LuceneExperiments {
	private SpellChecker spellChecker;
	
	public void loadDictionary() {
	    try {
	        Path dir = Paths.get("data/spellchecker/");
	        Directory directory = FSDirectory.open(dir);
	        spellChecker = new SpellChecker(directory);
	        PlainTextDictionary dictionary = new PlainTextDictionary(new FileInputStream("data/OOV_Dictionary_V1.0.tsv"));
	        IndexWriterConfig config = new IndexWriterConfig(null);
	        spellChecker.indexDictionary(dictionary, config, false);
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}

	public String performSpellCheck(String word) {
	    try {
	         String[] suggestions = spellChecker.suggestSimilar(word, 1);
	         if (suggestions.length > 0) {
	             return suggestions[0];
	         }
	         else {
	             return word; 
	         }
	    } catch (Exception e) {
	        return "Error";
	    }
	}
}
