package solrapi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.stream.Collectors;

import common.FacilityTypes;
import common.Tools;
import edu.stanford.nlp.util.CoreMap;
import nlp.NLPTools;
import nlp.NamedEntity;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.SortClause;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.StreamingResponseCallback;
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
import com.google.common.io.Files;

public class SolrClient {

	private final static String COLLECTION = "dependencies";
	
	final static Logger logger = LogManager.getLogger(SolrClient.class);

	private HttpSolrClient client;
	private static ObjectMapper mapper = new ObjectMapper();
	
	public SolrClient(String solrHostURL) {
		client = new HttpSolrClient.Builder(solrHostURL).build();
	}
	
	public static void main(String[] args) {
		SolrClient client = new SolrClient("http://localhost:8983/solr");
		//client.writeTrainingDataToFile(Tools.getProperty("nlp.waterNerTrainingFile"), client::getWaterDataQuery, client::formatForNERModelTraining);

		//retrieveAnnotatedData(client, "0bb9ead9-c71a-43fa-8e80-35e5d566c15e");
		//updateAnnotatedData(client, "0bb9ead9-c71a-43fa-8e80-35e5d566c15e");

		//client.writeTrainingDataToFile("data/clustering.csv", client::getDoccatDataQuery, client::formatForClustering);

		try {
			logger.info("Begin dependency data export");
			client.WriteDataToFile("data/depData.json", "filename:*", 1000000);
			logger.info("Dependency data export complete");
		} catch (SolrServerException e) {
			e.printStackTrace();
		}
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
		String file = Tools.GetFileString(filePath, "Cp1252");
		try {
			SolrDocument[] docs = mapper.readValue(file, SolrDocument[].class);
			List<SolrDocument> docsList = Arrays.asList(docs);
			for (SolrDocument doc : docsList) {
				String created = Tools.getFormattedDateTimeString(Instant.ofEpochMilli((long)doc.get("created")));
				String lastUpdated = Tools.getFormattedDateTimeString(Instant.ofEpochMilli((long)doc.get("lastUpdated")));
				doc.replace("created", created);
				doc.replace("lastUpdated", lastUpdated);
			}
			indexDocuments(docsList);
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

				client.add(COLLECTION, inputDocuments);
				UpdateResponse updateResponse = client.commit(COLLECTION);
				
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
			client.deleteByQuery(COLLECTION, query);
			UpdateResponse updateResponse = client.commit(COLLECTION);

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
			QueryResponse response = client.query(COLLECTION, query);
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
			QueryResponse response = client.query(COLLECTION, query);
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
			SolrDocumentList response = client.query(COLLECTION, query).getResults();
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
			SolrDocumentList response = client.query(COLLECTION, query).getResults();
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
			SolrDocumentList response = client.query(COLLECTION, query).getResults();
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

	public SolrQuery getDoccatDataQuery(SolrQuery query) {
		query.setQuery("parsed:*");

		return query;
	}

	public static Function<SolrQuery, SolrQuery> getCategorySpecificDataQuery(final String category) {
		Function<SolrQuery, SolrQuery> func = query -> {
			query.setQuery("annotated:* AND category:" + category);

			return query;
		};
		return func;
	}

	public static SolrQuery getWaterDataQuery(SolrQuery query) {
		query.setQuery("annotated:* AND category:Water");

		return query;
	}

	public static SolrQuery getWastewaterDataQuery(SolrQuery query) {
		query.setQuery("annotated:* AND category:Wastewater_System");

		return query;
	}

	public static SolrQuery getRecycledWaterDataQuery(SolrQuery query) {
		query.setQuery("annotated:* AND category:Recycled_Water_System");

		return query;
	}

	public static SolrQuery getElectricityDataQuery(SolrQuery query) {
		query.setQuery("annotated:* AND category:Electricity");

		return query;
	}

	public static SolrQuery getPetroleumDataQuery(SolrQuery query) {
		query.setQuery("annotated:* AND category:Petroleum");

		return query;
	}

	public static SolrQuery getNaturalGasDataQuery(SolrQuery query) {
		query.setQuery("annotated:* AND category:Natural_Gas");

		return query;
	}

	public void formatForDoccatModelTraining(String unused, SolrDocument doc, FileOutputStream fos) throws IOException {
		String parsed = doc.get("parsed").toString();
		String normalized = NLPTools.normalizeText(parsed);
		List<String> categories = (List<String>)doc.get("category");
		String output = categories.stream().map(category -> category + "\t" + normalized).reduce((p1, p2) -> p1 + System.lineSeparator() + p2).orElse("");
		fos.write(output.getBytes(Charset.forName("Cp1252")));
	}

	public void formatForNERModelTraining(String category, SolrDocument doc, FileOutputStream fos) throws IOException {
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
						sentence = sentence.replaceAll(" ?<START:.+?> ", "").replaceAll(" <END> ", "");
						List<NamedEntity> validLineEntities = lineEntities.get(s).stream()
								.filter(p -> facilityTypes.contains(p.getSpan().getType()))
								.collect(Collectors.toList());
						String annotatedLine = NLPTools.autoAnnotateSentence(sentence, validLineEntities);
						String formattedLine = NLPTools.fixFormattingAfterAnnotation(annotatedLine);

						annotatedLinesForCategory.add(formattedLine);
						break;
					}
				}
			}
		}

		//List<String> annotatedLines = Arrays.stream(sentences).filter(p -> p.contains("<START:")).collect(Collectors.toList());

		if (annotatedLinesForCategory.size() > 0) {
			String onlyAnnotated = String.join("\r\n", annotatedLinesForCategory);
			fos.write(onlyAnnotated.getBytes(Charset.forName("Cp1252")));
			fos.write(System.lineSeparator().getBytes());
		}
	}

	public void formatForClustering(String unused, SolrDocument doc, FileOutputStream fos) throws IOException {
		String id = doc.get("id").toString();
		String filename = doc.get("filename").toString();
		String parsed = doc.get("parsed").toString();
		String clusteringStr = id + "," + filename.replace(",", "") + "," + parsed.replace(",", "");
		clusteringStr = clusteringStr.replace("\r", " ")
				.replace("\n", " ");
		fos.write(clusteringStr.getBytes(Charset.forName("Cp1252")));
	}

	public void WriteDataToFile(String filePath, String queryStr, int rows, String... filterQueries) throws SolrServerException {
		ObjectWriter writer = new ObjectMapper().writer().withDefaultPrettyPrinter();
		SolrDocumentList docs = QuerySolrDocuments(queryStr, rows, 0, null, null, filterQueries);
		try {
			String output = writer.writeValueAsString(docs);
			File file = new File(filePath);
			file.getParentFile().mkdirs();
			Files.write(output, file, Charset.forName("Cp1252"));
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public void writeTrainingDataToFile(String trainingFilePath, String category, Function<SolrQuery, SolrQuery> queryGetter,
										Tools.CheckedTriConsumer<String, SolrDocument, FileOutputStream> consumer) {
		SolrQuery query = queryGetter.apply(new SolrQuery());
		query.setRows(1000000);
		try {
			File file = new File(trainingFilePath);
			file.getParentFile().mkdirs();
			FileOutputStream fos = new FileOutputStream(file);
			final BlockingQueue<SolrDocument> tmpQueue = new LinkedBlockingQueue<SolrDocument>();
			client.queryAndStreamResponse(COLLECTION, query, new CallbackHandler(tmpQueue));

			SolrDocument tmpDoc;
			do {
				tmpDoc = tmpQueue.take();
				if (!(tmpDoc instanceof StopDoc)) {
					consumer.apply(category, tmpDoc, fos);
					fos.write(System.lineSeparator().getBytes());
				}
			} while (!(tmpDoc instanceof StopDoc));

			fos.close();
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
