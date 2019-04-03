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

import com.google.common.base.Strings;
import net.sourceforge.lept4j.*;
import nlp.NLPTools;
import nlp.gibberish.GibberishDetector;
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
							parsed.append("PAGE " + i + " IS A SCHEMATIC");
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

	public static void main(String[] args) {
		TextExtractionProcessManager mgr = new TextExtractionProcessManager();
		File tiffFile = new File("E:\\LeptonicaTesting\\MASS_ELECT_map-major-facilities.tiff");

		GibberishDetector detector = new GibberishDetector();

		Pix pix = ImageTools.loadImage(tiffFile);
		int width = Leptonica.INSTANCE.pixGetWidth(pix);
		int height = Leptonica.INSTANCE.pixGetHeight(pix);

		Dimension size = new Dimension(width / 6, height / 6);
		Point ul = new Point(0, 0);
		Rectangle rect = new Rectangle(ul, size);

		List<TextExtractionTask> extractions = new ArrayList<>();

		float baseRank = 0.1f;
		int rotStep = 360; //degrees
		int widthStep = width / 12;
		int heightStep = height / 12;
		while(rect.y <= (height - rect.height)) {
			while(rect.x <= (width - rect.width)) {
				Rectangle convRect = new Rectangle(rect.getLocation(), rect.getSize());

				Pix pix2 = ImageTools.cropImage(pix, convRect);
				Pix pix2gray = ImageTools.convertImageToGrayscale(pix2);
				Pix pix2bin = ImageTools.binarizeImage(pix2gray);

				float rank = baseRank;
				while (rank <= 1.0f) {
					Pix pix2rank = Leptonica.INSTANCE.pixBlockrank(pix2bin, null, 2, 2, rank);
					for (int rot = 0; rot <= 345; rot += rotStep) {
						TextExtractionTask extraction = new TextExtractionTask(mgr, convRect, rank, rot, tiffFile, pix2rank);
						extractions.add(extraction);
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


		while (extractions.stream().anyMatch(p -> p.getMyThread().isAlive())) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		List<TextExtractionCandidate> candidates = new ArrayList<>();
		rect = new Rectangle(ul, size);
		while(rect.y <= (height - rect.height)) {
			while(rect.x <= (width - rect.width)) {
				Point point = new Point(rect.x, rect.y);
				List<TextExtractionTask> containing = extractions.stream()
						.filter(p -> p.getRect().contains(point))
						.collect(Collectors.toList());

				for (int rot = 0; rot <= 345; rot += rotStep) {
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

				rect.x += widthStep;
			}
			rect.x = 0;
			rect.y += heightStep;
		}


		rect = new Rectangle(ul, size);
		List<List<String>> outputLines = new ArrayList<>();
		while(rect.y <= (height - rect.height)) {
			List<String> horizontal = new ArrayList<>();
			while(rect.x <= (width - rect.width)) {
				Point point = new Point(rect.x, rect.y);
				List<TextExtractionCandidate> containing = candidates.stream()
						.filter(p -> p.getRect().contains(point))
						.collect(Collectors.toList());

				for (int rot = 0; rot <= 345; rot += rotStep) {
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

				rect.x += widthStep;
			}
			outputLines.add(horizontal);
			rect.x = 0;
			rect.y += heightStep;
		}

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

//		String outPath = tiffFile.getParent() + "\\" + baseName + ".txt";
//		File outFile = new File(outPath);
//		try {
//			FileUtils.writeStringToFile(outFile, bldr.toString(), Charsets.UTF_8);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}

		ImageTools.disposePixs(pix);
	}
}
