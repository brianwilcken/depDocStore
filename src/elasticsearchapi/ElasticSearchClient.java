package elasticsearchapi;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import common.Tools;
import newsapi.model.Article;

public class ElasticSearchClient {

	private TransportClient client;
	private ObjectMapper mapper;
	private MessageDigest md;
	
	public static void main(String[] args) {
		ElasticSearchClient elasticSearchClient = new ElasticSearchClient();
		
//		String newlyCategorizedArticles = Tools.GetFileString("data/uncategorized-articles.txt", "Cp1252");
//		try {
//			Article[] articles = elasticSearchClient.mapper.readValue(newlyCategorizedArticles, Article[].class);
//			elasticSearchClient.UpdateNewsArticles(Arrays.asList(articles));
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		
//		List<String> categorizedTrainingData = elasticSearchClient.GetCategorizedNewsArticles();
//		try {
//			String categorizedTrainingDataStr = String.join(System.lineSeparator(), categorizedTrainingData);
//			FileUtils.writeStringToFile(new File("data/categorized-training-data.train"), categorizedTrainingDataStr, "Cp1252");
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}
	
	public ElasticSearchClient() {
		mapper = new ObjectMapper();
		client = new PreBuiltTransportClient(Settings.EMPTY);
		try {
			client.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"), 9300));
			md = MessageDigest.getInstance("SHA-1");
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private String GenerateArticleId(Article article) {
		String id = null;
		try {
			id = Hex.encodeHexString(md.digest(mapper.writeValueAsBytes(
					article.getDescription() + 
					article.getTitle() + 
					article.getPublishedAt() + 
					article.getUrl() + 
					article.getUrlToImage() + 
					article.getSource().getName())));
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return id;
	}
	
	public void UpdateNewsArticles(Collection<Article> articles) {
		for (Article article : articles) {
			byte[] json;
			try {
				json = mapper.writeValueAsBytes(article);
				String id = GenerateArticleId(article);
				
				//query to make sure duplicate article is not being indexed by using SHA as unique id
				GetResponse getResponse = client.prepareGet("news", "article", id).get();
				if (getResponse.isExists()) {
					UpdateResponse updateResponse = client.prepareUpdate("news", "article", id)
							.setDoc(json, XContentType.JSON)
							.get();
					
					if (updateResponse.status() != RestStatus.OK) {
						
					}
				}
			} catch (JsonProcessingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public void IndexNewsArticles(Collection<Article> articles) {
		
		for (Article article : articles) {
			byte[] json;
			try {
				json = mapper.writeValueAsBytes(article);
				String id = GenerateArticleId(article);
				
				//query to make sure duplicate article is not being indexed by using SHA as unique id
				GetResponse getResponse = client.prepareGet("news", "article", id).get();
				if (!getResponse.isExists()) {
					IndexResponse indexResponse = client.prepareIndex("news", "article", id)
							.setSource(json, XContentType.JSON)
							.get();
					
					if (indexResponse.status() != RestStatus.CREATED) {
						
					}
				}
			} catch (JsonProcessingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public List<String> GetCategorizedNewsArticles() {
		SearchResponse scrollResponse = client.prepareSearch("news")
			.setTypes("article")
			.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
			.setScroll(new TimeValue(60000))
			.setQuery(QueryBuilders.existsQuery("category"))
			.setSize(100)
			.get();
		
		List<String> categorizedTrainingData = new ArrayList<String>();
		
		do {
			scrollResponse.getHits().forEach(p -> {
				try {
					Article article = mapper.readValue(p.getSourceAsString(), Article.class);
					String entry = article.getCategory() + "\t" + article.getTitle() + ": " + article.getDescription();
					categorizedTrainingData.add(entry.replace("\r", " ").replace("\n", " "));
				} catch (JsonParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (JsonMappingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
			
			scrollResponse = client.prepareSearchScroll(scrollResponse.getScrollId()).setScroll(new TimeValue(60000)).execute().actionGet();
		} while(scrollResponse.getHits().getHits().length != 0);

		return categorizedTrainingData;
	}
	
	public List<String> GetUncategorizedNewsArticlesJSON(int batchSize) {
		SearchResponse searchResponse = client.prepareSearch("news")
			.setTypes("article")
			.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
			.setQuery(QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery("category")))
			.setSize(batchSize)
			.get();
		
		List<String> searchHits = new ArrayList<String>();
		searchResponse.getHits().forEach(p -> searchHits.add(p.getSourceAsString()));
		
		return searchHits;
	}
	
	public void Close() {
		client.close();
	}
	
}
