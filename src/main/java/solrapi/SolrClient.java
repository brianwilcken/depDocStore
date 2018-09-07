package solrapi;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.SortClause;
import org.apache.solr.client.solrj.SolrServerException;
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
	
//	public static void main(String[] args) {
//		SolrClient solrClient = new SolrClient("http://localhost:8983/solr");
//		//solrClient.writeTrainingDataToFile("data/analyzed-events.csv", solrClient::getAnalyzedDataQuery, solrClient::formatForClustering, new ClusteringThrottle("", 0));
//		try {
//			solrClient.WriteEventDataToFile("data/all-model-training-events.json", "eventState:* AND concepts:*", 10000);
//			//solrClient.UpdateIndexedEventsFromJsonFile("data/solrData.json");
//			//solrClient.UpdateIndexedEventsWithAnalyzedEvents("data/doc_clusters.csv");
//		} catch (SolrServerException e) {
//			e.printStackTrace();
//		}
//	}
	
	public void indexDocuments(Collection<SolrInputDocument> docs) throws SolrServerException {
		try {
			if (!docs.isEmpty()) {
				client.add(COLLECTION, docs);
				UpdateResponse updateResponse = client.commit(COLLECTION);
				
				if (updateResponse.getStatus() != 0) {
					//TODO What should happen if the update fails?
				}
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public void indexDocument(SolrInputDocument doc) throws SolrServerException {
		List<SolrInputDocument> docs = new ArrayList<>();
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
			SolrDocumentList response = client.query(COLLECTION, query).getResults();
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

	public void WriteDataToFile(String filePath, String queryStr, int rows, String... filterQueries) throws SolrServerException {
		ObjectWriter writer = new ObjectMapper().writer().withDefaultPrettyPrinter();
		SolrDocumentList docs = QuerySolrDocuments(queryStr, rows, 0, null, filterQueries);
		try {
			String output = writer.writeValueAsString(docs);
			File file = new File(filePath);
			file.getParentFile().mkdirs();
			Files.write(output, file, Charset.forName("Cp1252"));
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}
}
