package eventsregistryapi.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;

import solrapi.SolrConstants;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ArticleResult {
	private String id;

    private String uri;

    private String lang;

    private boolean isDuplicate;

    private String date;

    private String time;

    private String dateTime;

    private double sim;

    private String url;

    private String title;

    private String body;

    private Source source;

    private String eventUri;

    private Location location;

    private Shares shares;

    private int wgt;

    public IndexedArticle GetIndexedArticle() {
    	IndexedArticle article = new IndexedArticle();
    	
    	article.setUri(uri);
    	article.setEventUri(eventUri);
    	article.setArticleDate(dateTime);
    	article.setUrl(url);
    	article.setTitle(title);
    	article.setSummary(body);
    	if (source != null) {
    		article.setSourceUri(source.getUri());
    		article.setSourceName(source.getTitle());
    		article.setSourceLocation(source.getLocation().getLabel().getEng());
    	}
    	article.initId();
    	
    	return article;
    }
    
    public void setId(String id){
        this.id = id;
    }
    public String getId(){
        return this.id;
    }
    public void setUri(String uri){
        this.uri = uri;
    }
    public String getUri(){
        return this.uri;
    }
    public void setLang(String lang){
        this.lang = lang;
    }
    public String getLang(){
        return this.lang;
    }
    public void setIsDuplicate(boolean isDuplicate){
        this.isDuplicate = isDuplicate;
    }
    public boolean getIsDuplicate(){
        return this.isDuplicate;
    }
    public void setDate(String date){
        this.date = date;
    }
    public String getDate(){
        return this.date;
    }
    public void setTime(String time){
        this.time = time;
    }
    public String getTime(){
        return this.time;
    }
    public void setDateTime(String dateTime){
        this.dateTime = dateTime;
    }
    public String getDateTime(){
        return this.dateTime;
    }
    public void setSim(double sim){
        this.sim = sim;
    }
    public double getSim(){
        return this.sim;
    }
    public void setUrl(String url){
        this.url = url;
    }
    public String getUrl(){
        return this.url;
    }
    public void setTitle(String title){
        this.title = title;
    }
    public String getTitle(){
        return this.title;
    }
    public void setBody(String body){
        this.body = body;
    }
    public String getBody(){
        return this.body;
    }
    public void setSource(Source source){
        this.source = source;
    }
    public Source getSource(){
        return this.source;
    }
    public void setEventUri(String eventUri){
        this.eventUri = eventUri;
    }
    public String getEventUri(){
        return this.eventUri;
    }
    public void setLocation(Location location){
        this.location = location;
    }
    public Location getLocation(){
        return this.location;
    }
    public void setShares(Shares shares){
        this.shares = shares;
    }
    public Shares getShares(){
        return this.shares;
    }
    public void setWgt(int wgt){
        this.wgt = wgt;
    }
    public int getWgt(){
        return this.wgt;
    }
}
