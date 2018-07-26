package datacapableapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import common.Tools;
import solrapi.model.IndexedEventSource;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Post {
    public String message;
    public double latitude;
    public double longitude;
    public String source;
    public String sourceDetails;
    public String imageUrl;
    public String date;
    public long id;
    public String locationType;
    public String locationDetails;
    public String sentiment;
    public String username;
    public String displayName;
    public String postUrl;
    public String profileUrl;
    public String replyUrl;

    public IndexedEventSource getIndexedEventSource(String eventId) {
        IndexedEventSource source = new IndexedEventSource();

        DateTimeFormatter f = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy", Locale.ENGLISH);
        LocalDateTime ldt = LocalDateTime.parse(date.replace("UTC ", ""), f);
        ZonedDateTime zdt = ZonedDateTime.ofInstant(ldt, ZoneOffset.UTC, ZoneId.of("UTC"));

        source.setEventId(eventId);
        source.setArticleDate(Tools.getFormattedDateTimeString(zdt.toInstant()));
        source.setUri(Long.toString(id));
        source.setUrl("https://twitter.com/");
        source.setTitle(message);
        source.setSummary(message);
        source.setSourceName("Twitter Status");
        source.setSourceLocation(locationDetails);
        source.initId();

        return source;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceDetails() {
        return sourceDetails;
    }

    public void setSourceDetails(String sourceDetails) {
        this.sourceDetails = sourceDetails;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getLocationType() {
        return locationType;
    }

    public void setLocationType(String locationType) {
        this.locationType = locationType;
    }

    public String getLocationDetails() {
        return locationDetails;
    }

    public void setLocationDetails(String locationDetails) {
        this.locationDetails = locationDetails;
    }

    public String getSentiment() {
        return sentiment;
    }

    public void setSentiment(String sentiment) {
        this.sentiment = sentiment;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPostUrl() {
        return postUrl;
    }

    public void setPostUrl(String postUrl) {
        this.postUrl = postUrl;
    }

    public String getProfileUrl() {
        return profileUrl;
    }

    public void setProfileUrl(String profileUrl) {
        this.profileUrl = profileUrl;
    }

    public String getReplyUrl() {
        return replyUrl;
    }

    public void setReplyUrl(String replyUrl) {
        this.replyUrl = replyUrl;
    }
}
