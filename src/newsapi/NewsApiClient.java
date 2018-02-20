package newsapi;

import newsapi.model.Article;
import newsapi.model.NewsQuery;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import common.Tools;
import elasticsearchapi.ElasticSearchClient;

public class NewsApiClient {
	
	public static void main(String args[]) {
		NewsApiClient newsApiClient = new NewsApiClient();
		ElasticSearchClient elasticSearchClient = new ElasticSearchClient();
		
//		String[] queryCategories = newsApiClient.GetQueryCategories();
//		
//		for (String category : queryCategories) {
//			Collection<Article> articles = newsApiClient.Query(category);
//			elasticSearchClient.IndexNewsArticles(articles);
//		}
		
		List<String> articles = elasticSearchClient.GetUncategorizedNewsArticlesJSON(200);
		try {
			String jsonArr = "[" + String.join(",", articles) + "]";
			FileUtils.writeStringToFile(new File("data/uncategorized-articles.txt"), jsonArr);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private String[] GetQueryCategories() {
		String hazardTerms = Tools.GetFileString("data/hazard-term-word-bank.txt").toLowerCase();
		String[] hazardTermsArr = hazardTerms.split(System.lineSeparator());
		return hazardTermsArr;
	}
	
	public Collection<Article> Query(String q) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

	    Proxy proxy= new Proxy(Type.HTTP, new InetSocketAddress("webbalance.inl.gov", 8080));
	    requestFactory.setProxy(proxy);
	    
		RestTemplate restTemplate = new RestTemplate(requestFactory);
		NewsQuery newsQuery = restTemplate.getForObject("https://newsapi.org/v2/everything?q=" + q + "&sortBy=publishedAt&apiKey=ef51a86394034696b105a37549c0eed0", NewsQuery.class);
		Collection<Article> articles = newsQuery.getArticles();
		
		return articles;
	}
	
}
