package solrapi;

import org.apache.solr.client.solrj.SolrServerException;

public class SolrDataImporter {

    public static void main(String[] args) {
        SolrClient solrClient = new SolrClient("http://localhost:8983/solr");
        try {
            System.out.println("Begin importing documents to Solr");
            solrClient.UpdateDocumentsFromJsonFile("./depData.json");
            System.out.println("Finished importing documents to Solr");
        } catch (SolrServerException e) {
            System.out.println("ERROR! Unable to contact Solr server.");
            e.printStackTrace();
        }

    }
}
