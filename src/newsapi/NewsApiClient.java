package newsapi;

import newsapi.model.Article;
import newsapi.model.NewsQuery;
import solrapi.SolrClient;

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

public class NewsApiClient {
	
	private SolrClient solrClient = new SolrClient("http://localhost:8983/solr");
	
	public static void main(String args[]) {
		NewsApiClient newsApiClient = new NewsApiClient();
		newsApiClient.RefreshNewsArticles();
	}
	
	public void RefreshNewsArticles() {
		String[] queryCategories = GetQueryCategories();
		
		for (String category : queryCategories) {
			Collection<Article> articles = Query(category);
			solrClient.IndexNewsArticles(articles);
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
		articles.forEach(p -> {
			p.setName(p.getSource().getName());
			p.initId();
			});
		
		return articles;
	}
	
}
