package eventsregistryapi.model;

import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.solr.common.SolrDocument;

public abstract class IndexedObject {
	
	protected void ConsumeSolr(SolrDocument doc) {
		Arrays.stream(this.getClass().getDeclaredFields()).forEach(p -> {
			if(doc.containsKey(p.getName())) {
				String fieldName = p.getName();
				Object value = doc.get(fieldName);
				try {
					if (value instanceof String || value instanceof Long) {
						BeanUtils.setProperty(this, fieldName, value);
					} else if (value instanceof List) {
						List<String> lst = (List<String>)value;
						String[] arr = lst.toArray(new String[lst.size()]);
						BeanUtils.setProperty(this, fieldName, arr);
					} else {
						String date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format((Date)value);
						BeanUtils.setProperty(this, fieldName, date);
					}
				} catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}
}
