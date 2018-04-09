package webapp.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import common.Tools;

public class JsonResponse {
	private Object data;
	private final String timeStamp;
	private List<String> exceptions = new ArrayList<String>();
	
	public JsonResponse() {
		timeStamp = Tools.getFormattedDateTimeString(LocalDateTime.now());
	}
	
	public JsonResponse(String timeStamp) {
		this.timeStamp = timeStamp;
	}

	public String getTimeStamp() {
		return timeStamp;
	}

	public Object getData() {
		return data;
	}

	public void setData(Object data) {
		this.data = data;
	}

	public List<String> getExceptions() {
		return exceptions;
	}

	public void setExceptions(List<Exception> exceptions) {
		for (Exception exception : exceptions) {
			this.exceptions.add(exception.getMessage());
		}
	}
}
