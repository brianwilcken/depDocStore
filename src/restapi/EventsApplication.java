package restapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@SpringBootApplication
public class EventsApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventsApplication.class, args);
		
//		WebClient client = WebClient.create("http://localhost:8080");
//		
//		Mono<ClientResponse> result = client.get().uri("/aee160c33e647970ca98348ab24990136f583a57").accept(MediaType.APPLICATION_JSON).exchange();
//		
//		String stuff = result.flatMap(res -> res.bodyToMono(String.class)).block();
//		
//		System.out.println(stuff);
	}
}
