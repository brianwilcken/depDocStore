package datacapableapi;

import com.google.common.base.Strings;
import common.Tools;
import datacapableapi.model.Event;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import solrapi.SolrClient;
import solrapi.model.IndexedEvent;
import solrapi.model.IndexedEventSource;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DataCapableClient {

    private RestTemplate restTemplate;
    private SolrClient solrClient;
    private final String solrUrl = Tools.getProperty("solr.url");
    private final String apiKey = Tools.getProperty("dataCapable.apiKey");
    private final String eventsUrl = Tools.getProperty("dataCapable.eventsUrl");
    private final String proxyUrl = Tools.getProperty("proxy");
    private final int proxyPort = Integer.parseInt(Tools.getProperty("proxyPort"));
    private final Boolean useProxy = Boolean.parseBoolean(Tools.getProperty("use.proxy"));

    public static final Map<String, String> validCategoryMap;
    static {
        validCategoryMap = new HashMap<>();
        validCategoryMap.put("OUTAGE", "PowerOutage");
        validCategoryMap.put("GAS_LEAK", "GasLeak");
        validCategoryMap.put("GAS_EXPLOSION", "GasLeak");
        validCategoryMap.put("FLOODING", "Flood");
        validCategoryMap.put("ACTIVE_SHOOTER", "ActiveShooter");
        validCategoryMap.put("EARTHQUAKE", "Earthquake");
        validCategoryMap.put("WILDFIRE", "Wildfire");
        validCategoryMap.put("STRUCTURE_FIRE", "LocalHazard");
        validCategoryMap.put("GAS_OUTAGE", "LocalHazard");
        validCategoryMap.put("SINKHOLE", "LocalHazard");
        validCategoryMap.put("LANDSLIDE", "LocalHazard");

    }

    public static final Map<String, String> displayCategoryMap;
    static {
        displayCategoryMap = new HashMap<>();
        displayCategoryMap.put("OUTAGE", "Power outage");
        displayCategoryMap.put("GAS_LEAK", "Gas leak");
        displayCategoryMap.put("GAS_EXPLOSION", "Gas leak");
        displayCategoryMap.put("FLOODING", "Flood");
        displayCategoryMap.put("ACTIVE_SHOOTER", "Active shooter");
        displayCategoryMap.put("EARTHQUAKE", "Earthquake");
        displayCategoryMap.put("WILDFIRE", "Wildfire");
        displayCategoryMap.put("STRUCTURE_FIRE", "Local Hazard");
        displayCategoryMap.put("GAS_OUTAGE", "Local Hazard");
        displayCategoryMap.put("SINKHOLE", "Local Hazard");
        displayCategoryMap.put("LANDSLIDE", "Local Hazard");
    }

    public static void main(String[] args){
        DataCapableClient client = new DataCapableClient();
        Event[] events = client.QueryEvents();

        try {
            client.ProcessEvents(events);
        } catch (SolrServerException e) {
            e.printStackTrace();
        }
    }

    public DataCapableClient() {
        solrClient = new SolrClient(solrUrl);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        if (useProxy) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyUrl, proxyPort));
            requestFactory.setProxy(proxy);
        }

        restTemplate = new RestTemplate(requestFactory);
    }

    public Event[] QueryEvents() {
        Event[] response = restTemplate.getForObject(eventsUrl + "?key=" + apiKey, Event[].class);
        return response;
    }

    public List<IndexedEvent> ProcessEvents(Event[] events) throws SolrServerException {
        //Filter out events with fewer than 5 posts unless the event type is Active Shooter
        List<Event> wellDefinedEvents = Arrays.stream(events)
                .filter(p -> !Strings.isNullOrEmpty(p.getLocationDetails()))
                .filter(p -> p.getPosts().length >= 5 || p.getType().compareTo("ACTIVE_SHOOTER") == 0)
                .collect(Collectors.toList());

        //Filter out events where the event type is not contained in the mapping of valid categories
        List<Event> validTypeEvents = wellDefinedEvents.stream()
                .filter(p -> validCategoryMap.containsKey(p.getType()))
                .collect(Collectors.toList());

        //Project data capable events to Solr schema compatible format
        List<IndexedEvent> validEvents = validTypeEvents.stream()
                .map(p -> p.GetIndexedEvent())
                .collect(Collectors.toList());

        //Get the valid events that have not yet been indexed
        List<IndexedEvent> newEvents = solrClient.GetIndexableEvents(validEvents);

        //Get the set of events that are presently indexed
        validEvents.removeAll(newEvents);

        //for the set of previously indexed events perform an update by verifying those fields that must be preserved
        for (IndexedEvent event : validEvents) {
            List<IndexedEvent> updEvents = solrClient.QueryIndexedDocuments(IndexedEvent.class, "id:" + event.getId(), 1, 0, null);
            if (!updEvents.isEmpty()) {
                IndexedEvent updEvent = updEvents.get(0);
                event.updateForDynamicFields(updEvent);
            }
        }

        //combine the old and new events into a common set
        validEvents.addAll(newEvents);

        //Get the list of sources from each of the valid events
        List<IndexedEventSource> indexableSources = validEvents.stream()
                .map(p -> p.getSources())
                .flatMap(List::stream)
                .collect(Collectors.toList());

        //Perform NLP post-processing to resolve two concerns:
        //1. validate geoparsing of location data
        //2. validate event category

        //Index events to Solr
        solrClient.indexDocuments(validEvents);

        //Index sources to Solr
        solrClient.indexDocuments(indexableSources);

        return validEvents;
    }
}
