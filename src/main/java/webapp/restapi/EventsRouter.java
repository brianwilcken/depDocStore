package webapp.restapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class EventsRouter {

	@Bean
	public RouterFunction<ServerResponse> refreshEventsFromEventRegistryRoute(EventsService eventsService) {
		return RouterFunctions.route(RequestPredicates.GET("/refreshEventsFromEventRegistry").and(RequestPredicates.accept(MediaType.APPLICATION_JSON)), eventsService::refreshEventsFromEventRegistry);
	}
	
	@Bean
	public RouterFunction<ServerResponse> initiateModelTrainingRoute(EventsService eventsService) {
		return RouterFunctions.route(RequestPredicates.GET("/initiateModelTraining").and(RequestPredicates.accept(MediaType.APPLICATION_JSON)), eventsService::initiateModelTraining);
	}
	
	@Bean
	public RouterFunction<ServerResponse> getIndexedEvents(EventsService eventsService) {
		return RouterFunctions.route(RequestPredicates.GET("/getIndexedEvents").and(RequestPredicates.accept(MediaType.APPLICATION_JSON)), eventsService::getIndexedEvents);
	}
	
	@Bean
	public RouterFunction<ServerResponse> getIndexedArticles(EventsService eventsService) {
		return RouterFunctions.route(RequestPredicates.GET("/getIndexedArticles").and(RequestPredicates.accept(MediaType.APPLICATION_JSON)), eventsService::getIndexedArticles);
	}
	
	@Bean
	public RouterFunction<ServerResponse> refreshArticlesFromEventRegistry(EventsService eventsService) {
		return RouterFunctions.route(RequestPredicates.GET("/refreshArticlesFromEventRegistry").and(RequestPredicates.accept(MediaType.APPLICATION_JSON)), eventsService::refreshArticlesFromEventRegistry);
	}
	
	@Bean
	public RouterFunction<ServerResponse> getModelTrainingDataFromEventRegistry(EventsService eventsService) {
		return RouterFunctions.route(RequestPredicates.GET("/getModelTrainingDataFromEventRegistry").and(RequestPredicates.accept(MediaType.APPLICATION_JSON)), eventsService::getModelTrainingDataFromEventRegistry);
	}
}
