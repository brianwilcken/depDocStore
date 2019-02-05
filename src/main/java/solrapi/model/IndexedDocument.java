package solrapi.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

import nlp.NLPTools;
import org.apache.commons.codec.binary.Hex;
import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.common.SolrDocument;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import common.Tools;
import opennlp.tools.tokenize.TokenizerModel;

public class IndexedDocument extends IndexedObject {
    @Field
    private String id;
    @Field
    private String docStoreId;
    @Field
    private String created;
    @Field
    private String lastUpdated;
    @Field
    private String docText;
    @Field
    private String category;
    @Field
    private String url;

    private static ObjectMapper mapper = new ObjectMapper();

    public IndexedDocument() { }

    public IndexedDocument(SolrDocument doc) {
        consumeSolr(doc);
    }

    public void initId() {
        try {
            if (url != null) {
                id = Hex.encodeHexString(MessageDigest.getInstance("SHA-1").digest(mapper.writeValueAsBytes(url)));
            } else {
                id = UUID.randomUUID().toString();
            }
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public String[] GetDocCatTokens(TokenizerModel model) {
        String normalized = getNormalizedDocCatString();
        String[] tokens = NLPTools.detectTokens(model, normalized);

        return tokens;
    }

    private String getNormalizedDocCatString() {
        String docCatStr = docText.replace("\r", " ").replace("\n", " ");

        return NLPTools.normalizeText(docCatStr);
    }

    public void updateLastUpdatedDate() {
        this.setLastUpdated(Tools.getFormattedDateTimeString(Instant.now()));
    }

    public String GetModelTrainingForm() {
        return category + "\t" + getNormalizedDocCatString();
    }

    public String GetClusteringForm() {
        String clusteringStr = id + "," + docText.replace(",", "");
        clusteringStr = clusteringStr.replace("\r", " ")
                .replace("\n", " ");

        return clusteringStr;
    }

    public String GetAnalysisForm() {
        String analysisStr = "0," + id + "," + docText.replace(",", "").replace("\"", "'") + ",cluster," + "0," + category;
        analysisStr = analysisStr.replace("\r", " ")
                .replace("\n", " ");

        return analysisStr;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getDocStoreId() {
        return docStoreId;
    }

    public void setDocStoreId(String docStoreId) {
        this.docStoreId = docStoreId;
    }

    public String getDocText() {
        return docText;
    }
    public void setDocText(String docText) {
        this.docText = docText;
    }
    public String getCategory() {
        return category;
    }
    public void setCategory(String category) {
        this.category = category;
    }
    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
