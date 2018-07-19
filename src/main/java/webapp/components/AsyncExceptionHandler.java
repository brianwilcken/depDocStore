package webapp.components;

import java.lang.reflect.Method;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.stereotype.Component;

@Component
public class AsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

	final static Logger logger = LogManager.getLogger(AsyncExceptionHandler.class);
	
    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
    	logger.error(ex.getMessage(), ex);
    }

}
