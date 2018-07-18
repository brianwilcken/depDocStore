package datacapableapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import common.Tools;
import datacapableapi.DataCapableClient;
import org.apache.logging.log4j.util.Strings;
import solrapi.SolrConstants;
import solrapi.model.IndexedEvent;
import solrapi.model.IndexedEventSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Event {
    public long id;
    public long date;
    public String type;
    public double latitude;
    public double longitude;
    public String locationDetails;
    public long lastUpdated;
    public Post[] posts;

    private final String TITLE_SUFFIX =  " reported in <location>";

    public IndexedEvent GetIndexedEvent() {
        IndexedEvent event = new IndexedEvent();
        event.setUri(Long.toString(id) + "_" + type);
        event.setEventDate(Tools.getFormattedDateTimeString(Instant.ofEpochSecond(date/1000, 0)));
        event.setLastUpdated(Tools.getFormattedDateTimeString(Instant.ofEpochSecond(lastUpdated/1000, 0)));
        event.setTitle(getTitle());
        event.setSummary(getSummary());
        event.setImages(getImages());
        event.setLatitude(Double.toString(latitude));
        event.setLongitude(Double.toString(longitude));
        event.setLocation(locationDetails);
        event.setTotalArticleCount(posts.length);
        event.setEventState(SolrConstants.Events.EVENT_STATE_NEW);
        event.setFeedType(SolrConstants.Events.FEED_TYPE_MEDIA);
        event.setCategorizationState(SolrConstants.Events.CATEGORIZATION_STATE_MACHINE);
        event.setCategory(DataCapableClient.validCategoryMap.get(type));
        event.initId();

        List<IndexedEventSource> sources = Arrays.stream(posts)
                .filter(p -> p.source.compareTo("TWITTER") == 0)
                .map(p -> p.getIndexedEventSource(event.getId()))
                .collect(Collectors.toList());

        event.setSources(sources);

        return event;
    }

    private String getTitle() {
        String displayCat = DataCapableClient.displayCategoryMap.get(type);
        String title = displayCat + TITLE_SUFFIX;
        title = title.replace("<location>", locationDetails);

        return title;
    }

    private String getSummary() {
        String summary = Arrays.stream(posts)
                .filter(p -> p.source.compareTo("TWITTER") == 0)
                .map(p -> p.message.replace("\n", "").replace("\r", ""))
                .findFirst().get();

        return summary;
    }

    private String[] getImages() {
        String[] images = Arrays.stream(posts)
                .filter(p -> p.source.compareTo("TWITTER") == 0)
                .filter(p -> Strings.isNotEmpty(p.imageUrl) && Strings.isNotBlank(p.imageUrl))
                .map(p -> p.imageUrl)
                .toArray(String[]::new);

        return images;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public String getLocationDetails() {
        return locationDetails;
    }

    public void setLocationDetails(String locationDetails) {
        this.locationDetails = locationDetails;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Post[] getPosts() {
        return posts;
    }

    public void setPosts(Post[] posts) {
        this.posts = posts;
    }
}
