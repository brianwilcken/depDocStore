package mongoapi;

import com.mongodb.Block;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.*;
import com.mongodb.client.gridfs.model.*;
import common.Tools;
import nlp.EventCategorizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.io.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

public class DocStoreMongoClient {

    final static Logger logger = LogManager.getLogger(DocStoreMongoClient.class);

    private MongoClient client;
    private MongoDatabase database;
    private GridFSBucket gridFSBucket;

    private static String temporaryFileRepo = Tools.getProperty("mongodb.temporaryFileRepo");

    public static void main(String[] args) {
        temporaryFileRepo = "C:/tempFileRepo/";
        DocStoreMongoClient docStoreMongoClient = new DocStoreMongoClient("mongodb://localhost:27017");
        //docStoreMongoClient.DeleteFile("5b90260167c3ce2808878c7a");
        //docStoreMongoClient.StoreFile("apache-tomcat-9.0.6.zip");
        GridFSFile file = docStoreMongoClient.GetFileMetadata("5b9036c667c3ce2fdc57a42a");
        FileInputStream responseStream = docStoreMongoClient.DownloadFileToStream(file);
    }

    public DocStoreMongoClient(String mongoDbUrl) {
        client = MongoClients.create(mongoDbUrl);
        database = client.getDatabase("depDocStore");
        gridFSBucket = GridFSBuckets.create(database);
    }

    public ObjectId StoreFile(String filename) {
        try {
            File fileToUpload = new File(temporaryFileRepo + filename);
            InputStream streamToUploadFrom = new FileInputStream(fileToUpload);
            // Create some custom options
            String contentType = Files.probeContentType(fileToUpload.toPath());
            GridFSUploadOptions options = new GridFSUploadOptions()
                    .metadata(new Document("type", contentType));

            ObjectId fileId = gridFSBucket.uploadFromStream(filename, streamToUploadFrom, options);
            return fileId;
        } catch (IOException e){
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    public void DeleteFile(String fileIdStr) {
        ObjectId fileId = new ObjectId(fileIdStr);
        gridFSBucket.delete(fileId);
    }

    public GridFSFile GetFileMetadata(String fileIdStr) {
        ObjectId fileId = new ObjectId(fileIdStr);

        List<GridFSFile> files = new ArrayList<>();

        gridFSBucket.find(eq(fileId)).forEach(
                new Block<GridFSFile>() {
                    public void apply(final GridFSFile gridFSFile) {
                        files.add(gridFSFile);
                    }
                });

        if (files.size() > 0) {
            return files.get(0);
        } else {
            return null;
        }
    }

    public FileInputStream DownloadFileToStream(GridFSFile file) {
        if (file != null) {
            try {
                //first download the file from mongoDB and write it to a temporary location
                FileOutputStream downloadStream = new FileOutputStream(temporaryFileRepo + file.getFilename());
                gridFSBucket.downloadToStream(file.getId(), downloadStream);
                downloadStream.close();

                //read the file into an input stream for the consumer
                FileInputStream responseStream = new FileInputStream(temporaryFileRepo + file.getFilename());
                return responseStream;
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                return null;
            }
        } else {
            return null;
        }
    }
}
