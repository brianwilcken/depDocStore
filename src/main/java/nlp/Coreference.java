package nlp;

import org.apache.solr.common.SolrDocument;

public class Coreference {
    private String docId;
    private NamedEntity namedEntity;
    private String coref;
    private int line;
    private int start;
    private int end;

    public Coreference(String docId, NamedEntity namedEntity, String coref, int line, int start, int end) {
        this.docId = docId;
        this.namedEntity = namedEntity;
        this.coref = coref;
        this.line = line;
        this.start = start;
        this.end = end;
    }

    public SolrDocument mutate() {
        SolrDocument doc = new SolrDocument();
        doc.addField("docId", docId);
        doc.addField("entityId", namedEntity.getId());
        doc.addField("coref", coref);
        doc.addField("line", line);
        doc.addField("start", start);
        doc.addField("end", end);

        return doc;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public NamedEntity getNamedEntity() {
        return namedEntity;
    }

    public void setNamedEntity(NamedEntity namedEntity) {
        this.namedEntity = namedEntity;
    }

    public String getCoref() {
        return coref;
    }

    public void setCoref(String coref) {
        this.coref = coref;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int end) {
        this.end = end;
    }
}
