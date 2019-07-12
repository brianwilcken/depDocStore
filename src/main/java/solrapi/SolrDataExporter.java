package solrapi;

public class SolrDataExporter {

    public static void main(String[] args) {
        SolrClient client = new SolrClient("http://localhost:8983/solr");
        System.out.println("Begin exporting documents from Solr");
        client.writeCorpusDataToFile("./depData.json", client::writeAllDocumentsHeader, client::writeAllDocumentsFooter, null,
                client::getAllDocumentsDataQuery, client::formatForCompleteDocumentOutput, new NERThrottle());
        System.out.println("Finished exporting documents to Solr");
    }
}
