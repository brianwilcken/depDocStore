package webapp.models;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

import java.util.List;

public class DocumentsResponse {
    private List<SolrDocument> docs;
    private long numFound;

    public DocumentsResponse(SolrDocumentList docs, long numFound) {
        this.docs = docs;
        this.numFound = numFound;
    }

    public List<SolrDocument> getDocs() {
        return docs;
    }

    public void setDocs(List<SolrDocument> docs) {
        this.docs = docs;
    }

    public long getNumFound() {
        return numFound;
    }

    public void setNumFound(long numFound) {
        this.numFound = numFound;
    }
}
