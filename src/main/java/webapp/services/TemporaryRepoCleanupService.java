package webapp.services;

import common.Tools;
import mongoapi.DocStoreMongoClient;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
public class TemporaryRepoCleanupService {

    private static File temporaryFileRepo = new File(Tools.getProperty("mongodb.temporaryFileRepo"));

    final static Logger logger = LogManager.getLogger(TemporaryRepoCleanupService.class);

    @Async("processExecutor")
    public void process() {
        try {
            FileUtils.cleanDirectory(temporaryFileRepo);
        } catch (IOException e) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e1) { }
            process();
        }
    }
}
