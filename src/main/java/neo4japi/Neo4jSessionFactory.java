package neo4japi;

import neo4japi.domain.Dependency;
import neo4japi.domain.Document;
import neo4japi.domain.Facility;
import neo4japi.domain.Reference;
import neo4japi.service.DocumentServiceImpl;
import neo4japi.service.FacilityServiceImpl;
import org.neo4j.ogm.config.ClasspathConfigurationSource;
import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;

import java.util.Collection;

public class Neo4jSessionFactory {

    private static ClasspathConfigurationSource configurationSource =
            new ClasspathConfigurationSource("ogm.properties");
    private static Configuration configuration = new Configuration.Builder(configurationSource).build();
    private static SessionFactory sessionFactory = new SessionFactory(configuration, "neo4japi.domain");
    private static Neo4jSessionFactory factory = new Neo4jSessionFactory();

    public static Neo4jSessionFactory getInstance() {
        return factory;
    }

    private Neo4jSessionFactory() {
    }

    public Session getNeo4jSession() {
        return sessionFactory.openSession();
    }

    public static void main(String[] args) {
        DocumentServiceImpl documentService = new DocumentServiceImpl();
//
        Document doc = new Document("testId", "test_document");
//
        documentService.createOrUpdate(doc);

//        Document doc = documentService.find(0L);
////
//        FacilityServiceImpl facilityService = new FacilityServiceImpl();
////
//        Facility reservoir = facilityService.find(40L);

        //Facility reservoir = new Facility("Jordanelle Reservoir", "Salt Lake City, Utah");
//
        //reservoir = facilityService.createOrUpdate(reservoir);


//
//        Reference ref = new Reference(doc, reservoir);
////
//        Session session = sessionFactory.openSession();
//
//        //Facility treatmentPlant = (Facility) session.loadAll(Facility.class, new Filter("name", ComparisonOperator.CONTAINING, "JV")).toArray()[0];
////
//        session.save(ref);

        //Dependency dependency  = new Dependency(treatmentPlant, reservoir);

        //session.save(dependency);
    }
}
