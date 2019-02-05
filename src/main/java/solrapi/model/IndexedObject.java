package solrapi.model;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import common.Tools;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.solr.common.SolrDocument;

public abstract class IndexedObject {

    protected void consumeSolr(SolrDocument doc) {
        Arrays.stream(this.getClass().getDeclaredFields()).forEach(p -> {
            if(doc.containsKey(p.getName())) {
                String fieldName = p.getName();
                Object value = doc.get(fieldName);
                if (value != null) {
                    try {
                        if (value instanceof String || value instanceof Long || value instanceof Boolean || value instanceof Double) {
                            BeanUtils.setProperty(this, fieldName, value);
                        } else if (value instanceof List) {
                            List<String> lst = (List<String>)value;
                            String[] arr = lst.toArray(new String[lst.size()]);
                            BeanUtils.setProperty(this, fieldName, arr);
                        } else if (value instanceof HashMap || value instanceof Set) {

                        } else {
                            String date = Tools.getFormattedDateTimeString(((Date) value).toInstant());
                            BeanUtils.setProperty(this, fieldName, date);
                        }
                    } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        });
    }
}
