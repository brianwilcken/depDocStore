package webapp.configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import webapp.controllers.EventsController;

@Configuration
public class EventsRouter {

//	@Bean
//	public RouterFunction<ServerResponse> optionsRoute(CorsService corsService) {
//		return RouterFunctions.route(RequestPredicates.OPTIONS("/**").and(RequestPredicates.accept(MediaType.TEXT_PLAIN)), corsService::handleOptionsRequest);
//	}
//	
//	@Bean
//	public RouterFunction<ServerResponse> eventsServiceRoutes(EventsController eventsService) {
//		return RouterFunctions.route(RequestPredicates.GET("/eventNLP/api/refreshEventsFromEventRegistry").and(RequestPredicates.accept(MediaType.TEXT_PLAIN)), eventsService::refreshEventsFromEventRegistry)
//				.andRoute(RequestPredicates.GET("/eventNLP/api/initiateModelTraining").and(RequestPredicates.accept(MediaType.TEXT_PLAIN)), eventsService::initiateModelTraining)
//				.andRoute(RequestPredicates.GET("/eventNLP/api/findSimilarDocuments").and(RequestPredicates.accept(MediaType.TEXT_PLAIN)), eventsService::findSimilarDocuments)
//				.andRoute(RequestPredicates.GET("/eventNLP/api/events").and(RequestPredicates.accept(MediaType.TEXT_PLAIN)), eventsService::getEvents)
//				.andRoute(RequestPredicates.GET("/eventNLP/api/articles").and(RequestPredicates.accept(MediaType.TEXT_PLAIN)), eventsService::getArticles)
//				.andRoute(RequestPredicates.GET("/eventNLP/api/refreshArticlesFromEventRegistry").and(RequestPredicates.accept(MediaType.TEXT_PLAIN)), eventsService::refreshArticlesFromEventRegistry)
//				.andRoute(RequestPredicates.GET("/eventNLP/api/getModelTrainingDataFromEventRegistry").and(RequestPredicates.accept(MediaType.TEXT_PLAIN)), eventsService::getModelTrainingDataFromEventRegistry)
//				.andRoute(RequestPredicates.DELETE("/eventNLP/api/event/{id}").and(RequestPredicates.accept(MediaType.TEXT_PLAIN)), eventsService::deleteEventById)
//				.andRoute(RequestPredicates.POST("/eventNLP/api/event").and(RequestPredicates.accept(MediaType.APPLICATION_JSON)), eventsService::updateEvent);			
//	}
}
