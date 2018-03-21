package eventsregistryapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Shares {
	private int facebook;

    public void setFacebook(int facebook){
        this.facebook = facebook;
    }
    public int getFacebook(){
        return this.facebook;
    }
}
