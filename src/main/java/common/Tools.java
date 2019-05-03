package common;

import java.awt.*;
import java.io.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.graph.*;
import edu.stanford.nlp.ling.CoreLabel;
import net.sourceforge.lept4j.*;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import nlp.NLPTools;
import nlp.gibberish.GibberishDetector;
import org.apache.commons.collections.bidimap.TreeBidiMap;
import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.core.io.ClassPathResource;
import textextraction.*;
import webapp.components.ApplicationContextProvider;
import webapp.models.JsonResponse;
import webapp.services.PDFProcessingService;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

public class Tools {

	private static Properties _properties;
	private static List<Exception> _exceptions;
	final static Logger logger = LogManager.getLogger(Tools.class);
	
	public static Properties getProperties() {
		if (_properties == null) {
			_properties = new Properties();
			try {
				_properties.load(Tools.class.getClassLoader().getResourceAsStream("application.properties"));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return _properties;
	}

	public static List<Exception> getExceptions() {
		if (_exceptions == null) {
			_exceptions = new ArrayList<>();
		}
		return _exceptions;
	}
	
	public static String getProperty(String property) {
		return getProperties().getProperty(property);
	}

	public static String getResource(String name) {
		ClassPathResource resource = new ClassPathResource(name);
		String fileString = null;
		try {
			fileString = Files.toString(resource.getFile(), Charsets.UTF_8);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return fileString;
	}

	public static String GetFileString(String filePath) {
		String fileString = null;
		try {
			fileString = Files.toString(new File(filePath), Charsets.UTF_8);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return fileString;
	}
	
	public static String GetFileString(String filePath, String encoding) {
		String fileString = null;
		try {
			return FileUtils.readFileToString(new File(filePath), encoding);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return fileString;
	}

	public static File WriteFileToDisk(String path, InputStream stream) throws IOException {
		File file = new File(path);
		FileOutputStream fileOutputStream = new FileOutputStream(file);
		org.apache.commons.io.IOUtils.copy(stream, fileOutputStream);
		stream.close();
		fileOutputStream.close();
		if (file.exists()) {
			logger.info("file written to disk");
		} else {
			logger.info("failure writing file to disk");
		}
		return file;
	}
	
	public static String GetQueryString(Object obj) {
    	ObjectMapper mapper = new ObjectMapper();
    	Map<?, ?> params = mapper.convertValue(obj,  Map.class);

    	String queryString = params.entrySet().stream().map(p -> p.getKey().toString() + "=" + p.getValue().toString())
	    	.reduce((p1, p2) -> p1 + "&" + p2)
	    	.map(s -> "?" + s)
	    	.orElse("");
	
    	return queryString;
    }
	
	public static String getFormattedDateTimeString(Instant dateTime) {
		return dateTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";
	}

	//Private "Helper" Methods
	public static JsonResponse formJsonResponse(Object data) {
		JsonResponse response = new JsonResponse();
		response.setData(data);
		response.setExceptions(getExceptions());
		getExceptions().clear();

		return response;
	}

	public static JsonResponse formJsonResponse(Object data, String timeStamp) {
		JsonResponse response = new JsonResponse(timeStamp);
		response.setData(data);
		response.setExceptions(getExceptions());
		getExceptions().clear();

		return response;
	}

	public static int[] argsort(final double[] a) {
		return argsort(a, true);
	}

	public static int[] argsort(final double[] a, final boolean ascending) {
		Integer[] indexes = new Integer[a.length];
		for (int i = 0; i < indexes.length; i++) {
			indexes[i] = i;
		}
		Arrays.sort(indexes, new Comparator<Integer>() {
			@Override
			public int compare(final Integer i1, final Integer i2) {
				return (ascending ? 1 : -1) * Double.compare(a[i1], a[i2]);
			}
		});
		return asArray(indexes);
	}

	public static <T extends Number> int[] asArray(final T... a) {
		int[] b = new int[a.length];
		for (int i = 0; i < b.length; i++) {
			b[i] = a[i].intValue();
		}
		return b;
	}

	public static final String UTF8_BOM = "\uFEFF";

	public static String removeUTF8BOM(String s) {
		if (s.startsWith(UTF8_BOM)) {
			s = s.substring(1);
		}
		return s;
	}

	public static String removeAllNumbers(String document) {
		document = document.replaceAll("\\d+", "");

		return document;
	}

	public static String removeSpecialCharacters(String document) {
		document = document.replaceAll("[$-,/:-?{-~!\"^_`\\[\\]+]", "");
		document = document.replace("-", " ");

		return document;
	}

	public static String removeFilenameSpecialCharacters(String filename) {
		filename = filename.replaceAll("[\\\\/:*?\"<>|]", "");
		return filename;
	}

	public static boolean numericRangeCompare(double num1, double num2, double rng) {
		return ((num1 - rng) < num2) && (num2 < (num1 + rng));
	}

	public static String escapeRegex(String regex) {
		regex = regex.replace("(", "\\(");
		regex = regex.replace("/", "\\/");
		regex = regex.replace("{", "\\{");
		regex = regex.replace("[", "\\[");
		regex = regex.replace("^", "\\^");
		regex = regex.replace("-", "\\-");
		regex = regex.replace("&", "\\&");
		regex = regex.replace("$", "\\$");
		regex = regex.replace("?", "\\?");
		regex = regex.replace("*", "\\*");
		regex = regex.replace("+", "\\+");
		regex = regex.replace(",", "\\,");
		regex = regex.replace(":", "\\:");
		regex = regex.replace("=", "\\=");
		regex = regex.replace("!", "\\!");
		regex = regex.replace("<", "\\<");
		regex = regex.replace(">", "\\>");
		regex = regex.replace("|", "\\|");
		regex = regex.replace("\\", "\\\\");
		return regex;
	}

	@FunctionalInterface
	public interface CheckedConsumer<T> {
		void apply(T t) throws Exception;
	}

	@FunctionalInterface
	public interface CheckedBiConsumer<T, U> {
		void apply(T t, U u) throws Exception;
	}

	@FunctionalInterface
	public interface CheckedTriConsumer<V, T, U> {
		void apply(V v, T t, U u) throws Exception;
	}

	public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
		Set<Object> seen = ConcurrentHashMap.newKeySet();
		return t -> seen.add(keyExtractor.apply(t));
	}

	public static ProcessedDocument extractPDFText(File pdfFile) {
		PDFProcessingService pdfProcessingService = ApplicationContextProvider.getApplicationContext().getBean(PDFProcessingService.class);
		//String temporaryFileRepo = Tools.getProperty("mongodb.temporaryFileRepo");
		StringBuilder parsed = new StringBuilder();
		//final double pdfGibberishThreshold = 0.75; //set this threshold very high to avoid using OCR whenever possible
		//final double ocrGibberishThreshold = 0.05; //set this threshold low to encourage additional image processing when using OCR
		ProcessedDocument doc = new ProcessedDocument();
		try {
			logger.info("Begin PDF text extraction for file: " + pdfFile.getName());
			RandomAccessFile randomAccessFile = new RandomAccessFile(pdfFile, "r");
			PDFParser parser = new PDFParser(randomAccessFile);
			parser.parse();
			COSDocument cosDoc = parser.getDocument();
			PDDocument pdDoc = new PDDocument(cosDoc);
			int pageCount = pdDoc.getNumberOfPages();
			logger.info("PDF contains " + pageCount + " page(s).");

			Map<Integer, Future<ProcessedPage>> pdfTasks = new HashMap<>();

			for (int i = 1; i <= pageCount; i++) {
				logger.info("Queueing Page " + i + " for processing");
				Future<ProcessedPage> pdfTask = pdfProcessingService.process(pdfFile, pdDoc, i);
				pdfTasks.put(i, pdfTask);
			}

			//wait until all pdf processing tasks are completed
			while(pdfTasks.entrySet().stream().anyMatch(p -> !p.getValue().isDone())) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					logger.error(e.getMessage(), e);
				}
			}

			for (int i = 1; i <= pageCount; i++) {
				try {
					ProcessedPage processedPage = pdfTasks.get(i).get();
					if (processedPage.getPageState() != ProcessedPage.PageState.Error) {
						if (processedPage.getPageType() == ProcessedPage.PageType.PlainText) {
							parsed.append(processedPage.getPageText());
						} else if (processedPage.getPageType() == ProcessedPage.PageType.Schematic) {
							parsed.append("PAGE " + i + " IS A SCHEMATIC" + System.lineSeparator());
							//pass the schematic page off to an alternate processing path for storage separate to the main document
							doc.getSchematics().add(processedPage);
						}
					} else {
						parsed.append("ERROR PROCESSING PAGE " + i);
					}
				} catch (Exception e) {
					continue;
				}
			}

			logger.info("PDF data extraction complete for file: " + pdfFile.getName());

			randomAccessFile.close();
			pdDoc.close();
			cosDoc.close();
		} catch (IOException  e) {
			logger.error(e.getMessage(), e);
		}

		//clean text to resolve broken hyphenated words
		String parsedText = parsed.toString();
		parsedText = parsedText.replaceAll("(?<=[a-z])-\\s(?=[a-z])", "");
		doc.setExtractedText(parsedText);

		return doc;
	}

	public static <T> T loadXML(String xmlPath, Class<T> type) {
		try {
			ClassPathResource resource = new ClassPathResource(xmlPath);
			JAXBContext jaxbContext = JAXBContext.newInstance(type);
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			T obj = (T) unmarshaller.unmarshal(resource.getInputStream());
			return obj;
		} catch (JAXBException | IOException e) {
			return null;
		}
	}

//	public static void main(String[] args) {
//		Tesseract tesseract = new Tesseract();
//		String tessdata = Tools.getProperty("tess4j.path");
//		tesseract.setDatapath(tessdata);
//
//		File tiffFile = new File("E:\\LeptonicaTesting\\test_map_1.tif");
//		Pix pix = ImageTools.loadImage(tiffFile);
//		Pix pix2gray = ImageTools.convertImageToGrayscale(pix);
//		Pix pix2bin = ImageTools.binarizeImage(pix2gray);
//		Pix pix2rank = Leptonica.INSTANCE.pixBlockrank(pix2bin, null, 1, 1, 0.95f);
//		File saved = new File("E:\\LeptonicaTesting\\test_map_1_bin.tif");
//		ImageTools.saveImage(saved.getPath(), pix2rank);
//		ImageTools.disposePixs(pix, pix2gray, pix2bin, pix2rank);
//
//		try {
//			String output = tesseract.doOCR(saved);
//			System.out.println(output);
//		} catch (TesseractException e) {
//			e.printStackTrace();
//		}
//	}

	public static void main(String[] args) {
		TextExtractionProcessManager mgr = new TextExtractionProcessManager();
		File tiffFile = new File("E:\\LeptonicaTesting\\test_map_1.tif");

		Pix pix = ImageTools.loadImage(tiffFile);
		int width = Leptonica.INSTANCE.pixGetWidth(pix);
		int height = Leptonica.INSTANCE.pixGetHeight(pix);

		Dimension size = new Dimension(width / 6, height / 6);
		Point ul = new Point(0, 0);
		Rectangle rect = new Rectangle(ul, size);

		Map<Rectangle, Map<String, TextExtractionTask>> extractions = new HashMap<>();

		float baseRank = 0.1f;
		int rotStep = 360; //degrees
		int widthStep = width / 12;
		int heightStep = height / 12;
		while(rect.y <= (height - rect.height)) {
			while(rect.x <= (width - rect.width)) {
				int convRectWidth = (int)size.getWidth();
				if (rect.x <= (width - (convRectWidth + 1))) {
					++convRectWidth;
				}
				int convRectHeight = (int)size.getHeight();
				if (rect.y <= (height - (convRectHeight + 1))) {
					++convRectHeight;
				}
				Dimension convRectSize = new Dimension(convRectWidth, convRectHeight);
				Rectangle convRect = new Rectangle(rect.getLocation(), convRectSize);
				extractions.put(convRect, new HashMap<>());

				Pix pix2 = ImageTools.cropImage(pix, convRect);
				Pix pix2gray = ImageTools.convertImageToGrayscale(pix2);
				Pix pix2bin = ImageTools.binarizeImage(pix2gray);

				float rank = baseRank;
				while (rank <= 1.0f) {
					Pix pix2rank = Leptonica.INSTANCE.pixBlockrank(pix2bin, null, 1, 1, rank);
					for (int rot = 0; rot <= 345; rot += rotStep) {
						TextExtractionTask extraction = new TextExtractionTask(mgr, convRect, rank, rot, tiffFile, pix2rank);
						extractions.get(convRect).put(String.format("%.2f", rank), extraction);
						extraction.enqueue();
					}
					ImageTools.disposePixs(pix2rank);
					rank += 0.1f;
				}

				ImageTools.disposePixs(pix2, pix2gray, pix2bin);
				rect.x += widthStep;
			}
			rect.x = 0;
			rect.y += heightStep;
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
			MutableValueGraph<String, Double> rankGraph = ValueGraphBuilder.directed().build();
			rectRanks.put(convRect, rankGraph);
			Map<String, TextExtractionTask> textExtractions = extractions.get(convRect);

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

//			if (!rank13Copy && !rank23Copy && !rank14Copy && !rank24Copy) {
//				for (RectangleTokenAtRank token3: textTokens.get(text3)) {
//					List<RectangleTokenAtRank> tokensAtRank4NotYetMatched = getUnmatchedTokens(textTokens, text4);
//					resolveTokenSimilarity(tokensAtRank4NotYetMatched, tokenGraph, token3);
//				}
//			}

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
		rect = new Rectangle(ul, size);
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
					rect.x += widthStep;
				}
			}
			if (rowAppended) {
				bldr.append(System.lineSeparator());
				rowAppended = false;
			}
			rect.x = 0;
			rect.y += heightStep;
		}

		ImageTools.disposePixs(pix);
	}

	private static String mergeConnectedRegionText(MutableValueGraph<RectangularRegion, RegionConnection> connectedRegionGraph, RectangularRegion region, RectangularRegion otherRegion, String separator) {
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

	private static class VerticalComparator implements Comparator<RectangularRegion> {
		@Override
		public int compare(RectangularRegion r1, RectangularRegion r2) {
			return Double.compare(r1.getRectangle().getY(), r2.getRectangle().getY());
		}
	}

	private static class HorizontalComparator implements Comparator<RectangularRegion> {
		@Override
		public int compare(RectangularRegion r1, RectangularRegion r2) {
			return Double.compare(r1.getRectangle().getX(), r2.getRectangle().getX());
		}
	}

	private static List<RectangleTokenAtRank> getUnmatchedTokens(Map<String, List<RectangleTokenAtRank>> textTokens, String text) {
		List<RectangleTokenAtRank> unmatchedTokens = textTokens.get(text).stream()
				.filter(p -> !p.isMatched())
				.collect(Collectors.toList());

		return unmatchedTokens;
	}

	private static void resolveTokenSimilarity(List<RectangleTokenAtRank> rectangleTokensAtRank, MutableValueGraph<RectangleTokenAtRank, Double> tokenGraph, RectangleTokenAtRank tokenOther) {
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

	private static void populateRectangleTokensAtRank(String rank, String text, Map<String, List<RectangleTokenAtRank>> textTokens) {
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

	private static void retainBestToken(EndpointPair<RectangleTokenAtRank> similarPair) {
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

	private static void retainBasedOnRank(RectangleTokenAtRank token1, RectangleTokenAtRank token2) {
		Float rank1 = Float.parseFloat(token1.getRank());
		Float rank2 = Float.parseFloat(token2.getRank());
		if (rank1 < rank2) {
			token1.setRetain(true);
		} else {
			token2.setRetain(true);
		}
	}

}
