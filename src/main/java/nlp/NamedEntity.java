package nlp;

import org.apache.solr.common.SolrDocument;
import opennlp.tools.util.Span;
import solrapi.model.IndexedObject;

import java.util.UUID;

public class NamedEntity extends IndexedObject {
    private String entity;
    private int line;
    private Span span;
    private String id;

    public NamedEntity(String entity, Span span, int line) {
        this.entity = entity;
        this.span = span;
        this.line = line;
        id = UUID.randomUUID().toString();
    }

    public NamedEntity(SolrDocument doc) {
        ConsumeSolr(doc);
    }

    public SolrDocument mutate(String docId) {
        SolrDocument doc = new SolrDocument();
        doc.addField("id", id);
        doc.addField("docId", docId);
        doc.addField("entity", entity);
        doc.addField("line", line);
        doc.addField("start", span.getStart());
        doc.addField("end", span.getEnd());

        return doc;
    }

    public String[] autoAnnotate(String[] tokens) {
        int start = span.getStart();
        int end = span.getEnd();
        String startAnnotation = " <START:" + span.getType() +"> ";
        String endAnnotation = " <END> ";

        tokens[start] = startAnnotation + tokens[start];
        tokens[end - 1] = tokens[end - 1] + endAnnotation;

        return tokens;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public Span getSpan() {
        return span;
    }

    public void setSpan(Span span) {
        this.span = span;
    }
}
