package neo4japi.service;

import neo4japi.domain.Facility;

public class FacilityServiceImpl extends GenericService<Facility> implements FacilityService {

    @Override
    Class<Facility> getEntityType() {
        return Facility.class;
    }
}
