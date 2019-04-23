package textextraction;

import common.ImageTools;
import common.SpellChecker;
import common.Tools;
import edu.stanford.nlp.ling.CoreLabel;
import net.sourceforge.lept4j.Leptonica;
import net.sourceforge.lept4j.Pix;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import nlp.NLPTools;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TextExtractionTask {
    private TextExtractionProcessManager mgr;
    private float rank;
    private int rot;
    private Rectangle rect;
    private java.util.List<String> valids;
    private String raw;
    private String cleaned;
    private int totalTokens;
    private int validTokens;
    private File tiffFile;
    private Thread myThread;
    private Pix pix;
    private Tesseract tesseract = new Tesseract();

    final static Logger logger = LogManager.getLogger(TextExtractionTask.class);

    public TextExtractionTask(TextExtractionProcessManager mgr, Rectangle rect, float rank, int rot, File tiffFile, Pix pix) {
        this.mgr = mgr;
        this.rect = rect;
        this.rank = rank;
        this.rot = rot;
        valids = new ArrayList<>();
        this.tiffFile = tiffFile;
        this.pix = Leptonica.INSTANCE.pixCopy(null, pix);

        tesseract = new Tesseract();
        String tessdata = Tools.getProperty("tess4j.path");
        tesseract.setDatapath(tessdata);
    }

    public float getRank() {
        return rank;
    }

    public int getRot() {
        return rot;
    }

    public Rectangle getRect() {
        return rect;
    }

    public java.util.List<String> getValids() {
        return valids;
    }

    public String getRaw() {
        return raw;
    }

    public void setRaw(String raw) {
        this.raw = raw;
        processOCRText();
    }

    public String getCleaned() {
        return cleaned;
    }

    public String getValidLine() {
        String validLine = getValids().stream().reduce((c, n) -> c + " " + n).orElse("");
        return validLine;
    }

    public double getPercentValid() {
        if (totalTokens > 0) {
            double percentValid = (double)validTokens / (double)totalTokens;
            return percentValid;
        } else {
            return 0d;
        }
    }

    private void processOCRText() {
        cleaned = NLPTools.deepCleanText(raw);
        List<CoreLabel> tokens = NLPTools.detectTokensStanford(cleaned);
        totalTokens = tokens.size();
        validTokens = 0;
        for (CoreLabel token : tokens) {
            String word = token.word().toLowerCase();
            if(SpellChecker.check58K(word) && !SpellChecker.checkStopwords(word)) {
                ++validTokens;
                valids.add(word);
            }
        }
    }

    public void enqueue() {
        myThread = mgr.startProcess(this::runExtraction);
    }

    public void runExtraction() {
        Pix pixRot;
        if (rot > 0) {
            pixRot = ImageTools.rotateImage(pix, rot, rect.getSize());
        } else {
            pixRot = Leptonica.INSTANCE.pixCopy(null, pix);
        }
        String savePath = getImagePath();

        File saved = ImageTools.saveImage(savePath, pixRot);
        ImageTools.disposePixs(pixRot);
        ImageTools.disposePixs(pix);

        try {
            String output = tesseract.doOCR(saved);
            setRaw(output);
        } catch (TesseractException e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                FileUtils.forceDelete(saved);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder bldr = new StringBuilder();

        bldr.append("(x, y): (" + rect.getX() + ", " + rect.getY() + ")");
        bldr.append(System.lineSeparator());
        bldr.append("(w, h): (" + rect.getWidth() + ", " + rect.getHeight() + ")");
        bldr.append(System.lineSeparator());
        bldr.append("Rank: " + rank);
        bldr.append(System.lineSeparator());
        bldr.append("Rotation: " + rot);
        bldr.append(System.lineSeparator());
        bldr.append("% Valid: " + getPercentValid());
        bldr.append(System.lineSeparator());
        bldr.append(System.lineSeparator());
        bldr.append(getValidLine());
        bldr.append(System.lineSeparator());
        bldr.append(System.lineSeparator());

        return bldr.toString();
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public int getValidTokens() {
        return validTokens;
    }

    public File getTiffFile() {
        return tiffFile;
    }

    public String getImagePath() {
        String baseName = FilenameUtils.getBaseName(tiffFile.getPath());

        int x = (int)rect.getX();
        int y = (int)rect.getY();
        int w = (int)rect.getWidth();
        int h = (int)rect.getHeight();
        String name = baseName + "_" + x + "_" + y + "_" + w + "_" + h;
        name = name + "_rank_" + String.format("%.02f", rank);
        name = name + "_rot_" + rot;
        String imagePath = tiffFile.getParent() + "\\" + name + "." + FilenameUtils.getExtension(tiffFile.getPath());

        return imagePath;
    }

    public Thread getMyThread() {
        return myThread;
    }
}
