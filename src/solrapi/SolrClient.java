package solrapi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

//import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.StreamingResponseCallback;
import org.apache.solr.client.solrj.beans.DocumentObjectBinder;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.io.Files;

import common.Tools;
import eventsregistryapi.model.EventData;
import eventsregistryapi.model.IndexedArticle;
import eventsregistryapi.model.IndexedEvent;
import eventsregistryapi.model.IndexedObject;
import newsapi.model.Article;

public class SolrClient {

	private HttpSolrClient client;
	private static ObjectMapper mapper = new ObjectMapper();
	
	public static void main(String[] args) {
		SolrClient solrClient = new SolrClient("http://localhost:8983/solr");
		
		//solrClient.UpdateNewsArticlesFromFile("data/uncategorized-articles.txt");
		//solrClient.WriteUncategorizedNewsArticlesToFile("data/uncategorized-articles.json", 50);
		//solrClient.WriteNewsArticlesToFile("data/categorized-training-data.train", "category:*", Integer.MAX_VALUE);
	}
	
	public SolrClient(String solrHostURL) {
		client = new HttpSolrClient.Builder(solrHostURL).build();
	}
	
	public void UpdateNewsArticlesFromFile(String filePath) {
		String file = Tools.GetFileString(filePath, "Cp1252");
		try {
			Article[] articles = mapper.readValue(file, Article[].class);
			IndexNewsArticles(Arrays.asList(articles));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void UpdateIndexedArticlesFromFile(String filePath) {
		String file = Tools.GetFileString(filePath, "Cp1252");
		try {
			IndexedArticle[] articles = mapper.readValue(file, IndexedArticle[].class);
			IndexDocuments(Arrays.asList(articles));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void UpdateIndexedEventsFromFile(String filePath) {
		String file = Tools.GetFileString(filePath, "Cp1252");
		try {
			IndexedEvent[] events = mapper.readValue(file, IndexedEvent[].class);
			for (IndexedEvent event : events) {
				event.updateLastUpdatedDate();
			}
			IndexDocuments(Arrays.asList(events));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public <T> void IndexDocuments(Collection<T> docs) {
		try {
			if (!docs.isEmpty()) {
				client.addBeans("events", docs);
				UpdateResponse updateResponse = client.commit("events");
				
				if (updateResponse.getStatus() != 0) {
					//TODO What should happen if the update fails?
				}
			}
		} catch (SolrServerException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public Boolean IsDocumentAlreadyIndexed(String uri) {
		SolrQuery query = new SolrQuery();
		query.setRows(0);
		query.setQuery("uri:" + uri);
		try {
			QueryResponse response = client.query("events", query);
			return response.getResults().getNumFound() > 0;
		} catch (SolrServerException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}
	
	public <T> List<T> QueryIndexedDocuments(Class<T> clazz, String queryStr, int rows, String... filterQueries) {
		SolrQuery query = new SolrQuery();
		query.setQuery(queryStr);
		if (filterQueries != null) {
			query.setFilterQueries(filterQueries);
		}
		query.setRows(rows);
		try {
			Constructor<?> cons;
			try {
				cons = clazz.getConstructor(SolrDocument.class);
			} catch (NoSuchMethodException | SecurityException e1) {
				return null;
			}
			SolrDocumentList response = client.query("events", query).getResults();
			List<T> events = (List<T>) response.stream().map(p -> {
				try {
					return cons.newInstance(p);
				} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
						| InvocationTargetException e) {
					return null;
				}
			}).collect(Collectors.toList());
			return events;
		} catch (SolrServerException | IOException e) {
			return null;
		}
	}
	
	public void WriteEventCategorizationTrainingDataToFile(String trainingFilePath) {
		SolrQuery query = new SolrQuery();
		query.setRows(1000000);
		query.setQuery("-eventState:" + SolrConstants.Events.EVENT_STATE_NEW);
		
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
	
	public void WriteEventDataToFile(String filePath, String queryStr, int rows, String... filterQueries) {
		ObjectWriter writer = new ObjectMapper().writer().withDefaultPrettyPrinter();
		List<IndexedEvent> events = QueryIndexedDocuments(IndexedEvent.class, queryStr, rows, filterQueries);
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
	
	public void IndexNewsArticles(Collection<Article> articles) {
		try {
			client.addBeans("news", articles);
			UpdateResponse updateResponse = client.commit("news");
			
			if (updateResponse.getStatus() != 0) {
				//TODO What should happen if the update fails?
			}
		} catch (SolrServerException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void WriteUncategorizedNewsArticlesToFile(String filePath, int rows) {
		ObjectWriter writer = new ObjectMapper().writer().withDefaultPrettyPrinter();
		SolrQuery query = new SolrQuery();
		query.setQuery("-category:*");
		query.setRows(rows);
		
		try {
			QueryResponse response = client.query("news", query);
			List<Article> articles = response.getBeans(Article.class);
			String output = writer.writeValueAsString(articles);
			Files.write(output, new File(filePath), Charset.forName("Cp1252"));
		} catch (SolrServerException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void WriteNewsArticlesToFile(String filePath, String solrQuery, int rows) {
		DocumentObjectBinder binder = new DocumentObjectBinder();
		
		SolrQuery query = new SolrQuery();
		query.setRows(rows);
		query.setQuery(solrQuery);
		
		try {
			File file = new File(filePath);
			FileOutputStream fos = new FileOutputStream(file);
			final BlockingQueue<SolrDocument> tmpQueue = new LinkedBlockingQueue<SolrDocument>();
			client.queryAndStreamResponse("news", query, new CallbackHandler(tmpQueue));
			
			SolrDocument tmpDoc;
	        do {
	          tmpDoc = tmpQueue.take();
	          if (!(tmpDoc instanceof StopDoc)) {
	        	  Article article = binder.getBean(Article.class, tmpDoc);
		          String entry = (article.getCategory() + "\t" + article.getTitle() + ": " + article.getDescription()).replace("\r", " ").replace("\n", " ");
		          fos.write(entry.getBytes(Charset.forName("Cp1252")));
		          fos.write(System.lineSeparator().getBytes());
	          }
	        } while (!(tmpDoc instanceof StopDoc));
	        
	        fos.close();
		} catch (SolrServerException | IOException | InterruptedException e) {
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
