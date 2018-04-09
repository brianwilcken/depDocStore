package webapp.controllers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.solr.client.solrj.SolrQuery.SortClause;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import common.Tools;
import eventsregistryapi.model.IndexedEvent;
import nlp.EventCategorizer;
import solrapi.SolrClient;
import solrapi.SolrConstants;

@CrossOrigin
@Controller
public class EventCategorizationController {

	private static SolrClient client = new SolrClient(Tools.getProperty("solr.url"));
	private static EventCategorizer categorizer = new EventCategorizer();
	private static EventsController svc = new EventsController();
	
	@GetMapping("/classify")
	public String getHandler(Model model) {
		model.addAttribute("mode", "N");
		try {
			return getNextEvent(model);
		} catch (SolrServerException e) {
			model.addAttribute("exception", e.getMessage());
			return "error";
		}
	}
	
	@PostMapping("/classify/ReviewEvents")
	public String reviewEventsHandler(Model model) {
		model.addAttribute("mode", "R");
		try {
			return getNextEvent(model);
		} catch (SolrServerException e) {
			model.addAttribute("exception", e.getMessage());
			return "error";
		}
	}
	
	@PostMapping("/classify/Next")
	public String nextHandler(@RequestBody MultiValueMap<String, String> form, Model model) {
		model.addAttribute("mode", "R");
		lockCategory(form, model);
		String id = form.get("id").get(0);
		try {
			return getNextEvent(model, id);
		} catch (SolrServerException e) {
			model.addAttribute("exception", e.getMessage());
			return "error";
		}
	}
	
	@PostMapping("/classify/EndReview")
	public String endReviewHandler(Model model) {
		model.addAttribute("mode", "N");
		try {
			return getNextEvent(model);
		} catch (SolrServerException e) {
			model.addAttribute("exception", e.getMessage());
			return "error";
		}
	}
	
	private void lockCategory(MultiValueMap<String, String> form, Model model) {
		if (model.asMap().get("mode").toString().compareTo("R") == 0) {
			String category = null;
			if (!form.get("newCategory").get(0).isEmpty()) {
				category = form.get("newCategory").get(0);
			} else {
				category = form.get("category").get(0);
			}
			model.addAttribute("category", category);
		}
	}
	
	@PostMapping("/classify")
	public String postHandler(@RequestBody MultiValueMap<String, String> form, Model model) {
		String id = form.get("id").get(0);
		model.addAttribute("mode", form.get("mode").get(0));
		lockCategory(form, model);
		try {
			List<IndexedEvent> indexedEvents = client.QueryIndexedDocuments(IndexedEvent.class, "id:" + id, 1, null);
			if (!indexedEvents.isEmpty()) {
				IndexedEvent indexedEvent = indexedEvents.get(0);
				if (!form.get("newCategory").get(0).isEmpty()) {
					indexedEvent.setCategory(form.get("newCategory").get(0));
				} else {
					indexedEvent.setCategory(form.get("category").get(0));
				}
				indexedEvent.setEventState(SolrConstants.Events.EVENT_STATE_REVIEWED);
				indexedEvent.setCategorizationState(SolrConstants.Events.CATEGORIZATION_STATE_USER_UPDATED);
				client.IndexDocuments(indexedEvents);
				return getNextEvent(model, indexedEvent.getId());
			} else {
				return "error";
			}
		} catch (SolrServerException e) {
			model.addAttribute("exception", e.getMessage());
			return "error";
		}
	}
	
	@PostMapping("/classify/TrainModel")
	public String trainModelPostHandler(Model model) {
		client.WriteEventCategorizationTrainingDataToFile(Tools.getProperty("nlp.doccatTrainingFile"));
		double accuracy = categorizer.TrainEventCategorizationModel(Tools.getProperty("nlp.doccatTrainingFile"));
		model.addAttribute("accuracy", String.format("%.2f%%", 100 * accuracy));
		model.addAttribute("mode", "N");
		return "noMoreEvents";
	}
	
	@PostMapping("/classify/RefreshEvents")
	public String refreshEventsPostHandler(Model model) {
		model.addAttribute("mode", "N");
		svc.refreshEventsFromEventRegistry();
		try {
			return getNextEvent(model);
		} catch (SolrServerException e) {
			model.addAttribute("exception", e.getMessage());
			return "error";
		}
	}
	
	@PostMapping("/classify/SearchEvents")
	public String searchEventsPostHandler(Model model) {
		model.addAttribute("mode", "N");
		svc.getModelTrainingDataFromEventRegistry();
		try {
			return getNextEvent(model);
		} catch (SolrServerException e) {
			model.addAttribute("exception", e.getMessage());
			return "error";
		}
	}
	
	private String getNextEvent(Model model, String... id) throws SolrServerException {
		Random rand = new Random();
		SortClause sort = new SortClause("random_" + Integer.toString(rand.nextInt()), "asc");
		String eventState = model.asMap().get("mode").toString();
		List<IndexedEvent> indexedEvents = null;
		if (model.asMap().containsKey("category")) {
			indexedEvents = client.QueryIndexedDocuments(IndexedEvent.class, "eventState:" + eventState, 2, sort, "category:\"" + model.asMap().get("category") + "\"");
		} else {
			indexedEvents = client.QueryIndexedDocuments(IndexedEvent.class, "eventState:" + eventState, 2, sort);
		}
		if (!indexedEvents.isEmpty()) {
			IndexedEvent indexedEvent = indexedEvents.get(0);
			if (id.length > 0 && indexedEvents.size() > 1 && indexedEvent.getId().compareTo(id[0]) == 0) {
				return getNextEvent(model, id);
			}
			return prepViewModel(model, indexedEvent);
		}
		else {
			return "noMoreEvents";
		}
	}
	
	private String prepViewModel(Model model, IndexedEvent indexedEvent) throws SolrServerException {
		model.addAttribute("id", indexedEvent.getId());
		model.addAttribute("title", indexedEvent.getTitle());
		model.addAttribute("summary", indexedEvent.getSummary());
		model.addAttribute("eventCategory", indexedEvent.getCategory());
		model.addAttribute("categories", getAvailableCategories());
		return "eventCategorization";
	}
	
	private String[] getAvailableCategories() throws SolrServerException {
		List<String> availableCategories = new ArrayList<String>();
		SimpleOrderedMap<?> facets = client.QueryFacets("{categories:{type:terms,field:category}}");
		SimpleOrderedMap<?> categories = (SimpleOrderedMap<?>) facets.get("categories");
		List<?> buckets = (ArrayList<?>) categories.get("buckets");
		for (int i = 0; i < buckets.size(); i++) {
			SimpleOrderedMap<?> nvpair = (SimpleOrderedMap<?>) buckets.get(i);
			String category = (String) nvpair.getVal(0);
			availableCategories.add(category);
		}
		
		String[] catArr = availableCategories.toArray(new String[availableCategories.size()]);
		Arrays.sort(catArr);
		
		return catArr;
	}
}
