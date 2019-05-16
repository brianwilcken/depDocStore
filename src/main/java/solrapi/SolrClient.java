package solrapi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Strings;
import common.FacilityTypes;
import common.Tools;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;
import nlp.NLPTools;
import nlp.NamedEntity;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.*;
import org.apache.solr.client.solrj.SolrQuery.SortClause;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MoreLikeThisParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.SimpleOrderedMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.io.Files;
import org.joda.time.DateTime;

public class SolrClient {

	private String collection = "dependencies";
	private static final String USERNAME = Tools.getProperty("solr.username");
	private static final String PASSWORD = Tools.getProperty("solr.password");
	
	final static Logger logger = LogManager.getLogger(SolrClient.class);

	private HttpSolrClient client;
	private static ObjectMapper mapper = new ObjectMapper();

	private static class PreemptiveAuthInterceptor implements HttpRequestInterceptor {

		public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
			AuthState authState = (AuthState) context.getAttribute(HttpClientContext.TARGET_AUTH_STATE);
			// If no auth scheme available yet, try to initialize it
			// preemptively
			if (authState.getAuthScheme() == null) {
				CredentialsProvider credsProvider = (CredentialsProvider)
						context.getAttribute(HttpClientContext.CREDS_PROVIDER);
				HttpHost targetHost = (HttpHost) context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST);
				AuthScope authScope = new AuthScope(targetHost.getHostName(), targetHost.getPort());
				Credentials creds = credsProvider.getCredentials(authScope);
				if(creds == null){

				}
				authState.update(new BasicScheme(), creds);
			}
		}
	}

	public SolrClient(String solrHostURL) {
		CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
		credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(USERNAME, PASSWORD));
		CloseableHttpClient httpClient = HttpClientBuilder.create()
				.addInterceptorFirst(new PreemptiveAuthInterceptor())
				.setDefaultCredentialsProvider(credentialsProvider)
				.build();

		client = new HttpSolrClient.Builder(solrHostURL).withHttpClient(httpClient).build();
	}

	public SolrClient(String solrHostURL, String collection) {
		this(solrHostURL);
		this.collection = collection;
	}
	
	public static void main(String[] args) {
		SolrClient client = new SolrClient("http://localhost:8983/solr");
		//client.writeCorpusDataToFile(Tools.getProperty("nlp.waterNerTrainingFile"), client::getWaterDataQuery, client::formatForNERModelTraining);

		//retrieveAnnotatedData(client, "0bb9ead9-c71a-43fa-8e80-35e5d566c15e");
		//updateAnnotatedData(client, "0bb9ead9-c71a-43fa-8e80-35e5d566c15e");

		//client.writeCorpusDataToFile("data/clustering.csv", "", client::getClusteringDataQuery, client::formatForClustering, new NERThrottle());
		client.writeCorpusDataToFile("PythonDataClustering/topic-modeling.data", "id,filename,parsed","", client::getClusteringDataQuery, client::formatForTopicModeling, new NERThrottle());

//		try {
//			logger.info("Begin dependency data export");
//			client.WriteDataToFile("data/depData.json", "filename:*", 1000000);
//			logger.info("Dependency data export complete");
//		} catch (SolrServerException e) {
//			e.printStackTrace();
//		}
	}

	private static void retrieveAnnotatedData(SolrClient client, String id) {
		try {
			SolrDocumentList docs = client.QuerySolrDocuments("id:" + id, 1, 0, null, null);
			SolrDocument doc = docs.get(0);

			String annotated = (String)doc.get("annotated");

			FileUtils.writeStringToFile(new File("data/annotated.txt"), annotated, Charset.forName("Cp1252").displayName());
		} catch (SolrServerException | IOException e) {
			e.printStackTrace();
		}
	}

	private static void updateAnnotatedData(SolrClient client, String id) {
		String annotated = Tools.GetFileString("data/annotated.txt");
		try {
			SolrDocumentList docs = client.QuerySolrDocuments("id:" + id, 1, 0, null, null);
			SolrDocument doc = docs.get(0);
			if (doc.containsKey("annotated")) {
				doc.replace("annotated", annotated);
			} else {
				doc.addField("annotated", annotated);
			}

			client.indexDocument(doc);
		} catch (SolrServerException e) {
			e.printStackTrace();
		}
	}

	public void UpdateDocumentsFromJsonFile(String filePath) throws SolrServerException {
		JsonFactory jsonfactory = new JsonFactory();
		File source = new File(filePath);
		try {
			JsonParser parser = jsonfactory.createParser(source);

			SolrDocumentList docs = new SolrDocumentList();
			int docNum = 0;
			while (parser.nextToken() != JsonToken.END_ARRAY) {
				if (parser.currentToken() == JsonToken.START_OBJECT) {
					++docNum;
					SolrDocument doc = new SolrDocument();
					while (parser.nextToken() != JsonToken.END_OBJECT) {
						String field = parser.getCurrentName();
						parser.nextValue();
						if (parser.currentToken() == JsonToken.START_ARRAY) {
							List<String> values = new ArrayList<>();
							while (parser.nextToken() != JsonToken.END_ARRAY) {
								values.add(parser.getText());
							}
							doc.put(field, values);
						} else {
							String value = parser.getText();
							if (field.equals("lastUpdated") || field.equals("created")) {
								doc.put(field, Tools.getFormattedDateTimeString(Instant.ofEpochMilli(Long.parseLong(value))));
							} else if (StringUtils.isNumeric(value)) {
								long number = Long.parseLong(value);
								doc.put(field, number);
							} else {
								doc.put(field, value);
							}
						}
					}
					docs.add(doc);
					if (docNum % 100 == 0) {
						indexDocuments(docs);
						docs.clear();
						logger.info("Indexed so far: " + docNum + " documents");
					}
				}
			}
			indexDocuments(docs);
			logger.info("Total Documents Indexed: " + docNum);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	public void indexDocuments(Collection<SolrDocument> docs) throws SolrServerException {
		try {
			if (!docs.isEmpty()) {
				List<SolrInputDocument> inputDocuments = new ArrayList<>();
				for (SolrDocument doc : docs) {
					SolrInputDocument solrInputDocument = convertSolrDocument(doc);
					inputDocuments.add(solrInputDocument);
				}

				client.add(collection, inputDocuments);
				UpdateResponse updateResponse = client.commit(collection);
				
				if (updateResponse.getStatus() != 0) {
					//TODO What should happen if the update fails?
				}
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	private SolrInputDocument convertSolrDocument(SolrDocument doc) {
		SolrInputDocument solrInputDocument = new SolrInputDocument();

		for (String name : doc.getFieldNames()) {
			solrInputDocument.addField(name, doc.getFieldValue(name));
		}

		List<SolrDocument> children = doc.getChildDocuments();
		if (children != null) {
			for (SolrDocument child : children) {
				solrInputDocument.addChildDocument(convertSolrDocument(child));
			}
		}

		return solrInputDocument;
	}

	public void indexDocument(SolrDocument doc) throws SolrServerException {
		List<SolrDocument> docs = new ArrayList<>();
		docs.add(doc);
		indexDocuments(docs);
	}

	public void deleteDocuments(String query) throws SolrServerException {
	    try {
			client.deleteByQuery(collection, query);
			UpdateResponse updateResponse = client.commit(collection);

            if (updateResponse.getStatus() != 0) {
                //TODO What should happen if the update fails?
            }
        } catch (IOException e) {
			logger.error(e.getMessage(), e);
        }
    }

	public Boolean DocumentExists(String queryStr) throws SolrServerException {
		SolrQuery query = new SolrQuery();
		query.setRows(0);
		query.setQuery(queryStr);
		try {
			QueryResponse response = client.query(collection, query);
			return response.getResults().getNumFound() > 0;
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			return false;
		}
	}

	public SimpleOrderedMap<?> QueryFacets(String queryStr, String facetQuery) throws SolrServerException {
		SolrQuery query = new SolrQuery();
		query.setRows(0);
		query.setQuery(queryStr);
		query.add("json.facet", facetQuery);
		try {
			QueryResponse response = client.query(collection, query);
			SimpleOrderedMap<?> facets = (SimpleOrderedMap<?>) response.getResponse().get("facets");
			return facets;
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}
	
	public SolrDocumentList FindSimilarDocuments(String searchText) throws SolrServerException {
		SolrQuery query = new SolrQuery();
	    query.setRequestHandler("/" + MoreLikeThisParams.MLT);
	    query.setParam(CommonParams.STREAM_BODY, searchText);
	    query.setRows(20);
	    try {
			SolrDocumentList response = client.query(collection, query).getResults();
			return response;
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}

	public SolrDocumentList QuerySolrDocuments(String queryStr, int rows, int start, SortClause sort, String[] fields, String... filterQueries) throws SolrServerException {
		SolrQuery query = new SolrQuery();
		query.setQuery(queryStr);
		if (filterQueries != null) {
			query.setFilterQueries(filterQueries);
		}
		query.setRows(rows);
		query.setStart(start);
		if (sort != null) {
			query.setSort(sort);
		}
		if (fields != null) {
			query.setFields(fields);
		}
		try {
			SolrDocumentList response = client.query(collection, query).getResults();
			return response;
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}

	public <T> List<T> QueryIndexedDocuments(Class<T> clazz, String queryStr, int rows, int start, SortClause sort, String[] fields, String... filterQueries) throws SolrServerException {
		SolrQuery query = new SolrQuery();
		query.setQuery(queryStr);
		if (filterQueries != null) {
			query.setFilterQueries(filterQueries);
		}
		query.setRows(rows);
		query.setStart(start);
		if (sort != null) {
			query.setSort(sort);
		} else {

        }
		try {
			Constructor<?> cons;
			try {
				cons = clazz.getConstructor(SolrDocument.class);
			} catch (NoSuchMethodException | SecurityException e1) {
				return null;
			}
			SolrDocumentList response = client.query(collection, query).getResults();
			List<T> typedDocs = convertSolrDocsToTypedDocs(cons, response);
			return typedDocs;
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}

	private <T> List<T> convertSolrDocsToTypedDocs(Constructor<?> cons, SolrDocumentList docs) {
		List<T> typedDocs = (List<T>) docs.stream().map(p -> {
			try {
				return cons.newInstance(p);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				logger.error(e.getMessage(), e);
				return null;
			}
		}).collect(Collectors.toList());

		return typedDocs;
	}

	public static SolrQuery getAnnotatedDataQuery(SolrQuery query) {
		query.setQuery("annotated:*");

		return query;
	}

	public SolrQuery getClusteringDataQuery(SolrQuery query) {
		query.setQuery("parsed:*");
		return query;
	}

	public SolrQuery getDoccatDataQuery(SolrQuery query) {
		query.setQuery("parsed:*");
		query.setFilterQueries("{!frange l=1}ms(lastUpdated,created) ");
		return query;
	}

	public static Function<SolrQuery, SolrQuery> getCategorySpecificNERModelTrainingDataQuery(final String category) {
		Function<SolrQuery, SolrQuery> func = query -> {
			query.setQuery("category:" + category + " AND includeInNERTraining:true");
			return query;
		};
		return func;
	}

	public static Function<SolrQuery, SolrQuery> getCategorySpecificNERModelTestingDataQuery(final String category) {
		Function<SolrQuery, SolrQuery> func = query -> {
			query.setQuery("category:" + category + " AND includeInNERTesting:true");
			return query;
		};
		return func;
	}

	public void formatForWord2VecModelTraining(String unused, SolrDocument doc, OutputStreamWriter writer) throws IOException {
		String parsed = doc.get("parsed").toString();
		parsed = parsed.replace("\r", " ").replace("\n", " ");
		writer.write(parsed);
		writer.write(System.lineSeparator());
	}

	public void formatForDoccatModelTraining(String unused, SolrDocument doc, OutputStreamWriter writer) throws IOException {
		String parsed = doc.get("parsed").toString();
		String normalized = NLPTools.normalizeText(parsed);
		List<String> categories = (List<String>)doc.get("category");
		String output = categories.stream().map(category -> category + "\t" + normalized).reduce((p1, p2) -> p1 + System.lineSeparator() + p2).orElse("");
		writer.write(output);
		writer.write(System.lineSeparator());
	}

	public void formatForNERCorpusReview(String category, SolrDocument doc, OutputStreamWriter writer) throws IOException {
		String filename = doc.get("filename").toString();
		String separator = "***********************************************************";
		writer.write(separator);
		writer.write(filename.toUpperCase());
		writer.write(separator);
		writer.write(System.lineSeparator());
		writer.write(System.lineSeparator());
		formatForNERModelTraining(category, doc, writer);
	}

	public void formatForNERModelTraining(String category, SolrDocument doc, OutputStreamWriter writer) throws IOException {
		final List<String> facilityTypes = FacilityTypes.dictionary.get(category);
		String annotated = doc.get("annotated").toString();
		List<NamedEntity> entities = NLPTools.extractNamedEntities(annotated);
		Map<Integer, List<NamedEntity>> lineEntities = entities.stream()
				.collect(Collectors.groupingBy(p -> p.getLine()));

		List<CoreMap> sentencesList = NLPTools.detectSentencesStanford(annotated);
		String[] sentences = sentencesList.stream().map(p -> p.toString()).toArray(String[]::new);

		List<String> annotatedLinesForCategory = new ArrayList<>();

		for (int s = 0; s < sentences.length; s++) {
			if (lineEntities.containsKey(s)) {
				for (NamedEntity entity : lineEntities.get(s)) {
					if (facilityTypes.contains(entity.getSpan().getType())) {
						//the current sentence contains a valid entity annotation based on the model training category
						String sentence = sentences[s];
						//first remove all annotations to ensure that only category-specific annotations are included in the training data
						//sentence = sentence.replaceAll(" ?<START:.+?> ", "").replaceAll(" <END> ", "");
						sentence = sentence.replaceAll("(?<=\\S\\s)<START:.+?> ", "")
								.replaceAll("\\s?<START:.+?> ", "")
								.replaceAll(" <END>  ?(\\b|\\B)", " ")
								.replaceAll(" <END> ", "");
						List<NamedEntity> validLineEntities = lineEntities.get(s).stream()
								.filter(p -> facilityTypes.contains(p.getSpan().getType()))
								.collect(Collectors.toList());
						String annotatedLine = NLPTools.autoAnnotateSentence(sentence, validLineEntities);
						String formattedLine = NLPTools.fixFormattingForModelTraining(annotatedLine);

						annotatedLinesForCategory.add(formattedLine);
						break;
					}
				}
			} else {
				int size = annotatedLinesForCategory.size();
				if (size > 0) {
					String lastEntry = annotatedLinesForCategory.get(size - 1);
					if (!lastEntry.equals(System.lineSeparator())) {
						annotatedLinesForCategory.add(System.lineSeparator());
					}
				}
			}
		}

		//List<String> annotatedLines = Arrays.stream(sentences).filter(p -> p.contains("<START:")).collect(Collectors.toList());

		if (annotatedLinesForCategory.size() > 0) {
			String onlyAnnotated = String.join("\r\n", annotatedLinesForCategory);
			writer.write(onlyAnnotated);
			writer.write(System.lineSeparator());
			writer.write(System.lineSeparator());
		}
	}

	public void formatForClustering(String unused, SolrDocument doc, OutputStreamWriter writer) throws IOException {
		String id = doc.get("id").toString();
		String filename = doc.get("filename").toString();
		String parsed = doc.get("parsed").toString();
		String category = doc.containsKey("category") ? doc.get("category").toString() : "NONE";
		String clusteringStr = id + "," + filename.replace(",", "") + "_<" + category.replace(",", ";") + ">" + "," + parsed.replace(",", "");
		clusteringStr = clusteringStr.replace("\r", " ")
				.replace("\n", " ");
		writer.write(clusteringStr);
		writer.write(System.lineSeparator());
	}

	public void formatForTopicModeling(String unused, SolrDocument doc, OutputStreamWriter writer) throws IOException {
		String created = doc.get("created").toString();
		String lastUpdated = doc.get("lastUpdated").toString();
		String filename = doc.get("id").toString() + "|" + doc.get("filename").toString().replace(" ", "_");
		String parsed = doc.get("parsed").toString();
		String category = doc.containsKey("category") ? ((List)doc.get("category")).stream().reduce((c,n) -> c + ";" + n).orElse("NONE").toString() : "NONE";
		if (created.equals(lastUpdated)) {
			category = "NEW;" + category;
		}
		String ldaStr = filename.replace(",", "") + "," + category + "," + parsed.replace(",", "");
		ldaStr = ldaStr.replace("\r", " ")
				.replace("\n", " ");
		writer.write(ldaStr);
		writer.write(System.lineSeparator());
	}

	public void WriteDataToFile(String filePath, String queryStr, int rows, String... filterQueries) throws SolrServerException {
		ObjectWriter objWriter = new ObjectMapper().writer().withDefaultPrettyPrinter();
		SolrDocumentList docs = QuerySolrDocuments(queryStr, rows, 0, null, null, filterQueries);

		File file = new File(filePath);
		try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)){
			file.getParentFile().mkdirs();
			writer.write("[ ");
			for (int i = 0; i < docs.size(); i++) {
				SolrDocument doc = docs.get(i);
				if (doc.containsKey("_version_")) {
					doc.remove("_version_");
				}
				String output = objWriter.writeValueAsString(doc);
				writer.write(output);
				if (i != docs.size() - 1) {
					writer.write(", ");
					writer.write(System.lineSeparator());
				}
			}
			writer.write("]");
			writer.close();
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public Map<String, String> writeCorpusDataToFile(String trainingFilePath, String header, String category, Function<SolrQuery, SolrQuery> queryGetter,
											  Tools.CheckedTriConsumer<String, SolrDocument, OutputStreamWriter> dataFormatter, TrainingDataThrottle throttle) {
		Map<String, String> corpusDocs = new HashMap<>();
		SolrQuery query = queryGetter.apply(new SolrQuery());
		query.setRows(1000000);
		File file = new File(trainingFilePath);
		file.getParentFile().mkdirs();
		try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)){
			if (!Strings.isNullOrEmpty(header)) {
				writer.write(header + System.lineSeparator());
			}
			final BlockingQueue<SolrDocument> tmpQueue = new LinkedBlockingQueue<SolrDocument>();
			client.queryAndStreamResponse(collection, query, new CallbackHandler(tmpQueue));
			throttle.init(tmpQueue.size());

			SolrDocument tmpDoc;
			do {
				tmpDoc = tmpQueue.take();
				if (!(tmpDoc instanceof StopDoc) && throttle.check(tmpDoc)) {
					if (tmpDoc.containsKey("id")) {
						String id = tmpDoc.get("id").toString();
						String filename = tmpDoc.get("filename").toString();
						corpusDocs.put(filename, id);
					}
					dataFormatter.apply(category, tmpDoc, writer);
				}
			} while (!(tmpDoc instanceof StopDoc));

			writer.close();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return corpusDocs;
	}

	private class StopDoc extends SolrDocument {
		// marker to finish queuing
	}

	private class CallbackHandler extends StreamingResponseCallback {
		private BlockingQueue<SolrDocument> queue;
		private long currentPosition;
		private long numFound;

		public CallbackHandler(BlockingQueue<SolrDocument> aQueue) {
			queue = aQueue;
		}

		@Override
		public void streamDocListInfo(long aNumFound, long aStart, Float aMaxScore) {
			// called before start of streaming
			// probably use for some statistics
			currentPosition = aStart;
			numFound = aNumFound;
			if (numFound == 0) {
				queue.add(new StopDoc());
			}
		}

		@Override
		public void streamSolrDocument(SolrDocument doc) {
			currentPosition++;
			queue.add(doc);
			if (currentPosition == numFound) {
				queue.add(new StopDoc());
			}
		}
	}

	private static abstract class TrainingDataThrottle {

		protected String throttleFor;
		protected double throttlePercent;

		public TrainingDataThrottle(String throttleFor, double throttlePercent) {
			this.throttleFor = throttleFor;
			this.throttlePercent = throttlePercent;
		}

		public abstract void init(int numDocs);

		public abstract boolean check(SolrDocument doc);
	}

	public static class DoccatThrottle extends TrainingDataThrottle {

		private int numDocs;
		private int throttleForCount;

		public DoccatThrottle() {
			super("Not_Applicable", 0.5);
		}

		@Override
		public void init(int numDocs) {
			this.numDocs = numDocs;
		}

		@Override
		public boolean check(SolrDocument doc) {
			if (doc.containsKey("category") && ((List)doc.get("category")).contains(throttleFor)) {
				double currentPercent = (double)throttleForCount / (double)numDocs;
				if (currentPercent > throttlePercent) {
					return false;
				} else {
					//randomization such that not always given document is added
					//50% likelihood the document is added
					double random = Math.random();
					if (random > 0.5) {
						throttleForCount++;
						return true;
					}
					return false;
				}
			}
			return true;
		}
	}

	public static class NERThrottle extends TrainingDataThrottle {

		public NERThrottle() {
			super("", 0);
		}

		@Override
		public void init(int numDocs) {

		}

		@Override
		public boolean check(SolrDocument doc) {
			return true;
		}
	}


}
