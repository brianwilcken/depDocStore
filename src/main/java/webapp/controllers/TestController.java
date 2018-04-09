package webapp.controllers;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

	@RequestMapping(value = "/test", method = RequestMethod.GET)
	public ResponseEntity<String> mainHandler(@RequestHeader HttpHeaders hdrs) throws IOException, InterruptedException {
		
		BodyBuilder response = ResponseEntity.ok();
		
	    response.contentType(MediaType.TEXT_PLAIN);
	    
	    //this is the object we use for writing the chunked response
	    StringBuilder str = new StringBuilder();

	    for (int i = 0; i < 63; i++) {
			for (int j = 0; j < 63; j++) {
				str.append(Integer.toHexString(i) + ":" + Integer.toHexString(j) + " ");
			}
		}
	    
	    return response.body(str.toString());
	}
}
