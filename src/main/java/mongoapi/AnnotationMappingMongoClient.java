package mongoapi;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;

import static com.mongodb.client.model.Filters.eq;

public class AnnotationMappingMongoClient {
    final static Logger logger = LogManager.getLogger(AnnotationMappingMongoClient.class);

    private MongoClient client;
    private MongoDatabase database;
    private MongoCollection<Document> collection;

    public static void main(String[] args) {
        AnnotationMappingMongoClient client = new AnnotationMappingMongoClient("mongodb://localhost:27017");
        Document mapping = client.createAnnotationMapping("WTR", "Water");
        //Document mapping = client.getAnnotationMapping("WSTWTR");
        System.out.println("");
    }

    public AnnotationMappingMongoClient(String mongoDbUrl) {
        client = MongoClients.create(mongoDbUrl);
        database = client.getDatabase("annotationMappings");
        collection = database.getCollection("mappings");
    }

    public Document getAnnotationMapping(String annotationType) {
        Document mapping = collection.find(eq("type", annotationType)).first();

        return mapping;
    }

    public Document createAnnotationMapping(String annotation, String dataModelNode) {
        Document mapping = new Document();
        mapping.put("type", annotation);
        mapping.put("mapping", dataModelNode);
        collection.insertOne(mapping);

        return mapping;
    }
}
