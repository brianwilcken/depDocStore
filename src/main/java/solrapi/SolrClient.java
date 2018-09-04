package solrapi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.stream.Collectors;

//import org.apache.commons.codec.binary.Hex;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.google.common.base.Strings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.SortClause;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.StreamingResponseCallback;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MoreLikeThisParams;
import org.apache.solr.common.util.SimpleOrderedMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.io.Files;

import common.Tools;
import solrapi.model.AnalyzedEvent;
import solrapi.model.IndexedEventSource;
import solrapi.model.IndexedEvent;

public class SolrClient {

	final static Logger logger = LogManager.getLogger(SolrClient.class);

	private HttpSolrClient client;
	private static ObjectMapper mapper = new ObjectMapper();
	
	public SolrClient(String solrHostURL) {
		client = new HttpSolrClient.Builder(solrHostURL).build();
	}
	
	public static void main(String[] args) {
		SolrClient solrClient = new SolrClient("http://localhost:8983/solr");
		//solrClient.writeTrainingDataToFile("data/analyzed-events.csv", solrClient::getAnalyzedDataQuery, solrClient::formatForClustering, new ClusteringThrottle("", 0));
		try {
			solrClient.WriteEventDataToFile("data/all-model-training-events.json", "eventState:* AND concepts:*", 10000);
			//solrClient.UpdateIndexedEventsFromJsonFile("data/solrData.json");
			//solrClient.UpdateIndexedEventsWithAnalyzedEvents("data/doc_clusters.csv");
		} catch (SolrServerException e) {
			e.printStackTrace();
		}
	}
	
	public void UpdateIndexedArticlesFromFile(String filePath) throws SolrServerException {
		String file = Tools.GetFileString(filePath, "Cp1252");
		try {
			IndexedEventSource[] articles = mapper.readValue(file, IndexedEventSource[].class);
			indexDocuments(Arrays.asList(articles));
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	public void UpdateIndexedEventsFromJsonFile(String filePath) throws SolrServerException {
		String file = Tools.GetFileString(filePath, "Cp1252");
		try {
			IndexedEvent[] events = mapper.readValue(file, IndexedEvent[].class);
			for (IndexedEvent event : events) {
				event.updateLastUpdatedDate();
			}
			indexDocuments(Arrays.asList(events));
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public List<AnalyzedEvent> CollectAnalyzedEventsFromCSV(String filePath) {
		String file = Tools.GetFileString(filePath, "Cp1252");
		try {
			MappingIterator<AnalyzedEvent> eventIter = new CsvMapper().readerWithTypedSchemaFor(AnalyzedEvent.class).readValues(file);
			List<AnalyzedEvent> events = eventIter.readAll();
			List<AnalyzedEvent> analyzedEvents = events.stream()
					.filter(p -> !Strings.isNullOrEmpty(p.category))
					.collect(Collectors.toList());

			return analyzedEvents;
		} catch (IOException e) {
			return null;
		}
	}

	public List<IndexedEvent> CollectIndexedEventsFromAnalyzedEvents(List<AnalyzedEvent> analyzedEvents) throws SolrServerException {
		List<String> ids = analyzedEvents.stream()
				.map(p -> p.id)
				.collect(Collectors.toList());

		List<IndexedEvent> indexedEvents = new ArrayList<>();

		for (int i = 0; i < ids.size(); i++) {
			String id = ids.get(i);
			indexedEvents.addAll(QueryIndexedDocuments(IndexedEvent.class, "id:" + id, 1, 0, null));
		}

		return indexedEvents;
	}

	public void UpdateIndexedEventsWithAnalyzedEvents(String filePath) throws SolrServerException {
		List<AnalyzedEvent> analyzedEvents = CollectAnalyzedEventsFromCSV(filePath);
		List<IndexedEvent> indexedEvents = CollectIndexedEventsFromAnalyzedEvents(analyzedEvents);

		List<IndexedEvent> indexReadyEvents = new ArrayList<>();

		int totalDocs = analyzedEvents.size();
		logger.info("Total documents to process: " + totalDocs);
		for (int i = 0; i < totalDocs; i++) {
			AnalyzedEvent analyzedEvent = analyzedEvents.get(i);
			Optional<IndexedEvent> maybeEvent = indexedEvents.stream().filter(p -> p.getId().compareTo(analyzedEvent.id) == 0).findFirst();
			if (maybeEvent.isPresent()) {
				IndexedEvent indexedEvent = maybeEvent.get();
				indexedEvent.setCategory(analyzedEvent.category);
				indexedEvent.setCategorizationState(SolrConstants.Events.CATEGORIZATION_STATE_USER_UPDATED);
				indexedEvent.setEventState(SolrConstants.Events.EVENT_STATE_ANALYZED);
				indexedEvent.updateLastUpdatedDate();

				indexReadyEvents.add(indexedEvent);
			}
			logger.info("Processed document: #" + (i + 1) + " of " + totalDocs);
		}

		indexDocuments(indexReadyEvents);
	}
	
	public <T> void indexDocuments(Collection<T> docs) throws SolrServerException {
		try {
			if (!docs.isEmpty()) {
				client.addBeans("events", docs);
				UpdateResponse updateResponse = client.commit("events");
				
				if (updateResponse.getStatus() != 0) {
					//TODO What should happen if the update fails?
				}
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public void deleteDocuments(String query) throws SolrServerException {
	    try {
			client.deleteByQuery("events", query);
			UpdateResponse updateResponse = client.commit("events");

            if (updateResponse.getStatus() != 0) {
                //TODO What should happen if the update fails?
            }
        } catch (IOException e) {
			logger.error(e.getMessage(), e);
        }
    }

	public List<IndexedEvent> GetIndexableEvents(List<IndexedEvent> events) throws SolrServerException {
		List<SolrServerException> exs = new ArrayList<SolrServerException>();
		List<IndexedEvent> indexableEvents = events.stream()
				.filter(p -> {
					try {
						return !DocumentExistsByURI(p.getUri());
					} catch (SolrServerException e) {
						exs.add(e);
						return false;
					}
				})
				.collect(Collectors.toList());

		if (!exs.isEmpty()) {
			throw exs.get(0);
		}

		return indexableEvents;
	}

	public Boolean DocumentExists(String queryStr) throws SolrServerException {
		SolrQuery query = new SolrQuery();
		query.setRows(0);
		query.setQuery(queryStr);
		try {
			QueryResponse response = client.query("events", query);
			return response.getResults().getNumFound() > 0;
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			return false;
		}
	}

	public Boolean DocumentExistsByURI(String uri) throws SolrServerException {
		return DocumentExists("uri:\"" + uri + "\"");
	}

	public Boolean DocumentExistsByComplexUri(String uri) throws SolrServerException {
		if (uri.contains(",")) {
			return DocumentExists(getComplexUriSolrQuery(uri));
		} else {
			return DocumentExistsByURI(uri);
		}
	}

	public String getComplexUriSolrQuery(String uri) {
		if (uri.contains(",")) {
			StringBuilder bldr = new StringBuilder();
			String[] uris = uri.split(",");
			for (int i = 0; i < uris.length; i++) {
				if (i > 0) {
					bldr.append(" ");
				}
				bldr.append("uri:*" + uris[i] + "*");
				if (i < (uris.length - 1)) {
					bldr.append(" OR");
				}
			}
			return bldr.toString();
		} else {
			return uri;
		}
	}

	public SimpleOrderedMap<?> QueryFacets(String queryStr, String facetQuery) throws SolrServerException {
		SolrQuery query = new SolrQuery();
		query.setRows(0);
		query.setQuery(queryStr);
		query.add("json.facet", facetQuery);
		try {
			QueryResponse response = client.query("events", query);
			SimpleOrderedMap<?> facets = (SimpleOrderedMap<?>) response.getResponse().get("facets");
			return facets;
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}
	
	public List<IndexedEvent> FindSimilarEvents(String searchText) throws SolrServerException {
		SolrQuery query = new SolrQuery();
	    query.setRequestHandler("/" + MoreLikeThisParams.MLT);
	    query.setParam(CommonParams.STREAM_BODY, searchText);
	    query.setRows(20);
	    try {
			SolrDocumentList response = client.query("events", query).getResults();
			//remove any potential documents that are not IndexedEvents
			for (int i = 0; i < response.size(); i++) {
				SolrDocument doc = response.get(i);
				if (doc.containsKey("eventId") || doc.containsKey("sourceId")) { //only source documents contain these fields
					response.remove(i--);
				}
			}
			List<IndexedEvent> events = convertSolrDocsToTypedDocs(IndexedEvent.class.getConstructor(SolrDocument.class), response);
			return events;
		} catch (IOException | NoSuchMethodException e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}

	public SolrDocumentList QuerySolrDocuments(String queryStr, int rows, int start, SortClause sort, String... filterQueries) throws SolrServerException {
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
			SolrDocumentList response = client.query("events", query).getResults();
			return response;
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}

	public <T> List<T> QueryIndexedDocuments(Class<T> clazz, String queryStr, int rows, int start, SortClause sort, String... filterQueries) throws SolrServerException {
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
			SolrDocumentList response = client.query("events", query).getResults();
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
	
	public void writeTrainingDataToFile(String trainingFilePath, Function<SolrQuery, SolrQuery> queryGetter,
										Tools.CheckedBiConsumer<IndexedEvent, FileOutputStream> consumer, TrainingDataThrottle throttle) {
		SolrQuery query = queryGetter.apply(new SolrQuery());
		query.setRows(1000000);
		appendFilterQueries(query);
		try {
			File file = new File(trainingFilePath);
			file.getParentFile().mkdirs();
			FileOutputStream fos = new FileOutputStream(file);
			final BlockingQueue<SolrDocument> tmpQueue = new LinkedBlockingQueue<SolrDocument>();
			client.queryAndStreamResponse("events", query, new CallbackHandler(tmpQueue));
			throttle.init(tmpQueue.size());

			SolrDocument tmpDoc;
	        do {
	          tmpDoc = tmpQueue.take();
	          if (!(tmpDoc instanceof StopDoc)) {
	        	  IndexedEvent event = new IndexedEvent(tmpDoc);
	        	  if (throttle.check(event)) {
					  consumer.apply(event, fos);
					  fos.write(System.lineSeparator().getBytes());
				  }
	          }
	        } while (!(tmpDoc instanceof StopDoc));
	        
	        fos.close();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
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

		public abstract boolean check(IndexedEvent event);
	}

	public static class EventCategorizationThrottle extends TrainingDataThrottle {

		private int numDocs;
		private int throttleForCount;

		public EventCategorizationThrottle(String throttleFor, double throttlePercent) {
			super(throttleFor, throttlePercent);
		}

		@Override
		public void init(int numDocs) {
			this.numDocs = numDocs;
		}

		@Override
		public boolean check(IndexedEvent event) {
			if (event.getCategory().compareTo(throttleFor) == 0) {
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

	public static class ClusteringThrottle extends TrainingDataThrottle {

		public ClusteringThrottle(String throttleFor, double throttlePercent) {
			super(throttleFor, throttlePercent);
		}

		@Override
		public void init(int numDocs) {

		}

		@Override
		public boolean check(IndexedEvent event) {
			return true;
		}
	}

	public void formatForEventCategorization(IndexedEvent event, FileOutputStream fos) throws IOException {
		fos.write(event.GetModelTrainingForm().getBytes(Charset.forName("Cp1252")));
	}

	public void formatForClustering(IndexedEvent event, FileOutputStream fos) throws IOException {
		fos.write(event.GetClusteringForm().getBytes(Charset.forName("Cp1252")));
	}

	public void formatForAnalysis(IndexedEvent event, FileOutputStream fos) throws IOException {
		fos.write(event.GetAnalysisForm().getBytes(Charset.forName("Cp1252")));
	}

	public SolrQuery getDoccatDataQuery(SolrQuery query) {
		query.setQuery("eventState:* AND -eventState:" + SolrConstants.Events.EVENT_STATE_NEW);
		query.addFilterQuery("category:* AND -category:" + SolrConstants.Events.CATEGORY_UNCATEGORIZED);

		return query;
	}

	public SolrQuery getNewDataQuery(SolrQuery query) {
		query.setQuery("eventState:" + SolrConstants.Events.EVENT_STATE_NEW);

		return query;
	}

	public SolrQuery getAnalyzedDataQuery(SolrQuery query) {
		query.setQuery("eventState:" + SolrConstants.Events.EVENT_STATE_ANALYZED);

		return query;
	}

	public SolrQuery getAnalyzedUncategorizedDataQuery(SolrQuery query) {
		query.setQuery("eventState:" + SolrConstants.Events.EVENT_STATE_ANALYZED);
		query.addFilterQuery("category:" + SolrConstants.Events.CATEGORY_UNCATEGORIZED);

		return query;
	}

	public SolrQuery getAnalyzedIrrelevantDataQuery(SolrQuery query) {
		query.setQuery("eventState:" + SolrConstants.Events.EVENT_STATE_ANALYZED);
		query.addFilterQuery("category:" + SolrConstants.Events.CATEGORY_IRRELEVANT);

		return query;
	}

	public SolrQuery getClusteringDataQuery(SolrQuery query) {
		query.setQuery("eventState:* AND -eventState:" + SolrConstants.Events.EVENT_STATE_ANALYZED);

		return query;
	}

	private void appendFilterQueries(SolrQuery query) {
		query.addFilterQuery("-userCreated:true");
		query.addFilterQuery("-feedType:" + SolrConstants.Events.FEED_TYPE_AUTHORITATIVE);
		query.addFilterQuery("concepts:*");
	}

	public void WriteDataToFile(String filePath, String queryStr, int rows, String... filterQueries) throws SolrServerException {
		ObjectWriter writer = new ObjectMapper().writer().withDefaultPrettyPrinter();
		SolrDocumentList events = QuerySolrDocuments(queryStr, rows, 0, null, filterQueries);
		try {
			String output = writer.writeValueAsString(events);
			File file = new File(filePath);
			file.getParentFile().mkdirs();
			Files.write(output, file, Charset.forName("Cp1252"));
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public void WriteEventDataToFile(String filePath, String queryStr, int rows, String... filterQueries) throws SolrServerException {
		ObjectWriter writer = new ObjectMapper().writer().withDefaultPrettyPrinter();
		List<IndexedEvent> events = QueryIndexedDocuments(IndexedEvent.class, queryStr, rows, 0, null, filterQueries);
		try {
			String output = writer.writeValueAsString(events);
			File file = new File(filePath);
			file.getParentFile().mkdirs();
			Files.write(output, file, Charset.forName("Cp1252"));
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public void WriteSourceDataToFile(String filePath, String queryStr, int rows, String... filterQueries) throws SolrServerException {
		ObjectWriter writer = new ObjectMapper().writer().withDefaultPrettyPrinter();
		List<IndexedEventSource> sources = QueryIndexedDocuments(IndexedEventSource.class, queryStr, rows, 0, null, filterQueries);
		try {
			String output = writer.writeValueAsString(sources);
			File file = new File(filePath);
			file.getParentFile().mkdirs();
			Files.write(output, file, Charset.forName("Cp1252"));
		} catch (IOException e) {
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
