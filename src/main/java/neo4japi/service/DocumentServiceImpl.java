package neo4japi.service;

import neo4japi.domain.Document;

public class DocumentServiceImpl extends GenericService<Document> implements DocumentService {

    @Override
    Class<Document> getEntityType() {
        return Document.class;
    }
}
