package webapp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.web.server.adapter.AbstractReactiveWebInitializer;

import webapp.configurations.EventsRouter;
import webapp.configurations.WebConfig;

@SpringBootApplication
public class EventsApplication extends SpringBootServletInitializer {

	final static Logger logger = LogManager.getLogger(EventsApplication.class);
	
	@Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(EventsApplication.class);
    }
	
	public static void main(String[] args) {
		logger.info("Application Startup");
		SpringApplication.run(EventsApplication.class, args);
	}

//	@Override
//	protected Class<?>[] getConfigClasses() {
//		return new Class[] {
//				EventsRouter.class,
//				WebConfig.class,
//				CorsConfiguration.class
//		};
//	}
}
