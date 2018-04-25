package solrapi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

//import org.apache.commons.codec.binary.Hex;
import eventsregistryapi.model.IndexedEventsQueryParams;
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
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.io.Files;

import common.Tools;
import eventsregistryapi.model.IndexedEventSource;
import eventsregistryapi.model.IndexedEvent;

public class SolrClient {

	private HttpSolrClient client;
	private static ObjectMapper mapper = new ObjectMapper();
	
	public SolrClient(String solrHostURL) {
		client = new HttpSolrClient.Builder(solrHostURL).build();
	}
	
	public static void main(String[] args) {
		SolrClient solrClient = new SolrClient("http://localhost:8983/solr");
		try {
			solrClient.UpdateIndexedEventsFromFile("data/solrData.json");
		} catch (SolrServerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void UpdateIndexedArticlesFromFile(String filePath) throws SolrServerException {
		String file = Tools.GetFileString(filePath, "Cp1252");
		try {
			IndexedEventSource[] articles = mapper.readValue(file, IndexedEventSource[].class);
			indexDocuments(Arrays.asList(articles));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void UpdateIndexedEventsFromFile(String filePath) throws SolrServerException {
		String file = Tools.GetFileString(filePath, "Cp1252");
		try {
			IndexedEvent[] events = mapper.readValue(file, IndexedEvent[].class);
			for (IndexedEvent event : events) {
				event.updateLastUpdatedDate();
			}
			indexDocuments(Arrays.asList(events));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public Boolean IsDocumentAlreadyIndexed(String uri) throws SolrServerException {
		SolrQuery query = new SolrQuery();
		query.setRows(0);
		query.setQuery("uri:" + uri);
		try {
			QueryResponse response = client.query("events", query);
			return response.getResults().getNumFound() > 0;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public SimpleOrderedMap<?> QueryFacets(String facetQuery) throws SolrServerException {
		SolrQuery query = new SolrQuery();
		query.setRows(0);
		query.setQuery("*:*");
		query.add("json.facet", facetQuery);
		try {
			QueryResponse response = client.query("events", query);
			SimpleOrderedMap<?> facets = (SimpleOrderedMap<?>) response.getResponse().get("facets");
			return facets;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
				if (doc.containsKey("eventUri")) { //only source documents contain this field
					response.remove(i--);
				}
			}
			List<IndexedEvent> events = convertSolrDocsToTypedDocs(IndexedEvent.class.getConstructor(SolrDocument.class), response);
			return events;
		} catch (IOException | NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
			return null;
		}
	}

	private <T> List<T> convertSolrDocsToTypedDocs(Constructor<?> cons, SolrDocumentList docs) {
		List<T> typedDocs = (List<T>) docs.stream().map(p -> {
			try {
				return cons.newInstance(p);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException e) {
				return null;
			}
		}).collect(Collectors.toList());

		return typedDocs;
	}
	
	public void writeEventCategorizationTrainingDataToFile(String trainingFilePath) {
		SolrQuery query = new SolrQuery();
		query.setRows(1000000);
		query.setQuery("eventState:* AND -eventState:" + SolrConstants.Events.EVENT_STATE_NEW);
		query.addFilterQuery("category:* AND -category:" + SolrConstants.Events.CATEGORY_UNKNOWN);
		query.addFilterQuery("-userCreated:true");
		try {
			File file = new File(trainingFilePath);
			file.getParentFile().mkdirs();
			FileOutputStream fos = new FileOutputStream(file);
			final BlockingQueue<SolrDocument> tmpQueue = new LinkedBlockingQueue<SolrDocument>();
			client.queryAndStreamResponse("events", query, new CallbackHandler(tmpQueue));
			
			SolrDocument tmpDoc;
	        do {
	          tmpDoc = tmpQueue.take();
	          if (!(tmpDoc instanceof StopDoc)) {
	        	  IndexedEvent event = new IndexedEvent(tmpDoc);
		          fos.write(event.GetModelTrainingForm().getBytes(Charset.forName("Cp1252")));
		          fos.write(System.lineSeparator().getBytes());
	          }
	        } while (!(tmpDoc instanceof StopDoc));
	        
	        fos.close();
		} catch (SolrServerException | IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
			// TODO Auto-generated catch block
			e.printStackTrace();
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
			// TODO Auto-generated catch block
			e.printStackTrace();
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
			// TODO Auto-generated catch block
			e.printStackTrace();
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
