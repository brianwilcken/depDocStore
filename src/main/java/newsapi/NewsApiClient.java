package newsapi;

import de.l3s.boilerpipe.BoilerpipeProcessingException;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import newsapi.model.Article;
import newsapi.model.NewsQuery;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.springframework.web.client.RestTemplate;

import common.Tools;
import common.DetectHtml;

public class NewsApiClient {
	
	public static void main(String args[]) {
		NewsApiClient newsApiClient = new NewsApiClient();
		ArticleExtractor articleExtractor = new ArticleExtractor();

		String[] queryCategories = newsApiClient.GetQueryCategories();
		for (String category : queryCategories) {
			Collection<Article> articles = newsApiClient.Query(category);

			for (Article article : articles) {
				try {
					URL url = new URL(article.getUrl());
					String articleText = articleExtractor.getText(url) + System.lineSeparator() + System.lineSeparator();
					if (DetectHtml.isHtml(articleText)) {
						articleText = articleExtractor.getText(articleText);
					}
					Files.write(Paths.get("data/extracted-articles.txt"), articleText.getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
				} catch (MalformedURLException e) {

				} catch (BoilerpipeProcessingException e) {

				} catch (IOException e) {

				}
			}
		}
	}
	
	private String[] GetQueryCategories() {
		String hazardTerms = Tools.getResource(Tools.getProperty("newsapi.hazardTerms")).toLowerCase();
		String[] hazardTermsArr = hazardTerms.split(System.lineSeparator());
		return hazardTermsArr;
	}
	
	public Collection<Article> Query(String q) {
		RestTemplate restTemplate = new RestTemplate();
		NewsQuery newsQuery = restTemplate.getForObject("https://newsapi.org/v2/top-headlines?country=us&q=" + q + "&sortBy=publishedAt&apiKey=ef51a86394034696b105a37549c0eed0", NewsQuery.class);
		Collection<Article> articles = newsQuery.getArticles();
		
		return articles;
	}
	
}
