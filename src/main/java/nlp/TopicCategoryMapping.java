package nlp;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.Strings;

import java.util.List;
import java.util.Map;

@JsonPropertyOrder({ "topic", "category1", "weight1", "category2", "weight2", "category3", "weight3" })
public class TopicCategoryMapping {
    public String topic;
    public String category1;
    public int weight1;
    public String category2;
    public int weight2;
    public String category3;
    public int weight3;

    public void updateCategoryWeight(List<CategoryWeight> categoryWeight, double probability) {
        updateForSpecificCategory(category1, weight1, categoryWeight, probability);
        if (!Strings.isNullOrEmpty(category2)) {
            updateForSpecificCategory(category2, weight2, categoryWeight, probability);
        }
        if (!Strings.isNullOrEmpty(category3)) {
            updateForSpecificCategory(category3, weight3, categoryWeight, probability);
        }
    }

    private void updateForSpecificCategory(String category, final int weight, List<CategoryWeight> categoryWeights, final double probability) {
        if(categoryWeights.stream().anyMatch(p -> p.category.equals(category))) {
            categoryWeights.stream().filter(p -> p.category.equals(category)).forEach(p -> {
                p.catWeight += (probability * weight);
            });
        } else {
            CategoryWeight categoryWeight = new CategoryWeight();
            categoryWeight.category = category;
            categoryWeight.catWeight = (probability * weight);
            categoryWeights.add(categoryWeight);
        }
    }
}
