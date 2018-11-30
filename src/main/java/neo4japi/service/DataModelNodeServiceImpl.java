package neo4japi.service;

import neo4japi.domain.DataModelNode;

public class DataModelNodeServiceImpl extends GenericService<DataModelNode> implements DataModelNodeService {

    @Override
    Class<DataModelNode> getEntityType() {
        return DataModelNode.class;
    }
}
