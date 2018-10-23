package common;

import java.awt.*;
import java.io.*;
import java.nio.IntBuffer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.PointerByReference;
import edu.stanford.nlp.ling.TaggedWord;
import net.sourceforge.lept4j.*;
import net.sourceforge.lept4j.util.LeptUtils;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.util.PdfBoxUtilities;
import nlp.NLPTools;
import nlp.NamedEntityRecognizer;
import nlp.gibberish.GibberishDetector;
import org.apache.commons.collections.ArrayStack;
import org.apache.commons.collections.list.SynchronizedList;
import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.w3c.dom.css.Rect;
import webapp.components.ApplicationContextProvider;
import webapp.models.JsonResponse;
import webapp.services.PDFProcessingService;
import webapp.services.TesseractOCRService;

public class Tools {

	private static Properties _properties;
	private static List<Exception> _exceptions;
	private static Leptonica leptInstance = Leptonica.INSTANCE;
	//private static Tesseract tesseract = new Tesseract();
	final static Logger logger = LogManager.getLogger(Tools.class);


//	static {
//		String tessdata = getProperty("tess4j.path");
//		tesseract.setDatapath(tessdata);
//	}
	
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

	@FunctionalInterface
	public interface CheckedConsumer<T> {
		void apply(T t) throws Exception;
	}

	@FunctionalInterface
	public interface CheckedBiConsumer<T, U> {
		void apply(T t, U u) throws Exception;
	}

	public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
		Set<Object> seen = ConcurrentHashMap.newKeySet();
		return t -> seen.add(keyExtractor.apply(t));
	}

//	public static void block() {
//		ThreadPoolTaskExecutor processExecutor = ApplicationContextProvider.getApplicationContext().getBean("processExecutor", ThreadPoolTaskExecutor.class);
//		BlockingQueue<Runnable> queue = processExecutor.getThreadPoolExecutor().getQueue();
//
//		while(queue.size() > 1000) {
//			try {
//				Thread.sleep(100);
//				queue.take().run();
//			} catch (InterruptedException e) {
//				logger.error(e.getMessage(), e);
//			}
//		}
//	}

	public static String extractPDFText(File pdfFile, GibberishDetector detector, PDFProcessingService pdfProcessingService, TesseractOCRService tesseractOCRService) {
		//String temporaryFileRepo = Tools.getProperty("mongodb.temporaryFileRepo");
		StringBuilder parsedText = new StringBuilder();
		//final double pdfGibberishThreshold = 0.75; //set this threshold very high to avoid using OCR whenever possible
		//final double ocrGibberishThreshold = 0.05; //set this threshold low to encourage additional image processing when using OCR
		try {
			logger.info("Begin PDF text extraction for file: " + pdfFile.getName());
			RandomAccessFile randomAccessFile = new RandomAccessFile(pdfFile, "r");
			PDFParser parser = new PDFParser(randomAccessFile);
			parser.parse();
			COSDocument cosDoc = parser.getDocument();
			PDDocument pdDoc = new PDDocument(cosDoc);
			int pageCount = pdDoc.getNumberOfPages();
			logger.info("PDF contains " + pageCount + " page(s).");

			Map<Integer, Future<String>> pdfTasks = new HashMap<>();

			for (int i = 1; i <= pageCount; i++) {
				logger.info("Queueing Page " + i + " for processing");
				Future<String> pdfTask = pdfProcessingService.process(pdfFile, pdDoc, i, detector, tesseractOCRService);
				pdfTasks.put(i, pdfTask);

//				PDFTextStripper pdfStripper = new PDFTextStripper();
//				pdfStripper.setStartPage(i);
//				pdfStripper.setEndPage(i);
//				String parsedPage = pdfStripper.getText(pdDoc);
//
//				//The pdf document may contain some arbitrary text encoding, in which case text extraction
//				// will be problematic.  In such a case the only option is to use OCR.
//				double pdfPercentGibberish = detector.getPercentGibberish(parsedPage);
//				if (Strings.isNullOrEmpty(parsedPage.trim()) ||
//						getAsciiPercentage(parsedPage) < 0.8 ||
//						pdfPercentGibberish > pdfGibberishThreshold) {
//					//Use OCR to extract page text
//					//first convert page to TIFF format that is compatible with OCR
//					String filename = temporaryFileRepo + pdfFile.getName() + "_" + i;
//					File pageFile = new File(filename);
//					PdfBoxUtilities.splitPdf(pdfFile, pageFile, i, i);
//
//					File tiffFile = PdfBoxUtilities.convertPdf2Tiff(pageFile);
//					File binFile = null;
//
//					try {
//						//The TIFF file may comprise a scanned page of plain text an engineering schematic or a map.  In that case,
//						//preprocessing is necessary before OCR can be performed.
//						binFile = binarizeImage(tiffFile);
//						String output = tesseract.doOCR(binFile);
//
//						//The OCR process may produce some gibberish output.  A threshold is used
//						//to deduce a point at which a different tactic is needed to extract information from the page.
//						//For instance, the page may consist of a map.  In that case, some image manipulation can
//						//help with extracting as much knowledge as possible from the page.
//						double ocrPercentGibberish = detector.getPercentGibberish(output);
//						if (ocrPercentGibberish <= ocrGibberishThreshold) {
//							//At this point it is very likely that the current page comprises scanned text.
//							//No further processing is needed.
//                            parsedText.append(output);
//                        } else {
//							//The page likely contains a map or an engineering schematic.  It may be possible to extract
//							//more information from the page by piecewise analysis.
//							output = doOCROnMap(binFile, detector, tesseractOCRService);
//							double outputGibberish = detector.getPercentGibberish(output);
//
//							if (outputGibberish > ocrGibberishThreshold) {
//								//As a final attempt, remove lines from the image and try extraction again.
//								removeLines(tiffFile);
//								String noLinesOutput = doOCROnMap(tiffFile, detector, tesseractOCRService);
//								double noLinesGibberish = detector.getPercentGibberish(noLinesOutput);
//
//								output = outputGibberish < noLinesGibberish ? output : noLinesOutput;
//							}
//
//							output = extractNouns(output);
//
//							parsedText.append(output);
//						}
//					} catch (TesseractException e) {
//						logger.error(e.getMessage(), e);
//					} finally {
//						//make sure the tiff file is deleted
//						if (binFile != null) {
//							binFile.delete();
//						}
//						tiffFile.delete();
//					}
//				} else {
//					parsedText.append(parsedPage);
//				}
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
					String output = pdfTasks.get(i).get();
					parsedText.append(output);
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
			}

			logger.info("PDF data extraction complete for file: " + pdfFile.getName());

			randomAccessFile.close();
			pdDoc.close();
			cosDoc.close();
		} catch (IOException  e) {
			logger.error(e.getMessage(), e);
		}
		return parsedText.toString();
	}

//	private static String extractNouns(String input) {
//		//Use POS Tagging to extract nouns from the OCR output
//		List<List<TaggedWord>> pos = NLPTools.tagText(input);
//		StringBuilder posOutput = new StringBuilder();
//		for (List<TaggedWord> taggedWords : pos) {
//			boolean newLine = false;
//			for (TaggedWord taggedWord : taggedWords) {
//				String tag = taggedWord.tag();
//				if (tag.compareTo("NNP") == 0 || tag.compareTo("NN") == 0) {
//					posOutput.append(taggedWord.word());
//					posOutput.append(" ");
//					newLine = true;
//				}
//			}
//			if (newLine) {
//				posOutput.append(System.lineSeparator());
//			}
//		}
//
//		return posOutput.toString();
//	}
//
//	private static String doOCROnMap(File tiffFile, GibberishDetector detector, TesseractOCRService tesseractOCRService) {
//		CopyOnWriteArrayList<String> allOutput = new CopyOnWriteArrayList<>();
//
//		//rotate 90 degrees clockwise and extract data
////		for (int angle = 0; angle <= 270; angle += 90) {
////			if (angle > 0) {
////				rotateImage(tiffFile);
////			}
//
//		Pix pix = leptInstance.pixRead(tiffFile.getPath());
//		int width = leptInstance.pixGetWidth(pix);
//		int height = leptInstance.pixGetHeight(pix);
//		int minWidth = width / 8;
//		int minHeight = height / 8;
//
//		Dimension size = new Dimension(width, height);
//		Point ul = new Point(0, 0);
//
//		Rectangle rect = new Rectangle(ul, size);
//		List<Future<Boolean>> tasks = new ArrayList<>();
//
//		doOCRByRectangles(tasks, tesseractOCRService, tiffFile, rect, minWidth, minHeight, allOutput, detector);
//
//		Rectangle convRect = new Rectangle(ul, new Dimension(width / 6, height / 6));
//		int widthStep = width / 12;
//		int heightStep = height / 12;
//
//		doOCRByConvolution(tasks, tesseractOCRService, tiffFile, convRect, width, height, widthStep, heightStep, allOutput, detector);
//
//		//wait for all threads to complete before rotating image
//		while(tasks.stream().anyMatch(p -> !p.isDone())) {
//			try {
//				Thread.sleep(1000);
//			} catch (InterruptedException e) {
//				logger.error(e.getMessage(), e);
//			}
//		}
////		}
//		String output = detector.removeGibberishLines(String.join(System.lineSeparator(), allOutput));
//
//		output = postProcessForOCR(output);
//
//		return output;
//	}
//
//	private static String postProcessForOCR(String input) {
//		NamedEntityRecognizer recognizer = new NamedEntityRecognizer(null);
//		String[] sentences = recognizer.detectSentences(input, true);
//
//		Map<String, Integer> termFrequency = new TreeMap<>();
//		for (String sentence : sentences) {
//			String[] tokens = NLPTools.detectTokens(sentence);
//			for (String token : tokens) {
//				String norm = token.toLowerCase();
//				if (termFrequency.containsKey(norm)) {
//					Integer freq = termFrequency.get(norm);
//					termFrequency.replace(norm, ++freq);
//				} else {
//					termFrequency.put(norm, 1);
//				}
//			}
//		}
//
//		TreeSet<String> highFrequency = termFrequency.entrySet().stream()
//				.filter(p -> p.getValue() > 1)
//				.map(p -> p.getKey())
//				.collect(Collectors.toCollection(TreeSet::new));
//
//		StringBuilder parsed = new StringBuilder();
//
//		for (String sentence : sentences) {
//			String[] tokens = NLPTools.detectTokens(sentence);
//			for (String token : tokens) {
//				String norm = token.toLowerCase();
//				if (highFrequency.contains(norm)) {
//					parsed.append(token);
//					parsed.append(" ");
//				}
//			}
//		}
//
//		//remove single and double lowercase character combinations that may be cluttering the text
//		String output = parsed.toString().replaceAll("\\b[a-zA-Z]{1,2}\\b", "");
//
//		//final sterilization pass to filter out noise
//		sentences = recognizer.detectSentences(output, true);
//
//		output = String.join("\r\n", sentences);
//
//		return output;
//	}
//
//	private static void doOCRByRectangles(List<Future<Boolean>> tasks, TesseractOCRService tesseractOCRService, File tiffFile, Rectangle rect, int minWidth, int minHeight, List<String> rectOutput, GibberishDetector detector) {
//		Dimension size = rect.getSize();
//
//		Dimension halfSize = new Dimension(size.width / 2, size.height / 2);
//		if (halfSize.width >= minWidth && halfSize.height >= minHeight) {
//			Point ul = rect.getLocation();
//			Point ll = new Point(ul.x, ul.y + halfSize.height);
//			Point ur = new Point(ul.x + halfSize.width, ul.y);
//			Point lr = new Point(ul.x + halfSize.width, ul.y + halfSize.height);
//
//			Rectangle rul = new Rectangle(ul, halfSize);
//			Rectangle rll = new Rectangle(ll, halfSize);
//			Rectangle rur = new Rectangle(ur, halfSize);
//			Rectangle rlr = new Rectangle(lr, halfSize);
//
//			Future<Boolean> result = tesseractOCRService.process(tiffFile, rect, rectOutput, detector);
//			tasks.add(result);
//
//			doOCRByRectangles(tasks, tesseractOCRService, tiffFile, rul, minWidth, minHeight, rectOutput, detector);
//			doOCRByRectangles(tasks, tesseractOCRService, tiffFile, rll, minWidth, minHeight, rectOutput, detector);
//			doOCRByRectangles(tasks, tesseractOCRService, tiffFile, rur, minWidth, minHeight, rectOutput, detector);
//			doOCRByRectangles(tasks, tesseractOCRService, tiffFile, rlr, minWidth, minHeight, rectOutput, detector);
//		}
//	}
//
//	private static void doOCRByConvolution(List<Future<Boolean>> tasks, TesseractOCRService tesseractOCRService, File tiffFile, Rectangle rect, int imgWidth, int imgHeight, int widthStep, int heightStep,
//										   List<String> convOutput, GibberishDetector detector) {
//		while(rect.y <= (imgHeight - rect.height)) {
//			while(rect.x <= (imgWidth - rect.width)) {
//				Rectangle convRect = new Rectangle(rect.getLocation(), rect.getSize());
//				Future<Boolean> result = tesseractOCRService.process(tiffFile, convRect, convOutput, detector);
//				tasks.add(result);
//				rect.x += widthStep;
//			}
//			rect.x = 0;
//			rect.y += heightStep;
//		}
//	}
////
////	private static int calculateBinarizationThreshold(Pix pix) {
////		//Use pixel density compared against the mean to determine a cutoff threshold for binarization
////		Numa hist = Leptonica1.pixGetGrayHistogram(pix, 1);
////		float[] distribution = hist.array.getPointer().getFloatArray(0, hist.nalloc);
////		double[] dblDist = IntStream.range(0, distribution.length).mapToDouble(i -> distribution[i]).toArray();
////
////		//exclude either extreme intensities from the calculation of mean and standard deviation
////		List<Double> dblDistTruncatedList = new ArrayList<>();
////		for (int i = 10; i < dblDist.length - 10; i++) {
////			dblDistTruncatedList.add(dblDist[i]);
////		}
////
////		Double[] dblDistTruncated = new Double[dblDistTruncatedList.size()];
////		dblDistTruncated = dblDistTruncatedList.toArray(dblDistTruncated);
////
////		double[] dblDistTruncatedPrim = ArrayUtils.toPrimitive(dblDistTruncated);
////
////		//Find the mean pixel count for the truncated greyscale
////		Mean mean = new Mean();
////		double mnCount = mean.evaluate(dblDistTruncatedPrim);
////
////		//Find the standard deviation, so that most pixel intensities will be near the mean
////		StandardDeviation std = new StandardDeviation();
////		double stdCount = std.evaluate(dblDistTruncatedPrim);
////
////		//define a maximum pixel count
////		double maxCount = mnCount + stdCount;
////
//////		//Find the smallest index of the pixel having a count less than one standard deviation to the right of the truncated mean
//////		int minThreshold = 0;
//////		for (int i = dblDist.length - 10; i >= 10; i--) {
//////			if (dblDist[i] < maxCount) {
//////				minThreshold = i;
//////			}
//////		}
//////
//////		//Find the largest index of the pixel having a count less than one standard deviation to the right of the truncated mean
//////		int maxThreshold = 0;
//////		for (int i = 10; i < dblDist.length - 10; i++) {
//////			if (dblDist[i] < maxCount) {
//////				maxThreshold = i;
//////			}
//////		}
////
////		//find the maximum pixel index having a count that is still less than the maximum allowed count
////		//this pixel index is the threshold cutoff for binarization
////		int threshold = 0;
////		double pixelCount = 0;
////		for (int i = 10; i < dblDist.length - 10; i++) {
////			if (dblDist[i] < maxCount && dblDist[i] >= pixelCount) {
////				threshold = i;
////				pixelCount = dblDist[i];
////			}
////		}
////
//////		//Take the average of the max and min thresholds to find a center point of image intensity.
//////		// Values below this limit will be black after binarization.
//////		int threshold = (minThreshold + maxThreshold) / 2;
////
////		return threshold;
////	}
//
//	private static File binarizeImage(File image) {
//		Pix pix = leptInstance.pixRead(image.getPath());
//		Pix pix1 = leptInstance.pixConvertRGBToGrayFast(pix);
//		Pix pix2 = binarizeImage(pix1);
//
//		String binarizedImagePath = image.getParent() + "\\" + FilenameUtils.getBaseName(image.getPath()) + "_bin." + FilenameUtils.getExtension(image.getPath());
//
//		leptInstance.pixWrite(binarizedImagePath, pix2, ILeptonica.IFF_TIFF);
//
//		return new File(binarizedImagePath);
//	}
//
//	private static Pix binarizeImage(Pix pix) {
//		int width = leptInstance.pixGetWidth(pix);
//		int height = leptInstance.pixGetHeight(pix);
//
//		PointerByReference ppixth = new PointerByReference();
//		PointerByReference ppixd = new PointerByReference();
//		leptInstance.pixOtsuAdaptiveThreshold(pix, width, height, 0, 0, (float)0.1, ppixth, ppixd);
//		Pix pixd = new Pix(ppixd.getValue());
//
//		return pixd;
//	}
//
//	private static void removeLines(File image) {
////		for (int deg = 0; deg <= 180; deg+=10) {
////			Pix pix = leptInstance.pixRead(image.getPath());
////			Pix pixGray = leptInstance.pixConvertRGBToGrayFast(pix);
////			if (pixGray == null) {
////				pixGray = pix;
////			}
////			//int width = leptInstance.pixGetWidth(pixGray);
////			//int height = leptInstance.pixGetHeight(pixGray);
////
////			//leptInstance.box
////
////			float rad = (float)Math.toRadians(deg);
////			Pix pixRot = leptInstance.pixRotate(pixGray, rad, Leptonica.L_ROTATE_AREA_MAP, Leptonica.L_BRING_IN_WHITE, 0, 0);
////			Pix pixRem = LeptUtils.removeLines(pixRot);
////			Pix pixCor = leptInstance.pixRotate(pixRem, -rad, Leptonica.L_ROTATE_AREA_MAP, Leptonica.L_BRING_IN_WHITE, 0, 0);
////			//leptInstance.pixDeskew(pixCor, )
////
////
////
////			String correctedImagePath = image.getParent() + "\\" + FilenameUtils.getBaseName(image.getPath()) + "_cor." + FilenameUtils.getExtension(image.getPath());
////			leptInstance.pixWrite(correctedImagePath, pixCor, ILeptonica.IFF_TIFF);
////			LeptUtils.disposePix(pixRot);
////			//LeptUtils.disposePix(pixRem);
////			LeptUtils.disposePix(pixCor);
////		}
//		//leptInstance.pixRotate180(pixR, pixR);
//		//pixR = leptInstance.pixThresholdToBinary(pixR, 170);
//		//leptInstance.pixWrite(image.getPath(), pixR, ILeptonica.IFF_TIFF);
//
//
//
//		Pix pix = leptInstance.pixRead(image.getPath());
//		Pix pix1 = leptInstance.pixConvertRGBToGrayFast(pix);
//		Pix pix2 = LeptUtils.removeLines(pix1);
//		Pix pix3 = leptInstance.pixRotate90(pix2, 1);
//		Pix pix4 = LeptUtils.removeLines(pix3);
//		Pix pix5 = leptInstance.pixRotate90(pix4, -1);
//		Pix pix6 = binarizeImage(pix5);
//		leptInstance.pixWrite(image.getPath(), pix6, ILeptonica.IFF_TIFF);
//	}
//
//	private static void rotateImage(File image) {
//		Pix pix = leptInstance.pixRead(image.getPath());
//		Pix pix90 = leptInstance.pixRotate90(pix, 1);
//		leptInstance.pixWrite(image.getPath(), pix90, ILeptonica.IFF_TIFF);
//	}
//
//	private static double getAsciiPercentage(String docText) {
//		return (double) CharMatcher.ascii().countIn(docText) / (double)docText.length();
//	}
}
