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
import textextraction.TextExtractionCandidate;
import textextraction.TextExtractionProcessManager;
import textextraction.TextExtractionTask;

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
    private static TextExtractionProcessManager textExtractionProcessManager = new TextExtractionProcessManager();

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
        final double ocrGibberishThreshold = 0.1; //set this threshold low to encourage additional image processing when using OCR
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

    private class OCRProperties {
        private float baseRank = 0.1f;
        private float rankStep;
        private int rotStep;
        private int width;
        private int height;
        private int div;

        public OCRProperties(float rankStep, int rotStep, int width, int height, int div) {
            this.rankStep = rankStep;
            this.rotStep = rotStep;
            this.width = width;
            this.height = height;
            this.div = div;
        }

        public Rectangle getExtractionRectangle() {
            Dimension size = new Dimension(width / div, height / div);
            Point ul = new Point(0, 0);
            Rectangle rect = new Rectangle(ul, size);

            return rect;
        }

        public float getBaseRank() {
            return baseRank;
        }

        public float getRankStep() {
            return rankStep;
        }

        public int getRotStep() {
            return rotStep;
        }

        public int getWidthStep() {
            return width / (div * 2);
        }

        public int getHeightStep() {
            return height / (div * 2);
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getDiv() {
            return div;
        }
    }

    private String doOCROnMap(File tiffFile, int page) {
        Pix pix = ImageTools.loadImage(tiffFile);
        int width = Leptonica.INSTANCE.pixGetWidth(pix);
        int height = Leptonica.INSTANCE.pixGetHeight(pix);

        OCRProperties props = new OCRProperties(0.1f, 360, width, height, 6);

        Rectangle rect = props.getExtractionRectangle();

        List<TextExtractionTask> extractions = new ArrayList<>();

        while(rect.y <= (props.getHeight() - rect.height)) {
            while(rect.x <= (props.getWidth() - rect.width)) {
                Rectangle convRect = new Rectangle(rect.getLocation(), rect.getSize());

                Pix pix2 = ImageTools.cropImage(pix, convRect);
                Pix pix2gray = ImageTools.convertImageToGrayscale(pix2);
                Pix pix2bin = ImageTools.binarizeImage(pix2gray);

                float rank = props.getBaseRank();
                while (rank <= 1.0f) {
                    Pix pix2rank = Leptonica.INSTANCE.pixBlockrank(pix2bin, null, 2, 2, rank);
                    for (int rot = 0; rot <= 359; rot += props.getRotStep()) {
                        TextExtractionTask extraction = new TextExtractionTask(textExtractionProcessManager, convRect, rank, rot, tiffFile, pix2rank);
                        logger.info("Begin OCR processing for file " + extraction.getImagePath());
                        extractions.add(extraction);
                        extraction.enqueue();
                    }
                    ImageTools.disposePixs(pix2rank);
                    rank += props.getRankStep();
                }

                ImageTools.disposePixs(pix2, pix2gray, pix2bin);
                rect.x += props.getWidthStep();
            }
            rect.x = 0;
            rect.y += props.getHeightStep();
        }

        while (extractions.stream().anyMatch(p -> p.getMyThread().isAlive())) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        List<TextExtractionCandidate> candidates = getExtractionCandidatesByRank(props, extractions);
        List<List<String>> outputLines = reduceToOutputLines(props, candidates);
        List<String> finalOutput = produceFinalOutput(outputLines);

        String reduced = finalOutput.stream().reduce((c, n) -> c + System.lineSeparator() + n).orElse("");

        return reduced;
    }

    private List<TextExtractionCandidate> getExtractionCandidatesByRank(OCRProperties props, List<TextExtractionTask> extractions) {
        Rectangle rect = props.getExtractionRectangle();
        List<TextExtractionCandidate> candidates = new ArrayList<>();
        while(rect.y <= (props.getHeight() - rect.height)) {
            while(rect.x <= (props.getWidth() - rect.width)) {
                Point point = new Point(rect.x, rect.y);
                List<TextExtractionTask> containing = extractions.stream()
                        .filter(p -> p.getRect().contains(point))
                        .collect(Collectors.toList());

                for (int rot = 0; rot <= 345; rot += props.getRotStep()) {
                    //look across all ranks for a given rotation
                    final int currRot = rot;
                    List<TextExtractionTask> sameRot = containing.stream()
                            .filter(p -> p.getRot() == currRot)
                            .collect(Collectors.toList());

                    List<String> rotStrings = new ArrayList<>();
                    for (TextExtractionTask extraction : sameRot) {
                        String valid = extraction.getValidLine();
                        rotStrings.add(valid);
                    }

                    int totalStrings = rotStrings.size();
                    int numContains = 0;
                    for (int i = 0; i < totalStrings; i++) {
                        String str1 = rotStrings.get(i);
                        if (Strings.isNullOrEmpty(str1)) {
                            continue;
                        }
                        for (int j = i + 1; j < totalStrings; j++) {
                            String str2 = rotStrings.get(j);
                            if (Strings.isNullOrEmpty(str2)) {
                                continue;
                            }
                            if (str1.contains(str2)) {
                                ++numContains;
                            }
                        }
                    }

                    double percentContained = (double)numContains / (double)totalStrings;
                    if (percentContained > 0.33) { //if at least 33% of the ranks have similar strings this is a good indication that a valid string exists
                        Rectangle candidateRect = new Rectangle(rect);
                        TextExtractionTask bestCandidate = sameRot.stream().max(new Comparator<TextExtractionTask>() {
                            @Override
                            public int compare(TextExtractionTask textExtractionTask, TextExtractionTask t1) {
                                return Double.compare(textExtractionTask.getPercentValid(), t1.getPercentValid());
                            }
                        }).orElse(null);
                        if (bestCandidate != null && !detector.isLineGibberish(bestCandidate.getValidLine())) {
                            TextExtractionCandidate candidate = new TextExtractionCandidate(candidateRect, rot, bestCandidate);
                            candidates.add(candidate);
                        }
                    }
                }

                rect.x += props.getWidthStep();
            }
            rect.x = 0;
            rect.y += props.getHeightStep();
        }

        return candidates;
    }

    private List<List<String>> reduceToOutputLines(OCRProperties props, List<TextExtractionCandidate> candidates) {
        Rectangle rect = props.getExtractionRectangle();
        List<List<String>> outputLines = new ArrayList<>();
        while(rect.y <= (props.getHeight() - rect.height)) {
            List<String> horizontal = new ArrayList<>();
            while(rect.x <= (props.getWidth() - rect.width)) {
                Point point = new Point(rect.x, rect.y);
                List<TextExtractionCandidate> containing = candidates.stream()
                        .filter(p -> p.getRect().contains(point))
                        .collect(Collectors.toList());

                for (int rot = 0; rot <= 345; rot += props.getRotStep()) {
                    //look across all ranks for a given rotation
                    final int currRot = rot;
                    List<TextExtractionCandidate> sameRot = containing.stream()
                            .filter(p -> p.getRot() == currRot)
                            .collect(Collectors.toList());

                    TextExtractionCandidate bestOverall = sameRot.stream().max(new Comparator<TextExtractionCandidate>() {
                        @Override
                        public int compare(TextExtractionCandidate textExtractionCandidate, TextExtractionCandidate t1) {
                            return Double.compare(textExtractionCandidate.getCandidate().getPercentValid(), t1.getCandidate().getPercentValid());
                        }
                    }).orElse(null);

                    if (bestOverall != null) {
                        String entry = bestOverall.getCandidate().getValidLine();
                        if (!horizontal.contains(entry)) {
                            horizontal.add(entry);
                        }
                    }
                }

                rect.x += props.getWidthStep();
            }
            outputLines.add(horizontal);
            rect.x = 0;
            rect.y += props.getHeightStep();
        }

        return outputLines;
    }

    private List<String> produceFinalOutput(List<List<String>> outputLines) {
        List<String> finalOutput = new ArrayList<>();
        for (int i = 0; i < outputLines.size(); i++) {
            List<String> currentLine = outputLines.get(i);
            if (i < outputLines.size() - 1) {
                List<String> nextLine = outputLines.get(i + 1);
                for (int j = 0; j < currentLine.size(); j++) {
                    String entry = currentLine.get(j);
                    double sim = 0d;
                    for (int m = 0; m < nextLine.size(); m++) {
                        String other = nextLine.get(m);
                        double currSim = NLPTools.similarity(entry, other);
                        if (currSim > 0.7) {
                            sim = 1000;
                            break;
                        }
                        sim += currSim;
                    }
                    double avgSim = sim / (double)nextLine.size();
                    if (avgSim > 0.5 && !finalOutput.contains(entry)) {
                        finalOutput.add(entry);
                    }
                }
            }
            if (i > 0) {
                List<String> prevLine = outputLines.get(i - 1);
                for (int j = 0; j < currentLine.size(); j++) {
                    String entry = currentLine.get(j);
                    double sim = 0d;
                    for (int k = 0; k < prevLine.size(); k++) {
                        String other = prevLine.get(k);
                        double currSim = NLPTools.similarity(entry, other);
                        if (currSim > 0.7) {
                            sim = 1000;
                            break;
                        }
                        sim += currSim;
                    }
                    double avgSim = sim / (double)prevLine.size();
                    if (avgSim > 0.5 && !finalOutput.contains(entry)) {
                        finalOutput.add(entry);
                    }
                }
            }
        }

        return finalOutput;
    }

    private double getAsciiPercentage(String docText) {
        return (double) CharMatcher.ascii().countIn(docText) / (double)docText.length();
    }
}
