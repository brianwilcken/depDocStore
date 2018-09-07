package webapp.configurations;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.*;

import webapp.components.AsyncExceptionHandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import webapp.components.DocumentsRequestInterceptor;

@EnableWebMvc
@Configuration
@ComponentScan(basePackages = {"webapp.components", "webapp.services", "webapp.controllers"})
@EnableAsync
public class WebConfig implements WebMvcConfigurer, AsyncConfigurer {

	final static Logger logger = LogManager.getLogger(WebConfig.class);
	
	@Autowired
    private AsyncExceptionHandler asyncExceptionHandler;
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
    	registry.addResourceHandler("/resources/**")
    		.addResourceLocations("classpath:/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new DocumentsRequestInterceptor()).addPathPatterns("/depDocStore/**");
    }
    
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return asyncExceptionHandler;
	}
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/depDocStore/**")
				.allowedOrigins("*")
		        .allowedMethods("*");
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
