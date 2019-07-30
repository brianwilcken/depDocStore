package solrapi;

import common.Tools;
import nlp.CategoryWeight;
import nlp.NLPTools;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.util.SimpleOrderedMap;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

public class DoccatThrottle extends TrainingDataThrottle {
    final static Logger logger = LogManager.getLogger(DoccatThrottle.class);
    private long numDocs;
    private double throttlePercent;
    private Map<String, RandomizationTracker> throttleTracker;

    private static class RandomizationTracker {
        private double randomPercent;
        private int count;

        public RandomizationTracker(double randomPercent, int count) {
            this.randomPercent = randomPercent;
            this.count = count;
        }

        public double getRandomPercent() {
            return randomPercent;
        }

        public void setRandomPercent(double randomPercent) {
            this.randomPercent = randomPercent;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    public DoccatThrottle(SolrClient client, Function<SolrQuery, SolrQuery> queryGetter) {
        SolrQuery query = queryGetter.apply(new SolrQuery());
        try {
            SimpleOrderedMap<?> categoryCounts = client.QueryFacets(query.getQuery(), "{categories:{type:terms,field:category,limit:10000}}");

            Map<String, Integer> categoryMap = ((ArrayList<SimpleOrderedMap<?>>)((SimpleOrderedMap<?>)categoryCounts.get("categories")).get("buckets"))
                    .stream()
                    .collect(Collectors.toMap(p -> ((SimpleOrderedMap<?>)p).get("val").toString(), p -> Integer.parseInt(((SimpleOrderedMap<?>)p).get("count").toString())));

            throttleTracker = categoryMap.entrySet().stream().collect(Collectors.toMap(p -> p.getKey(), p -> new RandomizationTracker (0, p.getValue())));



        } catch (SolrServerException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void init(long numDocs) {
        this.numDocs = numDocs;

        double[] counts = throttleTracker.entrySet().stream()
                .map(p -> p.getValue().getCount())
                .mapToDouble(p -> p.doubleValue())
                .toArray();

        double harmonicMean = Tools.harmonicMean(counts);
        //double sum = DoubleStream.of(counts).sum();
        double min = DoubleStream.of(counts).min().getAsDouble();
        throttlePercent = harmonicMean / numDocs;

        //calculate randomizers
        for (Map.Entry<String, RandomizationTracker> entry : throttleTracker.entrySet()) {
            RandomizationTracker tracker = entry.getValue();
            double randomPercent = 4 * (min / (double)tracker.getCount());
            tracker.setRandomPercent(randomPercent);  //at this stage count is the full count of documents for the category
            tracker.setCount(0); //reset the count for throttling later...
        }
    }

    @Override
    public boolean check(SolrDocument doc) {
        if (doc.containsKey("category")) {
            List categories = (List)doc.get("category");
            List<CategoryWeight> catWeights = null;
            if (doc.containsKey("ldaCategory")) {
                List<String> ldaCategories = (List<String>)doc.get("ldaCategory");
                catWeights = NLPTools.separateProbabilitiesFromCategories(ldaCategories);
            } else if (doc.containsKey("doccatCategory")) {
                List<String> doccatCategories = (List<String>)doc.get("doccatCategory");
                catWeights = NLPTools.separateProbabilitiesFromCategories(doccatCategories);
            }

            return check(categories, catWeights);
        }
        return true;
    }

    public boolean check(List category, List<CategoryWeight> catWeights) {
        boolean result = false;
        if (throttleTracker != null) {
            List<Map.Entry<String, RandomizationTracker>> workableCategories = throttleTracker.entrySet().stream()
                    .filter(p -> category.contains(p.getKey()))
                    .filter(p -> ((double)p.getValue().getCount() / (double)numDocs) <= throttlePercent)
                    .sorted(new Comparator<Map.Entry<String, RandomizationTracker>>() {
                        @Override
                        public int compare(Map.Entry<String, RandomizationTracker> entry1, Map.Entry<String, RandomizationTracker> entry2) {
                            return Double.compare(entry2.getValue().getRandomPercent(), entry1.getValue().getRandomPercent());
                        }
                    })
                    .collect(Collectors.toList());

            for (Map.Entry<String, RandomizationTracker> workableCategory : workableCategories) {
                RandomizationTracker randomizationTracker = workableCategory.getValue();
                double random = Math.random();
                double randomPercent = randomizationTracker.getRandomPercent();
                String cat = workableCategory.getKey();
                OptionalDouble weight = catWeights.stream().filter(p -> p.category.equals(cat)).mapToDouble(p -> p.catWeight).findFirst();
                if (weight.isPresent() && weight.getAsDouble() >= 0.5) { //if the category probability is high enough then automatically accept it
                    result = true;
                    break;
                } else if (random >= (1 - randomPercent)) { //if the category probability is low or if the category has no assigned weight then use random chance
                    result = true;
                    break;
                }
            }

            if (result) {
                for (Map.Entry<String, RandomizationTracker> workableCategory : workableCategories) {
                    RandomizationTracker randomizationTracker = workableCategory.getValue();
                    int count = randomizationTracker.getCount();
                    randomizationTracker.setCount(++count);
                }
            }
        } else {
            result = true;
        }
        return result;
    }

//    @Override
//    public boolean check(List category) {
//        if (throttleTracker != null) {
//            List<Map.Entry<String, RandomizationTracker>> workableCategories = throttleTracker.entrySet().stream()
//                    .filter(p -> category.contains(p.getKey()))
//                    .filter(p -> ((double)p.getValue().getCount() / (double)numDocs) <= throttlePercent)
//                    .sorted(new Comparator<Map.Entry<String, RandomizationTracker>>() {
//                        @Override
//                        public int compare(Map.Entry<String, RandomizationTracker> entry1, Map.Entry<String, RandomizationTracker> entry2) {
//                            return Double.compare(entry2.getValue().getRandomPercent(), entry1.getValue().getRandomPercent());
//                        }
//                    })
//                    .collect(Collectors.toList());
//
//            if (workableCategories.size() > 0) {
//                Map.Entry<String, RandomizationTracker> workableCategory = workableCategories.get(0);
//                RandomizationTracker randomizationTracker = workableCategory.getValue();
//                double random = Math.random();
//                double randomPercent = randomizationTracker.getRandomPercent();
//                if (random >= (1 - randomPercent)) {
//                    int count = randomizationTracker.getCount();
//                    randomizationTracker.setCount(++count);
//                    return true;
//                } else {
//                    return false;
//                }
//            } else {
//                return false;
//            }
//        }
//        return true;
//    }
}
