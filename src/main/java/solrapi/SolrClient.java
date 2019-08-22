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
import java.util.concurrent.Semaphore;
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
import nlp.*;
import org.apache.commons.io.FileUtils;
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
import org.apache.solr.common.util.SimpleOrderedMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class SolrClient {

	private String collection = "dependencies";
	private static final String USERNAME = Tools.getProperty("solr.username");
	private static final String PASSWORD = Tools.getProperty("solr.password");
	
	final static Logger logger = LogManager.getLogger(SolrClient.class);

	private HttpSolrClient client;
	private static ObjectWriter objWriter = new ObjectMapper().writer().withDefaultPrettyPrinter();

	private TopicModeller topicModeller;

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
		SolrClient client = new SolrClient("http://134.20.2.51:8983/solr");
		//client.writeCorpusDataToFile(Tools.getProperty("nlp.waterNerTrainingFile"), client::getWaterDataQuery, client::formatForNERModelTraining);

		//retrieveAnnotatedData(client, "0bb9ead9-c71a-43fa-8e80-35e5d566c15e");
		//updateAnnotatedData(client, "0bb9ead9-c71a-43fa-8e80-35e5d566c15e");

		//client.writeCorpusDataToFile("data/clustering.csv", "", client::getAllDocumentsDataQuery, client::formatForClustering, new NERThrottle());
		//client.writeCorpusDataToFile("/code/aha_nlp/brian_analysis/non-sector-topic-modeling.data", client::writeTopicModellingHeader,null, "", client.getAdhocDataQuery("-sector:* AND parsed:*"), client::formatForTopicModeling, new NERThrottle());

		//client.runParsedUpdateJob("docText:* AND -parsed:*");
		//client.runLDACategoryUpdateJob("parsed:* AND -sector:* AND -ldaCategory:*", 0, 73000);
		//client.runDoccatCategoryUpdateJob("-sector:* AND parsed:* AND -doccatCategory:*", 0, 73000);
		//client.runFieldUpdateJob("ldaCategory:*", client::removeLDACategoryAttribute);
		//client.runFieldUpdateJob("doccatCategory:*", client::removeDoccatCategoryAttribute);
		//client.runFieldUpdateJob("doccatCategory:* OR ldaCategory:*", client::removeVerticalPipeFromCategory);
		//client.runFieldUpdateJob("percentAnnotated:[0 TO 100]", client::removeDocumentAnnotations);
		client.runEntityDetectionJob("parsed:* AND category:Water AND -includeInNERTesting:* AND -includeInNERTraining:*", 0, 52000, "Water", 0.45);
		//client.runEntityDetectionJob("id:ac2e3289278ac66ed1f91fd2fce589a837de2cfc", 0, 10, "Water", 0.4);
		//client.runFileListingJob("parsed:*", 0, 600000);

//		try {
//			logger.info("Begin dependency data export");
//			client.WriteDataToFile("data/depData.json", "filename:*", 1000000);
//			logger.info("Dependency data export complete");
//		} catch (SolrServerException e) {
//			e.printStackTrace();
//		}
	}

	public void runParsedUpdateJob(String strQuery) {
		Semaphore semaphore = new Semaphore(16);
		int rows = 1000;
		List<BatchSolrJob> jobs = new ArrayList<>();
		for (int start = 0; start <= 67000; start += rows) {
			SolrQuery query = new SolrQuery(strQuery);
			query.setStart(start);
			query.setRows(rows);
			BatchSolrJob job = new BatchSolrJob(query, semaphore, this::updateParsedAttribute, null, null);
			jobs.add(job);
			Thread thread = new Thread(job);
			job.setMyThread(thread);
			thread.start();
		}

		while (jobs.stream().anyMatch(p -> p.getMyThread().isAlive())) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {

			}
		}

		saveBatchSolrJobDocs(jobs);
	}

	public void runFieldUpdateJob(String strQuery, Tools.CheckedBiConsumer<SolrDocumentList, Object[]> updater) {
		Semaphore semaphore = new Semaphore(16);
		int rows = 1000;
		List<BatchSolrJob> jobs = new ArrayList<>();
		for (int start = 0; start <= 20000; start += rows) {
			SolrQuery query = new SolrQuery(strQuery);
			query.setStart(start);
			query.setRows(rows);
			BatchSolrJob job = new BatchSolrJob(query, semaphore, updater, null, null);
			jobs.add(job);
			Thread thread = new Thread(job);
			job.setMyThread(thread);
			thread.start();
		}

		while (jobs.stream().anyMatch(p -> p.getMyThread().isAlive())) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {

			}
		}

		saveBatchSolrJobDocs(jobs);
	}

	public void runLDACategoryUpdateJob(String strQuery, int queryStart, int queryEnd) {
		Semaphore semaphore = new Semaphore(16);
		TopicModeller topicModeller = new TopicModeller(Tools.getProperty("mallet.general"));
		int rows = 1000;
		List<BatchSolrJob> jobs = new ArrayList<>();
		for (int start = queryStart; start <= queryEnd; start += rows) {
			SolrQuery query = new SolrQuery(strQuery);
			query.setStart(start);
			query.setRows(rows);
			BatchSolrJob job = new BatchSolrJob(query, semaphore, this::updateLDACategoryAttribute, new Object[] {topicModeller}, null);
			jobs.add(job);
			Thread thread = new Thread(job);
			job.setMyThread(thread);
			thread.start();
		}

		while (jobs.stream().anyMatch(p -> p.getMyThread().isAlive())) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {

			}
		}

		saveBatchSolrJobDocs(jobs);
	}

	public void runDoccatCategoryUpdateJob(String strQuery, int queryStart, int queryEnd) {
		Semaphore semaphore = new Semaphore(16);
		DocumentCategorizer doccat = new DocumentCategorizer(this);
		int rows = 1000;
		List<BatchSolrJob> jobs = new ArrayList<>();
		for (int start = queryStart; start <= queryEnd; start += rows) {
			SolrQuery query = new SolrQuery(strQuery);
			query.setStart(start);
			query.setRows(rows);
			BatchSolrJob job = new BatchSolrJob(query, semaphore, this::updateDoccatCategoryAttribute, new Object[] {doccat}, null);
			jobs.add(job);
			Thread thread = new Thread(job);
			job.setMyThread(thread);
			thread.start();
		}

		while (jobs.stream().anyMatch(p -> p.getMyThread().isAlive())) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {

			}
		}

		saveBatchSolrJobDocs(jobs);
	}

	private void saveBatchSolrJobDocs(List<BatchSolrJob> jobs) {
		Semaphore semaphore = new Semaphore(16);
		List<SaveToSolrJob> saveJobs = new ArrayList<>();
		for (BatchSolrJob job : jobs) {
			if (!job.isEmptyJob()) {
				SaveToSolrJob saveJob = new SaveToSolrJob(semaphore, job);
				saveJobs.add(saveJob);
				Thread thread = new Thread(saveJob);
				saveJob.setMyThread(thread);
				thread.start();
			}
		}

		while (saveJobs.stream().anyMatch(p -> p.getMyThread().isAlive())) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {

			}
		}
	}

	private class SaveToSolrJob implements Runnable {
		private Semaphore semaphore;
		private Thread myThread;
		private BatchSolrJob job;

		public SaveToSolrJob(Semaphore semaphore, BatchSolrJob job) {
			this.semaphore = semaphore;
			this.job = job;
		}

		@Override
		public void run() {
			try {
				semaphore.acquire();
				logger.info("now saving batch of " + job.getQuery().getRows() + " documents starting at " + job.getQuery().getStart());
				indexDocuments(job.getDocs());
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			} finally {
				semaphore.release();
			}
		}

		public void setMyThread(Thread myThread) {
			this.myThread = myThread;
		}

		public Thread getMyThread() {
			return myThread;
		}
	}

	public void runFileListingJob(String strQuery, int queryStart, int queryEnd) {
		File fileListing = new File("data/fileListing/listing.txt");
		fileListing.getParentFile().mkdirs();
		Semaphore semaphore = new Semaphore(32);
		int rows = 10000;
		List<BatchSolrJob> jobs = new ArrayList<>();
		List<File> files = new ArrayList<>();
		for (int start = queryStart; start <= queryEnd; start += rows) {
			File batchFile = new File(fileListing.getPath() + "_" + start);
			files.add(batchFile);
			Object[] args = new Object[] {batchFile, (Tools.CheckedTriConsumer<Object[], SolrDocument, OutputStreamWriter>)this::formatForFilenameListing, new NERThrottle(), null};
			SolrQuery query = new SolrQuery(strQuery);
			query.setStart(start);
			query.setRows(rows);
			BatchSolrJob job = new BatchSolrJob(query, semaphore, this::writeCorpusDocsToFile, args, null);
			jobs.add(job);
			Thread thread = new Thread(job);
			job.setMyThread(thread);
			thread.start();
		}

		while (jobs.stream().anyMatch(p -> p.getMyThread().isAlive())) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {

			}
		}
	}

	public void runEntityDetectionJob(String strQuery, int queryStart, int queryEnd, String category, double threshold) {
		File fileEntities = new File("data/file_entities.txt");
		fileEntities.getParentFile().mkdirs();
		Semaphore semaphore = new Semaphore(12);
		NamedEntityRecognizer recognizer = new NamedEntityRecognizer(this);
		int rows = 1000;
		List<BatchSolrJob> jobs = new ArrayList<>();
		List<File> files = new ArrayList<>();
		long[] increment = new long[3];
		for (int start = queryStart; start <= queryEnd; start += rows) {
			File batchFile = new File(fileEntities.getPath() + "_" + start);
			files.add(batchFile);
			Object[] args = new Object[] {batchFile, (Tools.CheckedTriConsumer<Object[], SolrDocument, OutputStreamWriter>)this::formatForNamedEntitiesReport, new NERThrottle(), category, increment, new Object[] {recognizer, threshold}};
			SolrQuery query = new SolrQuery(strQuery);
			query.setStart(start);
			query.setRows(rows);
			BatchSolrJob job = new BatchSolrJob(query, semaphore, this::writeCorpusDocsToFile, args, null);
			jobs.add(job);
			Thread thread = new Thread(job);
			job.setMyThread(thread);
			thread.start();
		}

		while (jobs.stream().anyMatch(p -> p.getMyThread().isAlive())) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {

			}
		}

		try (FileOutputStream fos = new FileOutputStream(fileEntities);
			 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)){
			Tools.mergeFiles(files, writer);
			writer.close();
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	private class BatchSolrJob implements Runnable {
		private SolrQuery query;
		private boolean emptyJob;
		private Tools.CheckedBiConsumer<SolrDocumentList, Object[]> consumer;
		private Tools.CheckedConsumer<SolrDocumentList> postConsumer;
		private Object[] args;
		private Semaphore semaphore;
		private Thread myThread;
		private SolrDocumentList docs;

		public BatchSolrJob(SolrQuery query, Semaphore semaphore, Tools.CheckedBiConsumer<SolrDocumentList, Object[]> consumer, Object[] args, Tools.CheckedConsumer<SolrDocumentList> postConsumer) {
			this.query = query;
			emptyJob = false;
			this.consumer = consumer;
			this.postConsumer = postConsumer;
			this.args = args;
			this.semaphore = semaphore;
		}

		public void run() {
			try {
				semaphore.acquire();
				docs = client.query(collection, query).getResults();
				if (docs.size() == 0) {
					emptyJob = true;
					logger.info("batch of " + query.getRows() + " documents starting at " + query.getStart() + " is EMPTY!");
					return;
				}
				consumer.apply(docs, args);
				if (postConsumer != null) {
					postConsumer.apply(docs);
				}
				logger.info("processed batch of " + query.getRows() + " documents starting at " + query.getStart());
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			} finally {
				semaphore.release();
			}
		}

		public SolrQuery getQuery() {
			return query;
		}

		public boolean isEmptyJob() {
			return emptyJob;
		}

		public Thread getMyThread() {
			return myThread;
		}

		public void setMyThread(Thread myThread) {
			this.myThread = myThread;
		}

		public SolrDocumentList getDocs() {
			return docs;
		}
	}

	private void updateParsedAttribute(SolrDocumentList docs, Object[] notUsed) {
		for (SolrDocument doc : docs) {
			String docText = doc.get("docText").toString();
			String parsed = NLPTools.deepCleanText(docText);
			parsed = NLPTools.redactTextForNLP(NLPTools.detectPOSStanford(parsed), 0.7, 1000);
			doc.replace("parsed", parsed);
			doc.replace("lastUpdated", Tools.getFormattedDateTimeString(Instant.now()));
		}
	}

	private void removeLDACategoryAttribute(SolrDocumentList docs, Object[] notUsed) {
		for (SolrDocument doc : docs) {
			if (doc.containsKey("ldaCategory")) {
				doc.remove("ldaCategory");
				logger.info(doc.get("filename").toString() + " LDA category removed");
			}
		}
	}

	private void removeDoccatCategoryAttribute(SolrDocumentList docs, Object[] notUsed) {
		for (SolrDocument doc : docs) {
			if (doc.containsKey("doccatCategory")) {
				doc.remove("doccatCategory");
				logger.info(doc.get("filename").toString() + " DocCat category removed");
			}
		}
	}

	private void removeDocumentAnnotations(SolrDocumentList docs, Object[] notUsed) {
		for (SolrDocument doc : docs) {
			if (doc.containsKey("annotated")) {
				doc.remove("annotated");
			}
			if (doc.containsKey("includeInNERTraining")) {
				doc.remove("includeInNERTraining");
			}
			if (doc.containsKey("includeInNERTesting")) {
				doc.remove("includeInNERTesting");
			}
			if (doc.containsKey("percentAnnotated")) {
				doc.remove("percentAnnotated");
			}
			logger.info(doc.get("filename").toString() + " annotations removed");
		}
	}

	private void removeVerticalPipeFromCategory(SolrDocumentList docs, Object[] args) {
		for (SolrDocument doc : docs) {
			if (doc.containsKey("ldaCategory")) {
				List<String> ldaCategories = (List<String>)doc.get("ldaCategory");
				List<String> corrected = ldaCategories.stream()
						.map(p -> p.replace("|", " "))
						.collect(Collectors.toList());
				doc.replace("ldaCategory", corrected);
			}
			if (doc.containsKey("doccatCategory")) {
				List<String> doccatCategories = (List<String>)doc.get("doccatCategory");
				List<String> corrected = doccatCategories.stream()
						.map(p -> p.replace("|", " "))
						.collect(Collectors.toList());
				doc.replace("doccatCategory", corrected);
			}
			logger.info(doc.get("filename").toString() + " categories fixed");
		}
	}

	private void updateLDACategoryAttribute(SolrDocumentList docs, Object[] args) {
		TopicModeller topicModeller = (TopicModeller)args[0];
		for (SolrDocument doc : docs) {
			String parsed = doc.get("parsed").toString();
			List<String> ldaCategories = topicModeller.inferCategoriesByTopics(parsed);
			if (ldaCategories != null) {
				List<String> categories = NLPTools.removeProbabilitiesFromCategories(ldaCategories);
				if (doc.containsKey("ldaCategory")) {
					doc.replace("ldaCategory", ldaCategories);
				} else {
					doc.put("ldaCategory", ldaCategories);
				}
				if (!doc.containsKey("userCategory")) {
					if (doc.containsKey("category")) {
						doc.replace("category", categories);
					} else {
						doc.put("category", categories);
					}
				}
				logger.info(doc.get("filename").toString() + " [" + parsed.length() + "] --> " + ldaCategories.stream().reduce((c, n) -> c + ", " + n).orElse(""));
			} else {
				logger.error(doc.get("filename").toString() + " --> ERROR GENERATING CATEGORY!!");
			}
		}
	}

	private void updateDoccatCategoryAttribute(SolrDocumentList docs, Object[] args) {
		DocumentCategorizer doccat = (DocumentCategorizer)args[0];
		for (SolrDocument doc : docs) {
			if (doc.containsKey("parsed")) {
				String parsed = doc.get("parsed").toString();
				List<String> doccatCategories = null;
				try {
					doccatCategories = doccat.detectBestCategories(parsed, 0);
				} catch (IOException e) { }
				if (doccatCategories != null) {
					List<String> categories = NLPTools.removeProbabilitiesFromCategories(doccatCategories);
					if (doc.containsKey("doccatCategory")) {
						doc.replace("doccatCategory", doccatCategories);
					} else {
						doc.put("doccatCategory", doccatCategories);
					}
					if (!doc.containsKey("userCategory")) {
						if (doc.containsKey("category")) {
							doc.replace("category", categories);
						} else {
							doc.put("category", categories);
						}
					}
					logger.info(doc.get("filename").toString() + " [" + parsed.length() + "] --> " + doccatCategories.stream().reduce((c, n) -> c + ", " + n).orElse(""));
				} else {
					logger.error(doc.get("filename").toString() + " --> ERROR GENERATING CATEGORY!!");
				}
			} else {
				logger.info("no parsed data!");
			}
		}
	}

	private void removeCopyFieldsAndVersion(SolrDocument doc) {
		if (doc.containsKey("_version_")) {
			doc.remove("_version_");
		}
		if (doc.containsKey("filename_str")) {
			doc.remove("filename_str");
		}
		if (doc.containsKey("url_str")) {
			doc.remove("url_str");
		}
		if (doc.containsKey("project_str")) {
			doc.remove("project_str");
		}
		if (doc.containsKey("organization_str")) {
			doc.remove("organization_str");
		}
	}

	public void setTopicModeller(TopicModeller topicModeller) {
		this.topicModeller = topicModeller;
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
								try {
									long number = Long.parseLong(value);
									doc.put(field, number);
								} catch (NumberFormatException e) {
									logger.error(e.getMessage(), e);
								}
							} else {
								doc.put(field, value);
							}
						}
					}
					docs.add(doc);
					if (docNum % 1000 == 0) {
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
					removeCopyFieldsAndVersion(doc);
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

	public SolrQuery getAllDocumentsDataQuery(SolrQuery query) {
		query.setQuery("parsed:*");
		return query;
	}

	public SolrQuery getDoccatDataQuery(SolrQuery query) {
		query.setQuery("parsed:* AND (ldaCategory:* OR userCategory:*)");
		//query.setFilterQueries("{!frange l=1}ms(lastUpdated,created) ");
		return query;
	}

	public static Function<SolrQuery, SolrQuery> getAdhocDataQuery(final String queryStr) {
        Function<SolrQuery, SolrQuery> func = query -> {
            query.setQuery(queryStr);
            return query;
        };
        return func;
    }

	public static Function<SolrQuery, SolrQuery> getCategorySpecificDataQuery(final String category) {
		Function<SolrQuery, SolrQuery> func = query -> {
			query.setQuery("category:" + category);
			return query;
		};
		return func;
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

	public void formatForNamedEntitiesReport(Object[] args, SolrDocument doc, OutputStreamWriter writer) throws IOException {
		List<String> category = new ArrayList<String>();
		category.add(args[0].toString());
		Object[] otherArgs = (Object[])args[3];
		NamedEntityRecognizer recognizer = (NamedEntityRecognizer)otherArgs[0];
		double threshold = (double)otherArgs[1];
		if (doc.containsKey("parsed") && doc.containsKey("category")) {
			String parsed = doc.get("parsed").toString();
			List<CoreMap> sentences = NLPTools.detectSentencesStanford(parsed);
			List<NamedEntity> entities = recognizer.detectNamedEntities(sentences, category, threshold);
			int count = entities.size();
			String filename = doc.get("filename").toString();
			writer.write(count + "\t" + filename);
			writer.write(System.lineSeparator());
			writer.flush();
			logger.info("Found " + count + " entities for " + filename);
		}
	}

	public void formatForFilenameListing(Object[] args, SolrDocument doc, OutputStreamWriter writer) throws IOException {
		String filename = doc.get("filename").toString();
		writer.write(filename);
		writer.write(System.lineSeparator());
		writer.flush();
	}


	public void formatForCompleteDocumentOutput(Object[] args, SolrDocument doc, OutputStreamWriter writer) throws IOException {
		removeCopyFieldsAndVersion(doc);
		String output = objWriter.writeValueAsString(doc);
		writer.write(output);
		Boolean lastElement = (Boolean)args[1];
		if (!lastElement) {
			writer.write(", ");
		}
		writer.write(System.lineSeparator());
		writer.flush();
	}

	public void formatForWord2VecModelTraining(Object[] args, SolrDocument doc, OutputStreamWriter writer) throws IOException {
		String parsed = doc.get("parsed").toString();
		parsed = parsed.replace("\r", " ").replace("\n", " ");
		writer.write(parsed);
		writer.write(System.lineSeparator());
		writer.flush();
	}

	public void formatForDoccatModelTraining(Object[] args, SolrDocument doc, OutputStreamWriter writer) throws IOException {
		String parsed = doc.get("parsed").toString();
		String normalized = NLPTools.normalizeText(parsed);
		List<String> categories = (List<String>)doc.get("category");
		String output = null;
		if (categories.size() == 1 || doc.containsKey("userCategory")) {
			if (!Strings.isNullOrEmpty(normalized) && normalized.trim().length() > 0) {
				output = categories.stream()
						.map(category -> category + "\t" + normalized)
						.reduce((p1, p2) -> p1 + System.lineSeparator() + p2)
						.orElse("");
			}
		} else {
			List<String> probCategories = null;
			if (doc.containsKey("ldaCategory")) {
				probCategories = (List<String>)doc.get("ldaCategory");

			} else if (doc.containsKey("doccatCategory")) {
				probCategories = (List<String>)doc.get("doccatCategory");
			}
			if (probCategories != null) {
				List<CategoryWeight> catWeights = NLPTools.separateProbabilitiesFromCategories(probCategories);
				double maxCat = catWeights.stream().mapToDouble(p -> p.catWeight).max().getAsDouble();
				CategoryWeight categoryWeight = catWeights.stream().filter(p -> p.catWeight == maxCat).collect(Collectors.toList()).get(0);
				output = categoryWeight.category + "\t" + normalized;
			}

//				if (topicModeller != null) {
//					List<TextChunkTopic> chunks = topicModeller.getTextChunkLDACategories(parsed, 50);
//					chunks.stream().forEach(p -> p.setLdaCategory(NLPTools.removeProbabilitiesFromCategories(p.getLdaCategory())));
//					chunks.stream().forEach(p -> p.setChunkText(NLPTools.normalizeText(p.getChunkText())));
//					output = chunks.stream()
//							.filter(p -> !Strings.isNullOrEmpty(p.getChunkText()) && p.getChunkText().trim().length() > 0)
//							.map(p -> p.getLdaCategory().get(0) + "\t" + p.getChunkText())
//							.reduce((p1, p2) -> p1 + System.lineSeparator() + p2)
//							.orElse("");
//				}
		}

		if (!Strings.isNullOrEmpty(output)) {
			writer.write(output);
			writer.write(System.lineSeparator());
		}
	}

	public void formatForNERCorpusReview(Object[] args, SolrDocument doc, OutputStreamWriter writer) throws IOException {
		String filename = doc.get("filename").toString();
		String separator = "***********************************************************";
		writer.write(separator);
		writer.write(filename.toUpperCase());
		writer.write(separator);
		writer.write(System.lineSeparator());
		writer.write(System.lineSeparator());
		writer.flush();
		formatForNERModelTraining(args, doc, writer);
	}

	public void formatForNERModelTraining(Object[] args, SolrDocument doc, OutputStreamWriter writer) throws IOException {
		String category = (String)args[0];
		//get the facility types that are valid for the given category
		final List<String> facilityTypes = FacilityTypes.dictionary.get(category);
		String annotated = doc.get("annotated").toString();
		boolean testing = Boolean.parseBoolean(doc.get("includeInNERTesting").toString());
		List<NamedEntity> entities = NLPTools.extractNamedEntities(annotated);

		Map<String, List<NamedEntity>> typeEntities = entities.stream()
				.collect(Collectors.groupingBy(p -> p.getSpan().getType()));

		List<CoreMap> sentencesList = NLPTools.detectSentencesStanford(annotated);
		String[] sentences = sentencesList.stream().map(p -> p.toString()).toArray(String[]::new);

		List<String> annotatedLinesForCategory = new ArrayList<>();

		for (String facilityType : facilityTypes) {
			if (typeEntities.containsKey(facilityType)) {
				Map<Integer, List<NamedEntity>> lineEntities = typeEntities.get(facilityType).stream()
						.collect(Collectors.groupingBy(p -> p.getLine()));

				annotatedLinesForCategory.add(NLPTools.NER_TRAINING_DATA_TYPE_DELIMITER);
				for (int s = 0; s < sentences.length; s++) {
					String sentence = sentences[s];
					//first remove all annotations to ensure that only category/type-specific annotations are included in this segment of the training data
					sentence = sentence.replaceAll("(?<=\\S\\s)<START:.+?> ", "")
							.replaceAll("\\s?<START:.+?> ", "")
							.replaceAll(" <END>  ?(\\b|\\B)", " ")
							.replaceAll(" <END> ", "");
					if (lineEntities.containsKey(s)) {
						String annotatedLine = NLPTools.autoAnnotateSentence(sentence, lineEntities.get(s));
						String formattedLine = NLPTools.fixFormattingForModelTraining(annotatedLine);

						annotatedLinesForCategory.add(formattedLine);
					} else if (testing) {
						annotatedLinesForCategory.add(sentence);
					}
				}
			}
		}

		int size = annotatedLinesForCategory.size();
		if (size > 0) {
			String lastEntry = annotatedLinesForCategory.get(size - 1);
			if (!lastEntry.equals(System.lineSeparator())) {
				annotatedLinesForCategory.add(System.lineSeparator());
			}
		}

		if (annotatedLinesForCategory.size() > 0) {
			String onlyAnnotated = String.join("\r\n", annotatedLinesForCategory);
			writer.write(onlyAnnotated);
			writer.write(System.lineSeparator());
			writer.write(System.lineSeparator());
			writer.flush();
		}
	}

	public void formatForClustering(Object[] args, SolrDocument doc, OutputStreamWriter writer) throws IOException {
		String id = doc.get("id").toString();
		String filename = doc.get("filename").toString();
		String parsed = doc.get("parsed").toString();
		String category = doc.containsKey("category") ? doc.get("category").toString() : "NONE";
		String clusteringStr = id + "," + filename.replace(",", "") + "_<" + category.replace(",", ";") + ">" + "," + parsed.replace(",", "");
		clusteringStr = clusteringStr.replace("\r", " ")
				.replace("\n", " ");
		writer.write(clusteringStr);
		writer.write(System.lineSeparator());
		writer.flush();
	}

	public void formatForTopicModeling(Object[] args, SolrDocument doc, OutputStreamWriter writer) throws IOException {
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
		writer.flush();
	}

	public void writeTopicModellingHeader(OutputStreamWriter writer) throws IOException {
		writer.write("id,filename,parsed" + System.lineSeparator());
	}

	public void writeAllDocumentsHeader(OutputStreamWriter writer) throws IOException {
		writer.write("[" + System.lineSeparator());
	}

	public void writeAllDocumentsFooter(OutputStreamWriter writer) throws IOException {
		writer.write("]");
	}

	public void WriteDataToFile(String filePath, String queryStr, int rows, String... filterQueries) throws SolrServerException {
		SolrDocumentList docs = QuerySolrDocuments(queryStr, rows, 0, null, null, filterQueries);
		File file = new File(filePath);
		try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)){
			file.getParentFile().mkdirs();
			writer.write("[ ");
			logger.info(docs.size() + " documents to export...");
			for (int i = 0; i < docs.size(); i++) {
				SolrDocument doc = docs.get(i);
				removeCopyFieldsAndVersion(doc);
				String output = objWriter.writeValueAsString(doc);
				writer.write(output);
				if (i != docs.size() - 1) {
					writer.write(", ");
					writer.write(System.lineSeparator());
				}
				writer.flush();
				if (i % 100 == 0) {
					logger.info("Exported so far: " + i + " documents");
				}
			}
			writer.write("]");
			writer.flush();
			writer.close();
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	private void massModifyCorpus(Function<SolrQuery, SolrQuery> queryGetter, String field, Object value) {
		SolrQuery query = queryGetter.apply(new SolrQuery());
		query.setRows(1000000);
		SolrDocumentList docBuffer = new SolrDocumentList();
		final BlockingQueue<SolrDocument> tmpQueue = new LinkedBlockingQueue<SolrDocument>();
		try {
			client.queryAndStreamResponse(collection, query, new CallbackHandler(tmpQueue));

			SolrDocument tmpDoc;
			long docCount = 0;
			do {
                tmpDoc = tmpQueue.take();
                if (!(tmpDoc instanceof StopDoc)) {
                    ++docCount;
                    if (tmpDoc.containsKey(field)) {
                        tmpDoc.replace(field, value);
                    } else {
                        tmpDoc.put(field, value);
                    }
                    docBuffer.add(tmpDoc);
                }
                if (docBuffer.size() >= 100) {
                    indexDocuments(docBuffer);
                    docBuffer.clear();
                    logger.info("Updated " + docCount + " documents");
                }
            } while (!(tmpDoc instanceof StopDoc));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void writeCorpusDocsToFile(SolrDocumentList docs, Object[] args) throws Exception {
		File file = (File)args[0];
		Tools.CheckedTriConsumer<Object[], SolrDocument, OutputStreamWriter> dataFormatter = (Tools.CheckedTriConsumer<Object[], SolrDocument, OutputStreamWriter>)args[1];
		TrainingDataThrottle throttle = (TrainingDataThrottle)args[2];
		String category = (String)args[3];
		Boolean lastElement = false;
		long[] increment = (long[])args[4];
		long start = increment[0];
		long end = increment[1];
		long span = increment[2];
		Object[] otherArgs = null;
		if (args.length > 5) {
			otherArgs = (Object[])args[5];
		}
		Object[] formatterArgs = new Object[] {category, lastElement, throttle, otherArgs};
		try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)){
			for (int i = 0; i < docs.size(); i++) {
				SolrDocument doc = docs.get(i);
				if (throttle.check(doc)) {
					//detect if this is the final document across all batches
					if (i == docs.size() - 1 && start + span > end) {
						formatterArgs[1] = true;
					}
					dataFormatter.apply(formatterArgs, doc, writer);
				}
			}

			writer.close();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}

	}

	public long getNumDocsFound(Function<SolrQuery, SolrQuery> queryGetter) throws SolrServerException, IOException {
		SolrQuery initQuery = queryGetter.apply(new SolrQuery());
		initQuery.setRows(0);
		SolrDocumentList results = client.query(collection, initQuery).getResults();
		long numFound = results.getNumFound();
		return numFound;
	}

	public void writeCorpusDataToFile(String outputPath, Tools.CheckedConsumer<OutputStreamWriter> header, Tools.CheckedConsumer<OutputStreamWriter> footer, String category, Function<SolrQuery, SolrQuery> queryGetter,
											  Tools.CheckedTriConsumer<Object[], SolrDocument, OutputStreamWriter> dataFormatter, TrainingDataThrottle throttle) {
		int rows = 10000;
		File file = new File(outputPath);
		file.getParentFile().mkdirs();

		try (FileOutputStream fos = new FileOutputStream(file);
			 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)){
			if (header != null) {
				header.apply(writer);
			}

			long numFound = getNumDocsFound(queryGetter);
			throttle.init(numFound);

			Semaphore semaphore = new Semaphore(4);
			List<BatchSolrJob> jobs = new ArrayList<>();
			List<File> files = new ArrayList<>();
			for (int start = 0; start < numFound; start += rows) {
				File batchFile = new File(outputPath + "_" + start);
				files.add(batchFile);
				long[] increment = {start, numFound, rows};
				Object[] args = new Object[] {batchFile, dataFormatter, throttle, category, increment};
				SolrQuery query = queryGetter.apply(new SolrQuery());
				query.setRows(rows);
				query.setStart(start);

				BatchSolrJob job = new BatchSolrJob(query, semaphore, this::writeCorpusDocsToFile, args, null);
				jobs.add(job);
				Thread thread = new Thread(job);
				job.setMyThread(thread);
				thread.start();
			}

			while (jobs.stream().anyMatch(p -> p.getMyThread().isAlive())) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {

				}
			}

			Tools.mergeFiles(files, writer);
			if (footer != null) {
				footer.apply(writer);
			}

			writer.close();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
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


}
