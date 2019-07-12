package solrapi;

import org.apache.solr.common.SolrDocument;

import java.util.List;

abstract class TrainingDataThrottle {

    public abstract void init(long numDocs);

    public abstract boolean check(SolrDocument doc);

    public abstract boolean check(List category);
}
