package webapp.configurations;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.TomcatHttpHandlerAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewResolverRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.view.freemarker.FreeMarkerViewResolver;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

import webapp.components.AsyncExceptionHandler;
import webapp.controllers.EventsController;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@EnableWebMvc
@Configuration
@ComponentScan(basePackages = {"webapp.components", "webapp.services", "webapp.controllers"})
@EnableAsync
public class WebConfig implements WebMvcConfigurer, AsyncConfigurer {

	final static Logger logger = LogManager.getLogger(WebConfig.class);
	
	@Autowired
    private AsyncExceptionHandler asyncExceptionHandler;
	
	@Override
    public void configureViewResolvers(ViewResolverRegistry registry) {
        registry.freeMarker();
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
    	registry.addResourceHandler("/resources/**")
    		.addResourceLocations("classpath:/");
    }
    
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return asyncExceptionHandler;
	}
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/eventNLP/**")
				.allowedOrigins("*")
		        .allowedMethods("*");
    }

    @Bean
    public FreeMarkerConfigurer freeMarkerConfigurer() {
        FreeMarkerConfigurer configurer = new FreeMarkerConfigurer();
        configurer.setTemplateLoaderPath("classpath:/templates");
        return configurer;
    }
    
    @Bean 
    public FreeMarkerViewResolver freemarkerViewResolver() { 
        FreeMarkerViewResolver resolver = new FreeMarkerViewResolver(); 
        resolver.setCache(true); 
        resolver.setPrefix(""); 
        resolver.setSuffix(".ftl"); 
        return resolver; 
    }
    
    @Bean(name="processExecutor")
    public TaskExecutor workExecutor() {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setThreadNamePrefix("Async-");
        threadPoolTaskExecutor.setCorePoolSize(3);
        threadPoolTaskExecutor.setMaxPoolSize(3);
        threadPoolTaskExecutor.setQueueCapacity(600);
        threadPoolTaskExecutor.afterPropertiesSet();
        return threadPoolTaskExecutor;
    }
}
