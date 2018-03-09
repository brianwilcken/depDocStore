package solrapi;

public class SolrConstants {

	public static class Events {
		//The user has manually adjusted the category for an event.  Events in this state are (happily!) used in model training.
		public static final String CATEGORIZATION_STATE_USER_UPDATED = "U";
		//The system has generated a category for an event using OpenNLP's document categorizer using a trained classification model.  These
		//events are also used in model training.
		public static final String CATEGORIZATION_STATE_MACHINE = "M";
		//The system has performed a broad search of events from the Event Registry site using a search query based on a predefined category.  
		//The events have no practical purpose in the system other than to build a set of training data.
		//Events in this state are not included in model training.  They must first be manually reviewed by a system admin before they can be used further.
		public static final String CATEGORIZATION_STATE_SEARCH_INFERRED = "S";
		
		public static final String EVENT_STATE_NEW = "N";
		public static final String EVENT_STATE_REVIEWED = "R";
		public static final String EVENT_STATE_LINKED = "L";
		public static final String EVENT_STATE_CLOSED = "C";
		public static final String EVENT_STATE_DELETED = "D";
	}
}
