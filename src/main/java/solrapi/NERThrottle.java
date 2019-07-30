package solrapi;

import org.apache.solr.common.SolrDocument;

import java.util.List;

public class NERThrottle extends TrainingDataThrottle {

    public NERThrottle() {
    }

    @Override
    public void init(long numDocs) {

    }

    @Override
    public boolean check(SolrDocument doc) {
        return true;
    }
}
