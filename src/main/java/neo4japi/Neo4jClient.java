package neo4japi;

import com.google.common.collect.Lists;
import geoparsing.LocationResolver;
import neo4japi.domain.Dependency;
import neo4japi.domain.Document;
import neo4japi.domain.Facility;
import neo4japi.domain.Reference;
import neo4japi.service.DocumentService;
import neo4japi.service.DocumentServiceImpl;
import neo4japi.service.FacilityService;
import neo4japi.service.FacilityServiceImpl;
import nlp.EntityRelation;
import org.apache.solr.common.SolrDocument;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;
import webapp.models.GeoNameWithFrequencyScore;

import java.util.*;
import java.util.stream.Collectors;

public class Neo4jClient {
    private FacilityService facilityService;
    private DocumentService documentService;

    public Neo4jClient() {
        facilityService = new FacilityServiceImpl();
        documentService = new DocumentServiceImpl();
    }

    public static void main(String[] args) {
        Neo4jClient client = new Neo4jClient();

        SolrDocument solrDoc = new SolrDocument();
        solrDoc.setField("filename", "facility_document.pdf");
        solrDoc.setField("id", "test");

        Document doc = client.addDocument(solrDoc);

        Facility dependentFacility = new Facility("Dependent", "Salt Lake Valley, Salt Lake County, Utah, United States", 40.76661, -111.96744);
        Facility providingFacility = new Facility("Providing", "Salt Lake Valley, Salt Lake County, Utah, United States", 40.76661, -111.96744);

        dependentFacility = client.addFacility(dependentFacility);
        providingFacility = client.addFacility(providingFacility);

        client.addDependency(dependentFacility, providingFacility, "receives water from");

        doc.getFacilities().add(dependentFacility);
        doc.getFacilities().add(providingFacility);

        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();
        session.save(doc);
    }

    public Document addDocument(SolrDocument doc) {
        //verify the document does not already exist in the database
        String filename = doc.get("filename").toString();
        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();
        Collection<Document> docs = session.loadAll(Document.class, new Filter("title", ComparisonOperator.EQUALS, filename));
        if (docs.size() > 0) {
            Document document = (Document)docs.toArray()[0];
            return document;
        } else {
            String solrId = doc.get("id").toString();
            Document document = new Document(solrId, filename);
            document = documentService.createOrUpdate(document);
            return document;
        }
    }

    public void addDependencies(Document doc, GeoNameWithFrequencyScore optimalGeoLocation, List<EntityRelation> relations) {
        List<Dependency> dependencies = relations.stream()
                .map(p -> p.mutateForNeo4j(optimalGeoLocation))
                .collect(Collectors.toList());

        for (Dependency dependency : dependencies) {
            Facility dependentFacility = addFacility(dependency.getDependentFacility());
            Facility providingFacility = addFacility(dependency.getProvidingFacility());

            addDependency(dependentFacility, providingFacility, dependency.getRelation());

            doc.getFacilities().add(dependentFacility);
            doc.getFacilities().add(providingFacility);

            Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();
            session.save(doc);
        }
    }

    public Dependency addDependency(Facility dependentFacility, Facility providingFacility, String relation) {
        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();

        List<Dependency> dependencies = Lists.newArrayList(session.query(Dependency.class, "MATCH p=(f:Facility)-[r:DEPENDS_ON]->(g:Facility) WHERE ID(f) = " + dependentFacility.getId() + " AND r.relation = \"" + relation + "\" RETURN r", Collections.EMPTY_MAP));

        if (dependencies.size() == 0) {
            Dependency dependency = new Dependency();
            dependency.setDependentFacility(dependentFacility);
            dependency.setProvidingFacility(providingFacility);
            dependency.setRelation(relation);

            session.save(dependency);
            return dependency;
        } else {
            return dependencies.get(0);
        }
    }

    public Facility addFacility(Facility facility) {
        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();
        Collection<Facility> facilities = session.loadAll(Facility.class, new Filter("name", ComparisonOperator.EQUALS, facility.getName()));
        if (facilities.size() > 0) {
            //in case there are multiple facilities with the same name we need to determine if the facility to be added has already been added to Neo4j
            List<Facility> geoClustering = new ArrayList<>(facilities);
            geoClustering.add(facility);

            List<Facility> clustered = LocationResolver.getValidGeoCoordinatesByClustering(geoClustering, 0.1, 2, 0.1, 3, 0);
            if (clustered.size() == 0 || !clustered.contains(facility)) {
                facility = facilityService.createOrUpdate(facility);
            } else {
                final String facilityName = facility.getName();
                final String facilityLoc = facility.getLocation();
                final double facilityLat = facility.getLatitude();
                final double facilityLon = facility.getLongitude();
                facility = facilities.stream()
                        .filter(p -> p.getName().equals(facilityName) && p.getLocation().equals(facilityLoc) && p.getLatitude() == facilityLat && p.getLongitude() == facilityLon)
                        .collect(Collectors.toList()).get(0);
            }

            return facility;
        } else {
            facility = facilityService.createOrUpdate(facility);
            return facility;
        }
    }
}
