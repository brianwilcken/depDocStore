package webapp.services;

import common.Tools;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import webapp.controllers.DocumentsController;
import webapp.models.JsonResponse;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Future;

@Service
public class BulkUploadService {
    final static Logger logger = LogManager.getLogger(BulkUploadService.class);

    @Autowired
    private DocumentsController controller;

    @Async("processExecutor")
    public Future<ResponseEntity<JsonResponse>> process(Map<String, Object> document) {
        return new AsyncResult<>(controller.uploadJSON(document));
    }
}
