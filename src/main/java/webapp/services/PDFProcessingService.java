package webapp.services;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import common.*;
import edu.stanford.nlp.ling.CoreLabel;
import textextraction.ProcessedPage;
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

        Map<Rectangle, Map<String, TextExtractionTask>> extractions = new HashMap<>();

        while(rect.y <= (props.getHeight() - rect.height)) {
            while(rect.x <= (props.getWidth() - rect.width)) {
                int convRectWidth = (int)rect.getSize().getWidth();
                if (rect.x <= (width - (convRectWidth + 1))) {
                    ++convRectWidth;
                }
                int convRectHeight = (int)rect.getSize().getHeight();
                if (rect.y <= (height - (convRectHeight + 1))) {
                    ++convRectHeight;
                }
                Dimension convRectSize = new Dimension(convRectWidth, convRectHeight);
                Rectangle convRect = new Rectangle(rect.getLocation(), convRectSize);
                extractions.put(convRect, new HashMap<>());

                Pix pix2 = ImageTools.cropImage(pix, convRect);
                Pix pix2gray = ImageTools.convertImageToGrayscale(pix2);
                Pix pix2bin = ImageTools.binarizeImage(pix2gray);

                float rank = props.getBaseRank();
                while (rank <= 1.0f) {
                    Pix pix2rank = Leptonica.INSTANCE.pixBlockrank(pix2bin, null, 1, 1, rank);
                    for (int rot = 0; rot <= 359; rot += props.getRotStep()) {
                        TextExtractionTask extraction = new TextExtractionTask(textExtractionProcessManager, convRect, rank, rot, tiffFile, pix2rank);
                        logger.info("Begin OCR processing for file " + extraction.getImagePath());
                        extractions.get(convRect).put(String.format("%.2f", rank), extraction);
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

        List<TextExtractionTask> allTasks = new ArrayList<>();
        extractions.values().stream().forEach(p -> p.values().stream().forEach(allTasks::add));
        while (allTasks.stream().anyMatch(p -> p.getMyThread().isAlive())) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        Map<Rectangle, MutableValueGraph<String, Double>> rectRanks = new HashMap<>();
        Map<Rectangle, MutableValueGraph<RectangleTokenAtRank, Double>> tokenGraphs = new HashMap<>();
        for (Rectangle convRect : extractions.keySet()) {
            Map<String, TextExtractionTask> textExtractions = extractions.get(convRect);
            MutableValueGraph<String, Double> rankGraph = getRectangleTextRankGraph(textExtractions);
            rectRanks.put(convRect, rankGraph);

            List<Double> edges = rankGraph.edges().stream()
                    .map(p -> rankGraph.edgeValueOrDefault(p, 0.0d))
                    .collect(Collectors.toList());
            if (edges.size() < 2) { //there must be at least 3 nodes connected by two edges for analysis
                continue;
            }
            edges.sort(Double::compare);
            Double first = edges.get(edges.size() - 1);
            Double second = edges.get(edges.size() - 2);

            EndpointPair<String> firstPair = rankGraph.edges().stream()
                    .filter(p -> rankGraph.edgeValueOrDefault(p, 0.0d) == first)
                    .collect(Collectors.toList())
                    .get(0);
            EndpointPair<String> secondPair = rankGraph.edges().stream()
                    .filter(p -> rankGraph.edgeValueOrDefault(p, 0.0d) == second)
                    .collect(Collectors.toList())
                    .get(0);

            String rank1 = firstPair.source();
            String rank2 = firstPair.target();
            String rank3 = secondPair.source();
            String rank4 = secondPair.target();

            //detect if rank 1 or 2 is the same as rank 3 or 4
            boolean rank13Copy = rank1.equals(rank3);
            boolean rank14Copy = rank1.equals(rank4);
            boolean rank23Copy = rank2.equals(rank3);
            boolean rank24Copy = rank2.equals(rank4);

            Map<String, List<RectangleTokenAtRank>> textTokens = new HashMap<>();
            String text1 = textExtractions.get(rank1).getCleaned();
            String text2 = textExtractions.get(rank2).getCleaned();
            String text3 = textExtractions.get(rank3).getCleaned();
            String text4 = textExtractions.get(rank4).getCleaned();

            populateRectangleTokensAtRank(rank1, text1, textTokens);
            populateRectangleTokensAtRank(rank2, text2, textTokens);

            if (!rank13Copy && !rank23Copy) {
                populateRectangleTokensAtRank(rank3, text3, textTokens);
            }
            if (!rank14Copy && !rank24Copy) {
                populateRectangleTokensAtRank(rank4, text4, textTokens);
            }

            //resolve tokens using digraph approach
            //compare text1 tokens against text2 and against other
            MutableValueGraph<RectangleTokenAtRank, Double> tokenGraph = ValueGraphBuilder.directed().allowsSelfLoops(true).build();
            tokenGraphs.put(convRect, tokenGraph);
            for (RectangleTokenAtRank token1 : textTokens.get(text1)) {
                tokenGraph.addNode(token1);
            }
            for (RectangleTokenAtRank token2 : textTokens.get(text2)) {
                tokenGraph.addNode(token2);
                resolveTokenSimilarity(textTokens.get(text1), tokenGraph, token2);
            }

            if (!rank13Copy && !rank23Copy) {
                for (RectangleTokenAtRank token3 : textTokens.get(text3)) {
                    tokenGraph.addNode(token3);
                    List<RectangleTokenAtRank> tokensAtRank1NotYetMatched = getUnmatchedTokens(textTokens, text1);
                    resolveTokenSimilarity(tokensAtRank1NotYetMatched, tokenGraph, token3);

                    List<RectangleTokenAtRank> tokensAtRank2NotYetMatched = getUnmatchedTokens(textTokens, text2);
                    resolveTokenSimilarity(tokensAtRank2NotYetMatched, tokenGraph, token3);
                }
            }

            if (!rank14Copy && !rank24Copy) {
                for (RectangleTokenAtRank token4 : textTokens.get(text4)) {
                    tokenGraph.addNode(token4);
                    List<RectangleTokenAtRank> tokensAtRank1NotYetMatched = getUnmatchedTokens(textTokens, text1);
                    resolveTokenSimilarity(tokensAtRank1NotYetMatched, tokenGraph, token4);

                    List<RectangleTokenAtRank> tokensAtRank2NotYetMatched = getUnmatchedTokens(textTokens, text2);
                    resolveTokenSimilarity(tokensAtRank2NotYetMatched, tokenGraph, token4);
                }
            }

            //analyze the nodes that don't have a match to see which can be kept
            for (RectangleTokenAtRank node : tokenGraph.nodes()) {
                if (!node.isMatched()) {
                    Set<EndpointPair<RectangleTokenAtRank>> endpointPairs = tokenGraph.incidentEdges(node).stream()
                            .filter(p -> !p.source().isMatched() && !p.target().isMatched())
                            .collect(Collectors.toSet());
                    //get the edge with the highest value... if multiple have the same value then skip
                    List<EndpointPair<RectangleTokenAtRank>> sortedBySimilarity = endpointPairs.stream().sorted(new Comparator<EndpointPair<RectangleTokenAtRank>>() {
                        @Override
                        public int compare(EndpointPair<RectangleTokenAtRank> pair1, EndpointPair<RectangleTokenAtRank> pair2) {
                            Double edge1 = tokenGraph.edgeValue(pair1).orElse(0.0d);
                            Double edge2 = tokenGraph.edgeValue(pair2).orElse(0.0d);
                            return edge1.compareTo(edge2);
                        }
                    }).collect(Collectors.toList());

                    int numPairs = sortedBySimilarity.size();
                    if (numPairs > 1) {
                        EndpointPair<RectangleTokenAtRank> bestSimilarity = sortedBySimilarity.get(numPairs - 1);
                        EndpointPair<RectangleTokenAtRank> nextSimilarity = sortedBySimilarity.get(numPairs - 2);
                        if (tokenGraph.edgeValue(bestSimilarity) != tokenGraph.edgeValue(nextSimilarity)) {
                            retainBestToken(bestSimilarity);
                        }
                    } else if (numPairs > 0) {
                        EndpointPair<RectangleTokenAtRank> similarPair = sortedBySimilarity.get(0);
                        retainBestToken(similarPair);
                    }
                }
            }
        }

        //Rectangle text reassembly
        MutableValueGraph<RectangularRegion, RegionConnection> connectedRegionGraph = ValueGraphBuilder.directed().allowsSelfLoops(true).build();

        for (Rectangle rectangle : tokenGraphs.keySet()) {
            RectangularRegion region = new RectangularRegion(rectangle);
            connectedRegionGraph.addNode(region);
        }

        //wire up all connected rectangular regions with associated intersecting/divergent text strings
        for (RectangularRegion region : connectedRegionGraph.nodes()) {
            for (RectangularRegion otherRegion : connectedRegionGraph.nodes()) {
                if (!region.getRectangle().equals(otherRegion.getRectangle()) && region.getRectangle().intersects(otherRegion.getRectangle())) {
                    MutableValueGraph<RectangleTokenAtRank, Double> rectangleTokens = tokenGraphs.get(region.getRectangle());
                    MutableValueGraph<RectangleTokenAtRank, Double> otherRectangleTokens = tokenGraphs.get(otherRegion.getRectangle());
                    RegionConnection connection = new RegionConnection(rectangleTokens, otherRectangleTokens);
                    connectedRegionGraph.putEdgeValue(region, otherRegion, connection);
                }
            }
        }

        //initialize for identifying horizontal and vertical shared/exclusive partitions
        for (RectangularRegion region : connectedRegionGraph.nodes()) {
            Set<EndpointPair<RectangularRegion>> incidentEdges = connectedRegionGraph.incidentEdges(region);
            for (EndpointPair<RectangularRegion> edge : incidentEdges) {
                RectangularRegion otherRegion = edge.adjacentNode(region);
                //RegionConnection connection = connectedRegionGraph.edgeValue(edge).get();
                if (region.getRectangle().getY() == otherRegion.getRectangle().getY() && !region.getLeft().contains(otherRegion) && !region.getRight().contains(otherRegion)) { //horizontal pair
                    if (region.getRectangle().getX() < otherRegion.getRectangle().getX()) {
                        region.getRight().add(otherRegion);
                    } else {
                        region.getLeft().add(otherRegion);
                    }
                } else if (region.getRectangle().getX() == otherRegion.getRectangle().getX() && !region.getAbove().contains(otherRegion) && !region.getBelow().contains(otherRegion)) { //vertical pair
                    if (region.getRectangle().getY() > otherRegion.getRectangle().getY()) {
                        region.getAbove().add(otherRegion);
                    } else {
                        region.getBelow().add(otherRegion);
                    }
                }
            }
        }

        Map<Double, List<RectangularRegion>> verticalRegionList = connectedRegionGraph.nodes().stream().collect(Collectors.groupingBy(p -> p.getRectangle().getX()));
        TreeMap<Double, List<RectangularRegion>> orderedVerticalRegionList = new TreeMap<>();
        orderedVerticalRegionList.putAll(verticalRegionList);

        for (Double x : orderedVerticalRegionList.keySet()) {
            List<RectangularRegion> column = orderedVerticalRegionList.get(x);
            column.sort(new VerticalComparator());

            for (RectangularRegion region : column) {
                List<RectangularRegion> belowRegions = region.getBelow().stream().collect(Collectors.toList());
                List<RectangularRegion> leftRegions = region.getLeft().stream().collect(Collectors.toList());
                List<RectangularRegion> rightRegions = region.getRight().stream().collect(Collectors.toList());
                belowRegions.sort(new VerticalComparator());
                leftRegions.sort(new HorizontalComparator());
                rightRegions.sort(new HorizontalComparator());
                if (belowRegions.size() > 0) {
                    RectangularRegion below = belowRegions.get(0);
                    String output = mergeConnectedRegionText(connectedRegionGraph, region, below, System.lineSeparator());
                    region.setBelowText(output);
                    below.setAboveText(output);
                }
                if (rightRegions.size() > 0) {
                    RectangularRegion right = rightRegions.get(0);
                    String output = mergeConnectedRegionText(connectedRegionGraph, region, right, "\t");
                    region.setRightText(output);
                    right.setLeftText(output);
                }
                if (leftRegions.size() > 0) {
                    RectangularRegion left = leftRegions.get(0);
                    String output = mergeConnectedRegionText(connectedRegionGraph, region, left, "\t");
                    region.setLeftText(output);
                    left.setRightText(output);
                }
            }
        }

        //merge all text together moving top to bottom and left to right
        StringBuilder bldr = new StringBuilder();
        rect = props.getExtractionRectangle();
        Map<Double, List<RectangularRegion>> horizontalRegionList = connectedRegionGraph.nodes().stream().collect(Collectors.groupingBy(p -> p.getRectangle().getY()));
        TreeMap<Double, List<RectangularRegion>> orderedHorizontalRegionList = new TreeMap<>();
        orderedHorizontalRegionList.putAll(horizontalRegionList);
        boolean rowAppended = false;
        String prevBelow = "";
        String prevRight = "";
        while(rect.y <= (height - rect.height)) {
            List<RectangularRegion> row = null;
            if (orderedHorizontalRegionList.containsKey((double)rect.y)) {
                row = orderedHorizontalRegionList.get((double)rect.y);
            }
            if (row != null) {
                while (rect.x <= (width - rect.width)) {
                    for (RectangularRegion region : row) {
                        if (region.getRectangle().getX() == rect.x) {
                            String below = region.getBelowText();
                            String right = region.getRightText();
                            if (below != null && NLPTools.similarity(prevBelow, below) < 0.9) {
                                bldr.append(below);
                                bldr.append("\t");
                                rowAppended = true;
                                prevBelow = below;
                            }
                            if (right != null && NLPTools.similarity(prevRight, right) < 0.9) {
                                bldr.append(right);
                                bldr.append("\t");
                                rowAppended = true;
                                prevRight = right;
                            }
                        }
                    }
                    rect.x += props.getWidthStep();
                }
            }
            if (rowAppended) {
                bldr.append(System.lineSeparator());
                rowAppended = false;
            }
            rect.x = 0;
            rect.y += props.getHeightStep();
        }

        ImageTools.disposePixs(pix);

        return bldr.toString();
    }

    private MutableValueGraph<String, Double> getRectangleTextRankGraph(Map<String, TextExtractionTask> textExtractions) {
        MutableValueGraph<String, Double> rankGraph = ValueGraphBuilder.directed().build();
        textExtractions.keySet().stream().forEach(p -> rankGraph.addNode(p));

        //compute extracted text similarity adjacency
        String[] ranks = textExtractions.keySet().toArray(new String[textExtractions.keySet().size()]);
        for (int r1 = 0; r1 < ranks.length; r1++) {
            String rank1 = ranks[r1];
            for (int r2 = r1 + 1; r2 < ranks.length; r2++) {
                String rank2 = ranks[r2];
                String rank1Text = textExtractions.get(rank1).getCleaned();
                String rank2Text = textExtractions.get(rank2).getCleaned();
                double similarity = NLPTools.similarity(rank1Text, rank2Text);
                if (similarity > 0.5d) {
                    rankGraph.putEdgeValue(rank1, rank2, similarity);
                }
            }
        }

        return rankGraph;
    }

    private String mergeConnectedRegionText(MutableValueGraph<RectangularRegion, RegionConnection> connectedRegionGraph, RectangularRegion region, RectangularRegion otherRegion, String separator) {
        if(connectedRegionGraph.edgeValue(region, otherRegion).isPresent()) {
            RegionConnection connection = connectedRegionGraph.edgeValue(region, otherRegion).get();
            String source = connection.getSourceTokens().stream().reduce((c, n) -> c + " " + n).orElse("");
            String output;
            if (connection.getIntersection().size() > connection.getSourceDiff().size()) { //merge the two regions
                connection.getTargetTokens().removeAll(connection.getIntersection());
                String target = connection.getTargetTokens().stream().reduce((c, n) -> c + " " + n).orElse(null);
                if (target != null) {
                    output = source + separator + target;
                } else {
                    output = source;
                }
            } else { //treat the source region as standalone
                output = source;
            }
            return output;
        }
        return "";
    }

    public class VerticalComparator implements Comparator<RectangularRegion> {
        @Override
        public int compare(RectangularRegion r1, RectangularRegion r2) {
            return Double.compare(r1.getRectangle().getY(), r2.getRectangle().getY());
        }
    }

    public class HorizontalComparator implements Comparator<RectangularRegion> {
        @Override
        public int compare(RectangularRegion r1, RectangularRegion r2) {
            return Double.compare(r1.getRectangle().getX(), r2.getRectangle().getX());
        }
    }

    private List<RectangleTokenAtRank> getUnmatchedTokens(Map<String, List<RectangleTokenAtRank>> textTokens, String text) {
        List<RectangleTokenAtRank> unmatchedTokens = textTokens.get(text).stream()
                .filter(p -> !p.isMatched())
                .collect(Collectors.toList());

        return unmatchedTokens;
    }

    private void resolveTokenSimilarity(List<RectangleTokenAtRank> rectangleTokensAtRank, MutableValueGraph<RectangleTokenAtRank, Double> tokenGraph, RectangleTokenAtRank tokenOther) {
        for (RectangleTokenAtRank token : rectangleTokensAtRank) {
            double similarity = NLPTools.similarity(token.getToken(), tokenOther.getToken());
            if (similarity >= 0.75d && !token.isMatched() && !tokenOther.isMatched()) {
                tokenGraph.putEdgeValue(token, tokenOther, similarity);
                if (similarity == 1.0d) { //exact match found - no sense in continuing to add more edges
                    token.setMatched(true);
                    tokenOther.setMatched(true);
                    retainBasedOnRank(token, tokenOther);
                    break;
                }
            }
        }
    }

    private void populateRectangleTokensAtRank(String rank, String text, Map<String, List<RectangleTokenAtRank>> textTokens) {
        List<CoreLabel> tokens = NLPTools.detectTokensStanford(text);
        List<RectangleTokenAtRank> tokensAtRank = new ArrayList<>();
        RectangleTokenAtRank prev = null;
        for (int i = 0; i < tokens.size(); i++) {
            CoreLabel token = tokens.get(i);
            RectangleTokenAtRank rectangleTokenAtRank = new RectangleTokenAtRank(token.word(), rank);
            if (prev != null) {
                rectangleTokenAtRank.setPrev(prev);
            }
            tokensAtRank.add(rectangleTokenAtRank);
            prev = rectangleTokenAtRank;
        }
        textTokens.put(text, tokensAtRank);
    }

    private void retainBestToken(EndpointPair<RectangleTokenAtRank> similarPair) {
        String word1 = similarPair.source().getToken();
        String word2 = similarPair.target().getToken();
        similarPair.target().setMatched(true);
        similarPair.source().setMatched(true);
        if (SpellChecker.check58K(word1) && SpellChecker.check58K(word2)){
            retainBasedOnRank(similarPair.source(), similarPair.target());
        } else if (SpellChecker.check58K(word1)) {
            similarPair.source().setRetain(true);
        } else if (SpellChecker.check58K(word2)) {
            similarPair.target().setRetain(true);
        } else {
            retainBasedOnRank(similarPair.source(), similarPair.target());
        }
    }

    private void retainBasedOnRank(RectangleTokenAtRank token1, RectangleTokenAtRank token2) {
        Float rank1 = Float.parseFloat(token1.getRank());
        Float rank2 = Float.parseFloat(token2.getRank());
        if (rank1 < rank2) {
            token1.setRetain(true);
        } else {
            token2.setRetain(true);
        }
    }

    private double getAsciiPercentage(String docText) {
        return (double) CharMatcher.ascii().countIn(docText) / (double)docText.length();
    }
}
