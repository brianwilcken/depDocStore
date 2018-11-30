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

	public static String removeAllNumbers(String document) {
		document = document.replaceAll("\\d+", "");

		return document;
	}

	public static String removeSpecialCharacters(String document) {
		document = document.replaceAll("[$-,/:-?{-~!\"^_`\\[\\]+]", "");
		document = document.replace("-", " ");

		return document;
	}

	public static boolean numericRangeCompare(double num1, double num2, double rng) {
		return ((num1 - rng) < num2) && (num2 < (num1 + rng));
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

	public static String extractPDFText(File pdfFile) {
		PDFProcessingService pdfProcessingService = ApplicationContextProvider.getApplicationContext().getBean(PDFProcessingService.class);
		//String temporaryFileRepo = Tools.getProperty("mongodb.temporaryFileRepo");
		StringBuilder parsed = new StringBuilder();
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
				Future<String> pdfTask = pdfProcessingService.process(pdfFile, pdDoc, i);
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
					String output = pdfTasks.get(i).get();
					parsed.append(output);
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

		return parsedText;
	}
}
