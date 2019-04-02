package webapp.services;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import textextraction.ProcessedPage;
import common.Tools;
import common.ImageTools;
import edu.stanford.nlp.ling.TaggedWord;
import net.sourceforge.lept4j.*;
import net.sourceforge.lept4j.util.LeptUtils;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.util.PdfBoxUtilities;
import nlp.NLPTools;
import nlp.NamedEntityRecognizer;
import nlp.gibberish.GibberishDetector;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Service
public class PDFProcessingService {

    final static Logger logger = LogManager.getLogger(PDFProcessingService.class);

    private static final String temporaryFileRepo = Tools.getProperty("mongodb.temporaryFileRepo");
    private static String tessdata = Tools.getProperty("tess4j.path");
    private static Leptonica leptInstance = Leptonica.INSTANCE;

    @Autowired
    private GibberishDetector detector;

    @Autowired
    private TesseractOCRService tesseractOCRService;

    @Autowired
    private WorkExecutorHeartbeatService workExecutorHeartbeatService;

    @PostConstruct
    public void startHeartbeatMonitor() {
        workExecutorHeartbeatService.process("pdfProcessExecutor", 1000, 4);
    }
    
    @Async("pdfProcessExecutor")
    public Future<ProcessedPage> process(File pdfFile, PDDocument pdDoc, int i) {
        ProcessedPage processedPage = new ProcessedPage();
        processedPage.setPageNum(i);
        processedPage.setPageState(ProcessedPage.PageState.Normal);
        processedPage.setPageType(ProcessedPage.PageType.PlainText);
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdata);
        final double pdfGibberishThreshold = 0.75; //set this threshold very high to avoid using OCR whenever possible
        final double ocrGibberishThreshold = 0.35; //set this threshold low to encourage additional image processing when using OCR
        try {
            logger.info("Attempt to process file " + pdfFile.getName() + " page " + i + " as PDF");
            PDFTextStripper pdfStripper = new PDFTextStripper();
            pdfStripper.setStartPage(i);
            pdfStripper.setEndPage(i);
            String parsedPage = pdfStripper.getText(pdDoc);
            processedPage.setPageText(parsedPage);
            //The pdf document may contain some arbitrary text encoding, in which case text extraction
            // will be problematic.  In such a case the only option is to use OCR.
            double pdfPercentGibberish = detector.getPercentGibberish(parsedPage);
            logger.info("PDF processing for file " + pdfFile.getName() + " page " + i + " percent gibberish: " + pdfPercentGibberish);
            if (Strings.isNullOrEmpty(parsedPage.trim()) ||
                    getAsciiPercentage(parsedPage) < 0.8 ||
                    pdfPercentGibberish > pdfGibberishThreshold) {
                logger.info("PDF processing for file " + pdfFile.getName() + " page " + i + " failed.  Attempt to process page " + i + " using OCR");
                //Use OCR to extract page text
                //first convert page to TIFF format that is compatible with OCR
                String baseName = FilenameUtils.getBaseName(pdfFile.getName());
                String filename = temporaryFileRepo + baseName + "_PAGE_" + i + ".pdf";
                File pageFile = new File(filename);
                processedPage.setPageFile(pageFile);
                PdfBoxUtilities.splitPdf(pdfFile, pageFile, i, i);

                logger.info("Converting page " + i + " to TIFF format for file " + pageFile.getName());
                File tiffFile = PdfBoxUtilities.convertPdf2Tiff(pageFile);
                logger.info("Successfully produced TIFF file " + tiffFile.getName() + " from PDF page file " + pageFile.getName() + " for PDF file " + pdfFile.getName() + " page " + i);
                File binFile = null;

                try {
                    //The TIFF file may comprise a scanned page of plain text an engineering schematic or a map.  In that case,
                    //preprocessing is necessary before OCR can be performed.
                    logger.info("Now binarizing page " + i + " for file " + tiffFile.getName());
                    binFile = ImageTools.binarizeImage(tiffFile);
                    logger.info("Binarization of file " + binFile.getName() + " page " + i + " complete.  Begin OCR processing.");
                    String output = tesseract.doOCR(binFile);
                    processedPage.setPageText(output);
                    processedPage.setPageState(ProcessedPage.PageState.OCR);

                    //The OCR process may produce some gibberish output.  A threshold is used
                    //to deduce a point at which a different tactic is needed to extract information from the page.
                    //For instance, the page may consist of a map.  In that case, some image manipulation can
                    //help with extracting as much knowledge as possible from the page.
                    double ocrPercentGibberish = detector.getPercentGibberish(output);
                    logger.info("OCR processing for file " + binFile.getName() + " page " + i + " percent gibberish: " + ocrPercentGibberish);
                    if (ocrPercentGibberish <= ocrGibberishThreshold) {
                        logger.info("OCR processing for file " + binFile.getName() + " page " + i + " complete!");
                        //At this point it is very likely that the current page comprises scanned text.
                        //No further processing is needed.
                        return new AsyncResult<>(processedPage);
                    } else {
                        logger.info("OCR processing for file " + binFile.getName() + " page " + i + " finished, but output contains too much gibberish text.");
                        //The page likely contains a map or an engineering schematic.  It may be possible to extract
                        //more information from the page by piecewise analysis.
                        logger.info("Begin processing for file " + tiffFile.getName() + " page " + i + " as a map or engineering schematic.");
                        output = doOCROnMap(tiffFile, i);
                        double outputGibberish = detector.getPercentGibberish(output);

                        logger.info("Map OCR processing for file " + binFile.getName() + " for page " + i + " percent gibberish: " + outputGibberish);
                        if (outputGibberish > ocrGibberishThreshold) {
                            //As a final attempt, remove lines from the image and try extraction again.
                            logger.info("Map OCR processing for file " + binFile.getName() + " for page " + i + " percent gibberish exceeds maximum allowable amount.  Attempt line removal to declutter image, and try OCR again.");
                            ImageTools.removeLinesAndBinarize(tiffFile);
                            String noLinesOutput = doOCROnMap(tiffFile, i);
                            double noLinesGibberish = detector.getPercentGibberish(noLinesOutput);

                            logger.info("Decluttered Map OCR processing for file " + tiffFile.getName() + " for page " + i + " percent gibberish: " + outputGibberish);
                            output = outputGibberish < noLinesGibberish ? output : noLinesOutput;
                        }

                        logger.info("Extract nouns from OCR output for file " + tiffFile.getName() + " for page " + i);
                        output = extractNouns(output);
                        processedPage.setPageText(output);
                        processedPage.setPageType(ProcessedPage.PageType.Schematic);
                        return new AsyncResult<>(processedPage);
                    }
                } catch (TesseractException e) {
                    logger.error(e.getMessage(), e);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                } finally {
                    //make sure the tiff file is deleted
                    if (binFile != null) {
                        logger.info("After processing page " + i + ", now deleting file: " + binFile.getName());
                        binFile.delete();
                    }
                    logger.info("After processing page " + i + ", now deleting file: " + tiffFile.getName());
                    tiffFile.delete();
//                    logger.info("After processing page " + i + ", now deleting file: " + pageFile.getName());
//                    pageFile.delete();
                }
            } else {
                logger.info("PDF processing for file " + pdfFile.getName() + " for page " + i + " complete!");
                return new AsyncResult<>(processedPage);
            }
        } catch (IOException e) {
            logger.info("An error has occurred affecting processing of file " + pdfFile.getName() + " for page " + i);
            logger.error(e.getMessage(), e);
        }
        logger.info("Processing for file " + pdfFile.getName() + " for page " + i + " FAILED!");
        processedPage.setPageState(ProcessedPage.PageState.Error);
        return new AsyncResult<>(processedPage);
    }

    private String extractNouns(String input) {
        //Use POS Tagging to extract nouns from the OCR output
        List<List<TaggedWord>> pos = NLPTools.tagText(input);
        StringBuilder posOutput = new StringBuilder();
        for (List<TaggedWord> taggedWords : pos) {
            boolean newLine = false;
            for (TaggedWord taggedWord : taggedWords) {
                String tag = taggedWord.tag();
                if (tag.compareTo("NNP") == 0 || tag.compareTo("NN") == 0) {
                    posOutput.append(taggedWord.word());
                    posOutput.append(" ");
                    newLine = true;
                }
            }
            if (newLine) {
                posOutput.append(System.lineSeparator());
            }
        }

        return posOutput.toString();
    }

    private String doOCROnMap(File tiffFile, int page) {
        ConcurrentHashMap<Rectangle, String> allOutput = new ConcurrentHashMap<>();

        //rotate 90 degrees clockwise and extract data
//		for (int angle = 0; angle <= 270; angle += 90) {
//			if (angle > 0) {
//				rotateImage(tiffFile);
//			}

        Pix pix = leptInstance.pixRead(tiffFile.getPath());
        int width = leptInstance.pixGetWidth(pix);
        int height = leptInstance.pixGetHeight(pix);
        LeptUtils.disposePix(pix);
        int minWidth = width / 8;
        int minHeight = height / 8;

        Dimension size = new Dimension(width, height);
        Point ul = new Point(0, 0);

        Rectangle rect = new Rectangle(ul, size);
        List<Future<Boolean>> tasks = new ArrayList<>();

        //logger.info("Perform OCR for file " + tiffFile.getName() + " on page " + page + " using rectangles");
        //doOCRByRectangles(tasks, tiffFile, page, rect, minWidth, minHeight, allOutput);

        Rectangle convRect = new Rectangle(ul, new Dimension(width / 6, height / 6));
        int widthStep = width / 12;
        int heightStep = height / 12;

        logger.info("Perform OCR for file " + tiffFile.getName() + " on page " + page + " using box convolution");
        doOCRByConvolution(tasks, tiffFile, page, convRect, width, height, widthStep, heightStep, allOutput);

        //Track all OCR threads
        while(tasks.stream().anyMatch(p -> !p.isDone())) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            }
        }
//		}
        //String output = detector.removeGibberishLines(String.join(System.lineSeparator(), allOutput));

        //output = postProcessForOCR(output);

        return "";
    }

    private String postProcessForOCR(String input) {
        NamedEntityRecognizer recognizer = new NamedEntityRecognizer(null);
        String[] sentences = recognizer.detectSentences(input);

        Map<String, Integer> termFrequency = new TreeMap<>();
        for (String sentence : sentences) {
            String[] tokens = NLPTools.detectTokens(sentence);
            for (String token : tokens) {
                String norm = token.toLowerCase();
                if (termFrequency.containsKey(norm)) {
                    Integer freq = termFrequency.get(norm);
                    termFrequency.replace(norm, ++freq);
                } else {
                    termFrequency.put(norm, 1);
                }
            }
        }

        TreeSet<String> highFrequency = termFrequency.entrySet().stream()
                .filter(p -> p.getValue() > 1)
                .map(p -> p.getKey())
                .collect(Collectors.toCollection(TreeSet::new));

        StringBuilder parsed = new StringBuilder();

        for (String sentence : sentences) {
            String[] tokens = NLPTools.detectTokens(sentence);
            for (String token : tokens) {
                String norm = token.toLowerCase();
                if (highFrequency.contains(norm)) {
                    parsed.append(token);
                    parsed.append(" ");
                }
            }
        }

        //remove single and double lowercase character combinations that may be cluttering the text
        String output = parsed.toString().replaceAll("\\b[a-zA-Z]{1,2}\\b", "");

        //final sterilization pass to filter out noise
        sentences = recognizer.detectSentences(output);

        output = String.join("\r\n", sentences);

        return output;
    }

//    private void doOCRByRectangles(List<Future<Boolean>> tasks, File tiffFile, int page, Rectangle rect, int minWidth, int minHeight, List<String> rectOutput) {
//        Dimension size = rect.getSize();
//        Dimension halfSize = new Dimension(size.width / 2, size.height / 2);
//        if (halfSize.width >= minWidth && halfSize.height >= minHeight) {
//            Point ul = rect.getLocation();
//            Point ll = new Point(ul.x, ul.y + halfSize.height);
//            Point ur = new Point(ul.x + halfSize.width, ul.y);
//            Point lr = new Point(ul.x + halfSize.width, ul.y + halfSize.height);
//
//            Rectangle rul = new Rectangle(ul, halfSize);
//            Rectangle rll = new Rectangle(ll, halfSize);
//            Rectangle rur = new Rectangle(ur, halfSize);
//            Rectangle rlr = new Rectangle(lr, halfSize);
//
//            logger.info("queue rectangle OCR processing task for file " + tiffFile.getName() + " for page " + page + ", rectangle: (" + rect.getX() + ", " + rect.getY() + ") with size: " + rect.width + "x" + rect.height + " pixels");
//            Future<Boolean> result = tesseractOCRService.process(tiffFile, page, rect, rectOutput);
//            tasks.add(result);
//
//            doOCRByRectangles(tasks, tiffFile, page, rul, minWidth, minHeight, rectOutput);
//            doOCRByRectangles(tasks, tiffFile, page, rll, minWidth, minHeight, rectOutput);
//            doOCRByRectangles(tasks, tiffFile, page, rur, minWidth, minHeight, rectOutput);
//            doOCRByRectangles(tasks, tiffFile, page, rlr, minWidth, minHeight, rectOutput);
//        }
//    }

    private void doOCRByConvolution(List<Future<Boolean>> tasks, File tiffFile, int page, Rectangle rect, int imgWidth, int imgHeight, int widthStep, int heightStep, ConcurrentHashMap<Rectangle, String> convOutput) {
        while(rect.y <= (imgHeight - rect.height)) {
            while(rect.x <= (imgWidth - rect.width)) {
                Rectangle convRect = new Rectangle(rect.getLocation(), rect.getSize());
                logger.info("queue convolution OCR processing task for file " + tiffFile.getName() + " for page " + page + ", rectangle: (" + rect.getX() + ", " + rect.getY() + ") with size: " + rect.width + "x" + rect.height + " pixels");
                Future<Boolean> result = tesseractOCRService.process(tiffFile, page, convRect, convOutput);
                tasks.add(result);
                rect.x += widthStep;
            }
            rect.x = 0;
            rect.y += heightStep;
        }
    }

    private double getAsciiPercentage(String docText) {
        return (double) CharMatcher.ascii().countIn(docText) / (double)docText.length();
    }
}
