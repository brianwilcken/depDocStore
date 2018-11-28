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
import nlp.NLPTools;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.solr.common.SolrDocument;
import org.neo4j.ogm.cypher.BooleanOperator;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.Filters;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.typeconversion.CompositeAttributeConverter;
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

//    public static void main(String[] args) {
//        Neo4jClient client = new Neo4jClient();
//
//        SolrDocument solrDoc = new SolrDocument();
//        solrDoc.setField("filename", "facility_document.pdf");
//        solrDoc.setField("id", "test");
//
//        Document doc = client.addDocument(solrDoc);
//
//        Facility dependentFacility = new Facility("Dependent", "Salt Lake Valley, Salt Lake County, Utah, United States", 40.76661, -111.96744);
//        Facility providingFacility = new Facility("Providing", "Salt Lake Valley, Salt Lake County, Utah, United States", 40.76661, -111.96744);
//
//        dependentFacility = client.addFacility(dependentFacility);
//        providingFacility = client.addFacility(providingFacility);
//
//        client.addDependency(dependentFacility, providingFacility, "receives water from");
//
//        doc.getFacilities().add(dependentFacility);
//        doc.getFacilities().add(providingFacility);
//
//        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();
//        session.save(doc);
//    }

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

    public void addDependencies(Document doc, List<GeoNameWithFrequencyScore> geoNames, List<EntityRelation> relations) {
        GeoNameWithFrequencyScore optimalGeoLocation = LocationResolver.getOptimalGeoLocation(geoNames);
        List<Dependency> dependencies = relations.stream()
                .map(p -> p.mutateForNeo4j(optimalGeoLocation, geoNames))
                .collect(Collectors.toList());

        for (Dependency dependency : dependencies) {
            Facility dependentFacility = addFacility(dependency.getDependentFacility(), geoNames);
            Facility providingFacility = addFacility(dependency.getProvidingFacility(), geoNames);

            addDependency(dependentFacility, providingFacility, dependency.getRelation());

            doc.getFacilities().add(dependentFacility);
            doc.getFacilities().add(providingFacility);

            Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();
            session.save(doc);
        }
    }

    public Dependency addDependency(Facility dependentFacility, Facility providingFacility, String relation) {
        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();

        List<Dependency> dependencies = Lists.newArrayList(session.query(Dependency.class, "MATCH p=(f:Facility)-[r:Dependent_On]->(g:Facility) WHERE f.UUID = \"" + dependentFacility.getUUID() + "\" AND g.UUID = \"" + providingFacility.getUUID() + "\" AND r.relation = \"" + relation + "\" RETURN r", Collections.EMPTY_MAP));

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

    public Facility addFacility(Facility facility, List<GeoNameWithFrequencyScore> geoNames) {
        final String facilityName = facility.getName();
        final String facilityCity = facility.getCity();
        final String facilityCounty = facility.getCounty();
        final String facilityState = facility.getState();
        final double facilityLat = facility.getLatitude();
        final double facilityLon = facility.getLongitude();
        final double minSimilarityThreshold = 0.6;
        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();

        //Resolve the set of facility nodes in the database down to a single matching node according to the facility parameter's attributes
        //search the database for the set of nodes that matches the name attribute of the facility
        Filters filters = new Filters();
        Filter containingFilter = new Filter("name", ComparisonOperator.CONTAINING, facility.getName());
        Filter likeFilter = new Filter("name", ComparisonOperator.LIKE, facility.getName());
        likeFilter.setBooleanOperator(BooleanOperator.OR);
        Filter startsWithFilter = new Filter("name", ComparisonOperator.STARTING_WITH, facility.getName());
        startsWithFilter.setBooleanOperator(BooleanOperator.OR);
        Filter endsWithFilter = new Filter("name", ComparisonOperator.ENDING_WITH, facility.getName());
        endsWithFilter.setBooleanOperator(BooleanOperator.OR);
        filters.add(containingFilter);
        filters.add(likeFilter);
        filters.add(startsWithFilter);
        filters.add(endsWithFilter);
        Collection<Facility> nameMatched = session.loadAll(Facility.class, filters);
        //if any name-matching nodes are discovered then match on other attributes to find an exact or close-enough match
        if (nameMatched.size() > 0) {
            //first filter to find an exact match
            List<Facility> exactMatches = nameMatched.stream()
                    .filter(p -> p.getName().equals(facilityName) &&
                            ((facilityCity == null && p.getCity() == null) || (p.getCity() != null && p.getCity().equals(facilityCity))) &&
                            ((facilityCounty == null && p.getCounty() == null) || (p.getCounty() != null && p.getCounty().equals(facilityCounty))) &&
                            ((facilityState == null && p.getState() == null) || (p.getState() != null && p.getState().equals(facilityState))) &&
                            p.getLatitude() == facilityLat &&
                            p.getLongitude() == facilityLon)
                    .collect(Collectors.toList());

            //if an exact match is found then return this
            if (exactMatches.size() > 0) {
                return exactMatches.get(0);
            } else {
                //No exact match found, but one or more nodes with the same or similar facility name is available.
                //The document originating this facility includes a set of geonames.
                //First check to see if any of these geonames is an exact match for any of the name-matched facility locations.
                List<Facility> nameAndLocMatched = new ArrayList<>();
                for (Facility nameFacility : nameMatched) {
                    final double nameLat = nameFacility.getLatitude();
                    final double nameLon = nameFacility.getLongitude();
                    List<GeoNameWithFrequencyScore> locMatched = geoNames.stream()
                            .filter(p -> p.getGeoName().getLongitude() == nameLon && p.getGeoName().getLatitude() == nameLat)
                            .collect(Collectors.toList());
                    if (locMatched.size() > 0) {
                        nameAndLocMatched.add(nameFacility);
                    }
                }

                //Now find the name-location-matched facility having a name that is the closest to this facility's name.
                if (nameAndLocMatched.size() > 0) {
                    Optional<Facility> closestName = nameAndLocMatched.stream().max(Comparator.comparingDouble(p -> NLPTools.similarity(facilityName, p.getName())));
                    if (closestName.isPresent()) {
                        Facility closestFacility = closestName.get();
                        double similarity = NLPTools.similarity(facilityName, closestFacility.getName());
                        if (similarity >= minSimilarityThreshold) {
                            return closestFacility;
                        }
                    }
                }

                //None of the document geonames is an exact match for any of the name-matched facilities.
                //However, it may be possible to find a close-enough location match through clustering of the available geonames with the name-matched facilities.
                //Cluster this set of geonames against the set of name-matching facilities in the database.
                //After clustering, if any name-matched facilities remain then one of these is probably a close-enough match.
                List<Clusterable> geoClustering = new ArrayList<>(nameMatched);
                geoClustering.addAll(geoNames);

                //Cluster using a small search radius, so as to limit the likelihood of a false-positive
                List<Clusterable> clustered = LocationResolver.getValidGeoCoordinatesByClustering(geoClustering, 0.5, 2, 0.25, 4, 0);

                //check if any of the clustered objects is a facility - if so then this is probably a close-enough match
                if (clustered.size() > 0) {
                    List<Facility> closeMatches = clustered.stream()
                            .filter(p -> p instanceof Facility)
                            .map(p -> (Facility)p)
                            .collect(Collectors.toList());

                    if (closeMatches.size() > 0) {
                        Optional<Facility> closestMatch = closeMatches.stream().max(Comparator.comparingDouble(p -> NLPTools.similarity(facilityName, p.getName())));
                        if (closestMatch.isPresent()) {
                            Facility closestFacility = closestMatch.get();
                            double similarity = NLPTools.similarity(facilityName, closestFacility.getName());
                            if (similarity >= minSimilarityThreshold) {
                                return closestMatch.get();
                            }
                        }
                    }
                }
            }
        }
        //if all else fails then create a new facility node
        facility = facilityService.createOrUpdate(facility);
        return facility;
    }
}
