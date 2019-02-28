package neo4japi;

import com.bericotech.clavin.gazetteer.GeoName;
import com.google.common.collect.Lists;
import geoparsing.LocationResolver;
import neo4japi.domain.*;
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

        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();
        //Collection<DataModelNode> dataModelNodes = session.loadAll(DataModelNode.class);

        //List<DataModelNode> dataModelNodes = Lists.newArrayList(session.query(DataModelNode.class, "MATCH (n:DataModelNode) WHERE n.name = \"Coal\" RETURN n", Collections.EMPTY_MAP));

//        List<Facility> facilities = Lists.newArrayList(session.query(Facility.class, "MATCH (f:Facility {city:\"Corpus Christi\"})-[m:IsDataModelNode]->(n:DataModelNode {name:\"Finished Water System\"}) RETURN f", Collections.EMPTY_MAP));
//        for (Facility facility : facilities) {
//            System.out.println(facility.getName());
//        }

        Map<String, List<DataModelNode>> facilityTypes = client.getFacilityTypes("Finished Water System");

        facilityTypes.size();

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
    }

    public Map<String, List<Facility>> getFacilitiesInArea(List<GeoNameWithFrequencyScore> geoNames, String category) {
        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();
        Map<String, List<DataModelNode>> facilityTypes = getFacilityTypes(category);
        GeoNameWithFrequencyScore optimalGeoLocation = LocationResolver.getOptimalGeoLocation(geoNames);
//        GeoName cityGeoName = optimalGeoLocation.getCity(optimalGeoLocation.getGeoName());
//        GeoName countyGeoName = optimalGeoLocation.getCounty(optimalGeoLocation.getGeoName());
//        GeoName stateGeoName = optimalGeoLocation.getState(optimalGeoLocation.getGeoName());

        double maxLat = optimalGeoLocation.getGeoName().getLatitude() + 0.5;
        double minLat = optimalGeoLocation.getGeoName().getLatitude() - 0.5;
        double maxLon = optimalGeoLocation.getGeoName().getLongitude() + 0.5;
        double minLon = optimalGeoLocation.getGeoName().getLongitude() - 0.5;

        String locQuery = "WHERE f.latitude > " + minLat + " AND f.latitude < " + maxLat + " AND f.longitude > " + minLon + " AND f.longitude < " + maxLon;

//        String locQuery = null;
//        if (cityGeoName != null) {
//            locQuery = "city:\"" + cityGeoName.getName() + "\"";
//        } else if (countyGeoName != null) {
//            locQuery = "county:\"" + countyGeoName.getName() + "\"";
//        } else if (stateGeoName != null) {
//            locQuery = "state:\"" + stateGeoName.getName() + "\"";
//        }

        Map<String, List<Facility>> areaFacilities = new HashMap<>();
        //if (locQuery != null) {
        for (String facilityType : facilityTypes.keySet()) {
            if (!areaFacilities.containsKey(facilityType)) {
                areaFacilities.put(facilityType, new ArrayList<>());
            }
            //areaFacilities.get(facilityType).addAll(Lists.newArrayList(session.query(Facility.class, "MATCH (f:Facility {" + locQuery + "})-[m:IsDataModelNode]->(n:DataModelNode {name:\"" + facilityType + "\"}) RETURN f", Collections.EMPTY_MAP)));
            areaFacilities.get(facilityType).addAll(Lists.newArrayList(session.query(Facility.class, "MATCH (f:Facility)-[m:IsDataModelNode]->(n:DataModelNode {name:\"" + facilityType + "\"}) " + locQuery + " RETURN f", Collections.EMPTY_MAP)));
        }
        //}

        return areaFacilities;
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

    //we want to relate the document to one or more data model nodes according to the document's category(s)
    public List<DataModelRelation> addDataModelNodeDocumentRelation(SolrDocument solrDoc, Document doc) {
        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();
        List<String> categories = (List<String>)solrDoc.get("category");

        //get the DataModelNode that corresponds with the solr document's category
        List<DataModelNode> dataModelNodes = new ArrayList<>();
        for (String category : categories) {
            String cat = category.replace("_", " ");
            dataModelNodes.addAll(Lists.newArrayList(session.query(DataModelNode.class, "MATCH (n:DataModelNode) WHERE n.name = \"" + cat + "\" RETURN n", Collections.EMPTY_MAP)));
        }

        if (dataModelNodes.size() > 0) {
            List<DataModelRelation> relations = new ArrayList<>();
            for (DataModelNode dataModelNode : dataModelNodes) {
                //determine if the document has a relation to the data model node
                List<DataModelRelation> nodeRelations = (Lists.newArrayList(session.query(DataModelRelation.class, "MATCH p=(d:Document)-[r:IsDataModelNode]->(n:DataModelNode) WHERE d.UUID = \"" + doc.getUUID() + "\" AND n.UUID = \"" + dataModelNode.getUUID() + "\" RETURN r", Collections.EMPTY_MAP)));

                if (nodeRelations.size() == 0) {
                    DataModelRelation relation = new DataModelRelation(doc, dataModelNode);
                    session.save(relation);
                }
                relations.addAll(nodeRelations);
            }

            return relations;
        } else {
            return null;
        }
    }

    public Map<EntityRelation, Dependency> getNominalDependencies(List<GeoNameWithFrequencyScore> geoNames, List<EntityRelation> relations) {
        GeoNameWithFrequencyScore optimalGeoLocation = LocationResolver.getOptimalGeoLocation(geoNames);
        Map<EntityRelation, Dependency> dependencies = relations.stream()
                .collect(Collectors.toMap(p -> p, p -> p.mutateForNeo4j(optimalGeoLocation, geoNames)));

        return dependencies;
    }

    public void addDependencies(Document doc, List<GeoNameWithFrequencyScore> geoNames, List<EntityRelation> relations) {
        Map<EntityRelation, Dependency> dependencies = getNominalDependencies(geoNames, relations);
        for (Map.Entry<EntityRelation, Dependency> dependency : dependencies.entrySet()) {
            Facility dependentFacility = dependency.getValue().getDependentFacility();
            Facility providingFacility = dependency.getValue().getProvidingFacility();
            String dependentDataModelNodeName = dependentFacility.getDataModelNode().replace("_", " ");
            String providingDataModelNodeName = providingFacility.getDataModelNode().replace("_", " ");
            dependentFacility = addFacility(dependentFacility, geoNames);
            providingFacility = addFacility(providingFacility, geoNames);

            addDependency(dependentFacility, providingFacility, dependency.getValue().getRelation(), doc.getUUID());

            addDataModelNodeFacilityRelation(dependentFacility, dependentDataModelNodeName);
            addDataModelNodeFacilityRelation(providingFacility, providingDataModelNodeName);

            doc.getFacilities().add(dependentFacility);
            doc.getFacilities().add(providingFacility);

            Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();
            session.save(doc);
        }
    }

    public Dependency addDependency(Facility dependentFacility, Facility providingFacility, String linkUUID, String docUUID) {
        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();

        List<Dependency> dependencies = Lists.newArrayList(session.query(Dependency.class, "MATCH p=(f:Facility)-[r:Dependent_On]->(g:Facility) WHERE f.UUID = \"" + dependentFacility.getUUID() + "\" AND g.UUID = \"" + providingFacility.getUUID() + "\" AND r.dependencyTypeId = \"" + linkUUID + "\" AND r.documentUUID = \"" + docUUID + "\" RETURN r", Collections.EMPTY_MAP));

        if (dependencies.size() == 0) {
            Dependency dependency = new Dependency();
            dependency.setDependentFacility(dependentFacility);
            dependency.setProvidingFacility(providingFacility);
            dependency.setDependencyTypeId(linkUUID);
            dependency.setDocumentUUID(docUUID);

            session.save(dependency);
            return dependency;
        } else {
            return dependencies.get(0);
        }
    }

    public DataModelRelation addDataModelNodeFacilityRelation(Facility facility, String dataModelNodeName) {
        //prevent facilities from accidentally being assigned multiple data model nodes
        if (facility.getDataModelNode() != null && !facility.getDataModelNode().equals(dataModelNodeName)) {
            return null;
        }
        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();

        //get the DataModelNode that corresponds with the named entity's type
        List<DataModelNode> dataModelNodes = Lists.newArrayList(session.query(DataModelNode.class, "MATCH (n:DataModelNode) WHERE n.name = \"" + dataModelNodeName + "\" RETURN n", Collections.EMPTY_MAP));

        if (dataModelNodes.size() > 0) {
            DataModelNode dataModelNode = dataModelNodes.get(0);

            List<DataModelRelation> relations = Lists.newArrayList(session.query(DataModelRelation.class, "MATCH p=(f:Facility)-[r:IsDataModelNode]->(n:DataModelNode) WHERE f.UUID = \"" + facility.getUUID() + "\" AND n.UUID = \"" + dataModelNode.getUUID() + "\" RETURN r", Collections.EMPTY_MAP));

            if (relations.size() == 0) {
                DataModelRelation relation = new DataModelRelation(facility, dataModelNode);

                session.save(relation);
                return relation;
            } else {
                return relations.get(0);
            }
        } else {
            return null;
        }
    }

    public Map<String, List<DataModelNode>> getFacilityTypes(String typeName) {
        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();

        List<DataModelNode> types = Lists.newArrayList(session.query(DataModelNode.class, "MATCH (n:DataModelNode {name:\"" + typeName + "\"} )-[r:ParentOf]->(m:DataModelNode) RETURN m ORDER BY m.name", Collections.EMPTY_MAP));
        Map<String, List<DataModelNode>> typeMap = new HashMap<>();
        typeMap.put(typeName, types);

        for (DataModelNode type : types) {
            typeMap.putAll(getFacilityTypes(type.getName()));
        }

        return typeMap;
    }

    public Map<String, List<DataModelLink>> getTradableAssets(String linkName) {
        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();
        //List<DataModelLink> assets = Lists.newArrayList(session.query(DataModelLink.class, "MATCH (n:DataModelLink) WHERE NOT (n:DataModelLink)-[:ParentOf]->() RETURN n ORDER BY n.name", Collections.EMPTY_MAP));
        //List<DataModelLink> assets = Lists.newArrayList(session.query(DataModelLink.class, "MATCH (n:DataModelLink)<-[:ChildOf]-(m:DataModelLink) RETURN m ORDER BY m.name", Collections.EMPTY_MAP));

        List<DataModelLink> assets = Lists.newArrayList(session.query(DataModelLink.class, "MATCH (n:DataModelLink {name:\"" + linkName + "\"} )-[r:ParentOf]->(m:DataModelLink) RETURN m ORDER BY m.name", Collections.EMPTY_MAP));
        Map<String, List<DataModelLink>> assetMap = new HashMap<>();
        assetMap.put(linkName, assets);

        for (DataModelLink asset : assets) {
            assetMap.putAll(getTradableAssets(asset.getName()));
        }

        return assetMap;
    }

    public DataModelLink getTradedAsset(String providingFacilityUUID, String dependentFacilityUUID) {
        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();
        List<DataModelLink> assets = Lists.newArrayList(session.query(DataModelLink.class, "MATCH (n:Facility)-[r:Dependent_On]->(m:Facility) WHERE n.UUID = \"" + dependentFacilityUUID + "\" AND m.UUID = \"" + providingFacilityUUID + "\" RETURN r", Collections.EMPTY_MAP));

        if (assets.size() > 0) {
            return assets.get(0);
        } else {
            return null;
        }
    }

    public Document addDocumentFacilitiesRelation(Document document, Facility providingFacility, Facility dependentFacility) {
        document.getFacilities().add(dependentFacility);
        document.getFacilities().add(providingFacility);

        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();
        session.save(document);

        return document;
    }

    public Facility addFacility(Facility facility) {
        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();

        List<Facility> facilities = Lists.newArrayList(session.query(Facility.class, "MATCH (f:Facility) WHERE f.UUID = \"" + facility.getUUID() + "\" RETURN f", Collections.EMPTY_MAP));

        if (facilities.size() == 0) {
            return facilityService.createOrUpdate(facility);
        } else {
            return facilities.get(0);
        }
    }

    public Facility addFacility(Facility facility, List<GeoNameWithFrequencyScore> geoNames) {
        final String facilityName = facility.getName();
        final String facilityCity = facility.getCity();
        final String facilityCounty = facility.getCounty();
        final String facilityState = facility.getState();
        final String facilityType = facility.getDataModelNode().replace("_", " ");
        final double facilityLat = facility.getLatitude();
        final double facilityLon = facility.getLongitude();
        final double minSimilarityThreshold = 0.8;
        Session session = Neo4jSessionFactory.getInstance().getNeo4jSession();

        //as an initial step, check if this facility has already been added to the database by referencing the UUID
        List<Facility> alreadyAdded = Lists.newArrayList(session.query(Facility.class, "MATCH (f:Facility) WHERE f.UUID = \"" + facility.getUUID() + "\" RETURN f", Collections.EMPTY_MAP));
        if (alreadyAdded.size() > 0) {
            return alreadyAdded.get(0);
        }

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
        for (Facility foundFacility : nameMatched) {
            List<DataModelNode> dataModelNodes = Lists.newArrayList(session.query(DataModelNode.class, "MATCH p=(f:Facility)-[r:IsDataModelNode]->(n:DataModelNode) WHERE f.UUID = \"" + foundFacility.getUUID() + "\" RETURN n", Collections.EMPTY_MAP));
            if (dataModelNodes.size() > 0) {
                foundFacility.setDataModelNode(dataModelNodes.get(0).getName());
            }
        }

        //if any name-matching nodes are discovered then match on other attributes to find an exact or close-enough match
        if (nameMatched.size() > 0) {
            //first filter to find an exact match
            List<Facility> exactMatches = nameMatched.stream()
                    .filter(p -> p.getName().equals(facilityName) &&
                            ((facilityCity == null && p.getCity() == null) || (p.getCity() != null && p.getCity().equals(facilityCity))) &&
                            ((facilityCounty == null && p.getCounty() == null) || (p.getCounty() != null && p.getCounty().equals(facilityCounty))) &&
                            ((facilityState == null && p.getState() == null) || (p.getState() != null && p.getState().equals(facilityState))) &&
                            ((facilityType == null && p.getDataModelNode() == null) || (p.getDataModelNode() != null && p.getDataModelNode().equals(facilityType))) &&
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
                    Optional<Facility> closestName = nameAndLocMatched.stream().max(Comparator.comparingDouble(p -> getSimilarityConfidenceScore(p, facilityName, facilityType, facilityLat, facilityLon)));
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
                        Optional<Facility> closestMatch = closeMatches.stream().max(Comparator.comparingDouble(p -> getSimilarityConfidenceScore(p, facilityName, facilityType, facilityLat, facilityLon)));
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
        facility.setDataModelNode(null);
        facility = facilityService.createOrUpdate(facility);
        return facility;
    }

    public Map<Double, Facility> getMatchingFacilities(Facility facility, List<GeoNameWithFrequencyScore> geoNames) {
        Map<Double, Facility> facilities = new HashMap<>();

        final String facilityName = facility.getName();
        final String facilityCity = facility.getCity();
        final String facilityCounty = facility.getCounty();
        final String facilityState = facility.getState();
        final String facilityType = facility.getDataModelNode().replace("_", " ");
        final double facilityLat = facility.getLatitude();
        final double facilityLon = facility.getLongitude();
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
        for (Facility foundFacility : nameMatched) {
            List<DataModelNode> dataModelNodes = Lists.newArrayList(session.query(DataModelNode.class, "MATCH p=(f:Facility)-[r:IsDataModelNode]->(n:DataModelNode) WHERE f.UUID = \"" + foundFacility.getUUID() + "\" RETURN n", Collections.EMPTY_MAP));
            if (dataModelNodes.size() > 0) {
                foundFacility.setDataModelNode(dataModelNodes.get(0).getName());
            }
        }

        //if any name-matching nodes are discovered then match on other attributes to find an exact or close-enough match
        if (nameMatched.size() > 0) {
            //first filter to find an exact match
            List<Facility> exactMatches = nameMatched.stream()
                    .filter(p -> p.getName().equals(facilityName) &&
                            ((facilityCity == null && p.getCity() == null) || (p.getCity() != null && p.getCity().equals(facilityCity))) &&
                            ((facilityCounty == null && p.getCounty() == null) || (p.getCounty() != null && p.getCounty().equals(facilityCounty))) &&
                            ((facilityState == null && p.getState() == null) || (p.getState() != null && p.getState().equals(facilityState))) &&
                            ((facilityType == null && p.getDataModelNode() == null) || (p.getDataModelNode() != null && p.getDataModelNode().equals(facilityType))) &&
                            p.getLatitude() == facilityLat &&
                            p.getLongitude() == facilityLon)
                    .collect(Collectors.toList());

            //if an exact match is found then return this
            if (exactMatches.size() > 0) {
                facilities.put(1.0, exactMatches.get(0));
                return facilities;
            } else {
                //No exact match found, but one or more nodes with the same or similar facility name is available.
                //The document originating this facility includes a set of geonames.
                //First check to see if any of these geonames is an exact match for any of the name-matched facility locations.
                List<Facility> similarNameExactLatLon = new ArrayList<>();
                for (Facility nameFacility : nameMatched) {
                    final double nameLat = nameFacility.getLatitude();
                    final double nameLon = nameFacility.getLongitude();
                    List<GeoNameWithFrequencyScore> locMatched = geoNames.stream()
                            .filter(p -> p.getGeoName().getLongitude() == nameLon && p.getGeoName().getLatitude() == nameLat)
                            .collect(Collectors.toList());
                    if (locMatched.size() > 0) {
                        similarNameExactLatLon.add(nameFacility);
                    }
                }

                //Now find the name-location-matched facility having a name that is the closest to this facility's name.
                if (similarNameExactLatLon.size() > 0) {
                    facilities.putAll(similarNameExactLatLon.stream().collect(Collectors.toMap(p -> getSimilarityConfidenceScore(p, facilityName, facilityType, facilityLat, facilityLon), p -> p)));
                    return facilities;
                }

                //None of the document geonames is an exact match for any of the name-similarity-matched facilities.
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
                        facilities.putAll(closeMatches.stream().collect(Collectors.toMap(p -> getSimilarityConfidenceScore(p, facilityName, facilityType, facilityLat, facilityLon), p -> p)));
                    }
                }
            }
        }

        return facilities;
    }

    private double getSimilarityConfidenceScore(Facility facility, final String testName, final String testType, final double testLat, final double testLon) {
        double nameConfidenceScore = 2 * NLPTools.similarity(testName, facility.getName()); //name similarity importance is weighted by a factor of 2
        double typeConfidenceScore = 4 * NLPTools.similarity(testType, facility.getDataModelNode()); //type similarity importance is weighted by a factor of 4
        double latConfidenceScore = 1 - (Math.abs(facility.getLatitude() - testLat)/facility.getLatitude());
        double lonConfidenceScore = 1 - (Math.abs(facility.getLongitude() - testLon)/facility.getLongitude());

        //produce a normalized confidence score
        double degOfConfidence = (nameConfidenceScore + typeConfidenceScore + latConfidenceScore + lonConfidenceScore) / 8;

        if (degOfConfidence > 1) {
            degOfConfidence = 1.0;
        }

        return degOfConfidence;
    }
}
