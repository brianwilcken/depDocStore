package solrapi;

import org.apache.solr.client.solrj.SolrServerException;

public class SolrDataExporter {

    public static void main(String[] args) {
        SolrClient solrClient = new SolrClient("http://localhost:8983/solr");
        try {
            System.out.println("Begin exporting documents from Solr");
            solrClient.WriteDataToFile("./depData.json", "filename:*", 1000000);
            System.out.println("Finished exporting documents to Solr");
        } catch (SolrServerException e) {
            System.out.println("ERROR! Unable to contact Solr server.");
            e.printStackTrace();
        }

    }
}
