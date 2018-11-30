package webapp.services;

import com.google.common.base.Strings;
import common.Tools;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import nlp.gibberish.GibberishDetector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.Future;

@Service
public class TesseractOCRService {

    final static Logger logger = LogManager.getLogger(TesseractOCRService.class);

    @Autowired
    private GibberishDetector detector;

    @Autowired
    private WorkExecutorHeartbeatService workExecutorHeartbeatService;

    public TesseractOCRService() {
        //This setting speeds up Tesseract OCR
        System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider");
    }

    @PostConstruct
    public void startHeartbeatMonitor() {
        workExecutorHeartbeatService.process("tesseractProcessExecutor", 1000, 32);
    }

    @Async("tesseractProcessExecutor")
    public Future<Boolean> process(File tiffFile, int page, Rectangle rect, List<String> lsOutput) {
        try {
            if (tiffFile.exists()) {
                Tesseract tesseract = new Tesseract();
                String tessdata = Tools.getProperty("tess4j.path");
                tesseract.setDatapath(tessdata);
                logger.info("Begin OCR processing for file " + tiffFile.getName() + " for page " + page + " on rectangle: (" + rect.getX() + ", " + rect.getY() + ") with size: " + rect.width + "x" + rect.height + " pixels");
                String output = tesseract.doOCR(tiffFile, rect);
                logger.info("Finished OCR processing for file " + tiffFile.getName() + " for page " + page + " on rectangle: (" + rect.getX() + ", " + rect.getY() + ") with size: " + rect.width + "x" + rect.height + " pixels");
                output = detector.removeGibberishLines(output);
                if (!Strings.isNullOrEmpty(output)) {
                    lsOutput.add(output);
                }
            } else {
                logger.warn("For page " + page + ": UNABLE TO PROCESS NON-EXISTANT IMAGE: " + tiffFile.getName());
            }
            return new AsyncResult<>(true);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return new AsyncResult<>(false);
        }
    }
}
