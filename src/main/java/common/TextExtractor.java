package common;

import com.google.common.base.Charsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class TextExtractor {

    final static Logger logger = LogManager.getLogger(TextExtractor.class);

    public static final Map<String, Function<File, ProcessedDocument>> extractors;

    static {
        extractors = new HashMap<>();
        extractors.put("application/pdf", TextExtractor::extractPDFText);
        extractors.put("text/plain", TextExtractor::extractPlainText);
        extractors.put("application/msword", TextExtractor::extractDocText);
        extractors.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", TextExtractor::extractDocxText);
    }

    public static boolean canExtractText(File file) {
        try {
            String contentType = Files.probeContentType(file.toPath());
            return extractors.containsKey(contentType);
        } catch (IOException e) {
            return false;
        }
    }

    public static ProcessedDocument extractText(File file) {
        try {
            String contentType = Files.probeContentType(file.toPath());
            logger.info("uploaded file type: " + contentType);
            if (extractors.containsKey(contentType)) {
                return extractors.get(contentType).apply(file);
            } else {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static ProcessedDocument extractPDFText(File file) {
        ProcessedDocument pd = Tools.extractPDFText(file);
        return pd;
    }

    private static ProcessedDocument extractPlainText(File file) {
        try {
            String docText = com.google.common.io.Files.toString(file, Charsets.UTF_8);
            ProcessedDocument pd = new ProcessedDocument();
            pd.setExtractedText(docText);
            return pd;
        } catch (IOException e) {
            return null;
        }
    }

    private static ProcessedDocument extractDocText(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            HWPFDocument doc = new HWPFDocument(fis);
            WordExtractor docExtractor = new WordExtractor(doc);
            String docText = docExtractor.getText();
            ProcessedDocument pd = new ProcessedDocument();
            pd.setExtractedText(docText);
            return pd;
        } catch (IOException e) {
            return null;
        }
    }

    private static ProcessedDocument extractDocxText(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            XWPFDocument docx = new XWPFDocument(fis);
            XWPFWordExtractor docxExtractor = new XWPFWordExtractor(docx);
            String docText = docxExtractor.getText();
            ProcessedDocument pd = new ProcessedDocument();
            pd.setExtractedText(docText);
            return pd;
        } catch (IOException e) {
            return null;
        }
    }

}
