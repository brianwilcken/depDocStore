package solrapi;

import common.Tools;
import org.apache.solr.client.solrj.SolrServerException;

public class SolrLDACategoryUpdater {
    public static void main(String[] args) {
        String query = args[0];
        int start = Integer.parseInt(args[1]);
        int end = Integer.parseInt(args[2]);
        SolrClient solrClient = new SolrClient(Tools.getProperty("solr.url"));
        System.out.println("Begin updating LDA categories for Solr Documents");
        solrClient.runLDACategoryUpdateJob(query, start, end);
        System.out.println("Finished updating LDA categories for Solr Documents");
    }
}
