package common;

import java.io.*;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import net.sourceforge.lept4j.ILeptonica;
import net.sourceforge.lept4j.Leptonica;
import net.sourceforge.lept4j.Pix;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.util.PdfBoxUtilities;
import nlp.NLPTools;
import nlp.NamedEntityRecognizer;
import nlp.gibberish.GibberishDetector;
import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.ClassPathResource;
import webapp.models.JsonResponse;

public class Tools {

	private static Properties _properties;
	private static List<Exception> _exceptions;
	private static Leptonica leptInstance = Leptonica.INSTANCE;
	private static Tesseract tesseract = new Tesseract();
	final static Logger logger = LogManager.getLogger(Tools.class);


	static {
		String tessdata = getProperty("tess4j.path");
		tesseract.setDatapath(tessdata);
	}
	
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

	public static String extractPDFText(File pdfFile, GibberishDetector detector) {
		String temporaryFileRepo = Tools.getProperty("mongodb.temporaryFileRepo");
		StringBuilder parsedText = new StringBuilder();
		final double pdfGibberishThreshold = 0.75; //set this threshold very high to avoid using OCR whenever possible
		final double ocrGibberishThreshold = 0.05; //set this threshold low to encourage additional image processing when using OCR
		try {
			RandomAccessFile randomAccessFile = new RandomAccessFile(pdfFile, "r");
			PDFParser parser = new PDFParser(randomAccessFile);
			parser.parse();
			COSDocument cosDoc = parser.getDocument();
			PDFTextStripper pdfStripper = new PDFTextStripper();
			PDDocument pdDoc = new PDDocument(cosDoc);
			int pageCount = pdDoc.getNumberOfPages();
			for (int i = 1; i <= pageCount; i++) {
				pdfStripper.setStartPage(i);
				pdfStripper.setEndPage(i);
				String parsedPage = pdfStripper.getText(pdDoc);

				//The pdf document may contain some arbitrary text encoding, in which case text extraction
				// will be problematic.  In such a case the only option is to use OCR.
				double pdfPercentGibberish = detector.getPercentGibberish(parsedPage);
				if (Strings.isNullOrEmpty(parsedPage.trim()) ||
						getAsciiPercentage(parsedPage) < 0.8 ||
						pdfPercentGibberish > pdfGibberishThreshold) {
					//Use OCR to extract page text
					//first convert page to TIFF format that is compatible with OCR
					String filename = temporaryFileRepo + pdfFile.getName() + "_" + i;
					File pageFile = new File(filename);
					PdfBoxUtilities.splitPdf(pdfFile, pageFile, i, i);

					File tiffFile = PdfBoxUtilities.convertPdf2Tiff(pageFile);

					try {
						//The TIFF file may comprise a scanned page of plain text.  In that case,
						//a single OCR pass is all that is needed to extract the text.
						String output = tesseract.doOCR(tiffFile);

						//The OCR process may produce some gibberish output.  A threshold is used
						//to deduce a point at which a different tactic is needed to extract information from the page.
						//For instance, the page may consist of a map.  In that case, some image manipulation can
						//help with extracting as much knowledge as possible from the page.
						double ocrPercentGibberish = detector.getPercentGibberish(output);
						if (ocrPercentGibberish <= ocrGibberishThreshold) {
							//At this point it is very likely that the current page comprises scanned text.
							//No further processing is needed.
                            parsedText.append(output);
                        } else {
							//The page likely contains a map or an engineering schematic.  It may be possible to extract
							//more information from the page by applying some rotations to the TIFF file.
							parsedText.append(detector.removeGibberishLines(output));

							//rotate 90 degrees clockwise and extract data
							for (int angle = 90; angle <= 270; angle += 90) {
								rotateImage(tiffFile);
								output = tesseract.doOCR(tiffFile);
								parsedText.append(detector.removeGibberishLines(output));
							}
						}
					} catch (TesseractException e) {
						logger.error(e.getMessage(), e);
					} finally {
						//make sure the tiff file is deleted
						tiffFile.delete();
					}
				} else {
					parsedText.append(parsedPage);
				}
			}

			randomAccessFile.close();
			pdDoc.close();
			cosDoc.close();
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		return parsedText.toString();
	}

	private static void rotateImage(File image) {
		Pix pix = leptInstance.pixRead(image.getPath());
		Pix pix90 = leptInstance.pixRotate90(pix, 1);
		leptInstance.pixWrite(image.getPath(), pix90, ILeptonica.IFF_TIFF);
	}

	private static double getAsciiPercentage(String docText) {
		return (double) CharMatcher.ascii().countIn(docText) / (double)docText.length();
	}
}
