package solrapi;

import org.apache.solr.client.solrj.SolrServerException;

public class SolrDataImporter {

    public static void main(String[] args) {
        SolrClient solrClient = new SolrClient("http://localhost:8983/solr");
        try {
            System.out.println("Begin importing events to Solr");
            solrClient.UpdateIndexedEventsFromJsonFile("./all-model-training-events.json");
            System.out.println("Finished importing events to Solr");
        } catch (SolrServerException e) {
            System.out.println("ERROR! Unable to contact Solr server.");
            e.printStackTrace();
        }

    }
}
