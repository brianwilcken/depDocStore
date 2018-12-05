
package nlp;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Generated;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="entry" maxOccurs="unbounded" minOccurs="0">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;element name="token" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
 *                 &lt;/sequence>
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *       &lt;/sequence>
 *       &lt;attribute name="caseSensitive" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "entry"
})
@XmlRootElement(name = "dictionary")
@Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-12-04T11:34:41-07:00", comments = "JAXB RI v2.2.8-b130911.1802")
public class Dictionary {

    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-12-04T11:34:41-07:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected List<Dictionary.Entry> entry;
    @XmlAttribute(name = "caseSensitive")
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-12-04T11:34:41-07:00", comments = "JAXB RI v2.2.8-b130911.1802")
    protected String caseSensitive;

    /**
     * Gets the value of the entry property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the entry property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getEntry().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Dictionary.Entry }
     * 
     * 
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-12-04T11:34:41-07:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public List<Dictionary.Entry> getEntry() {
        if (entry == null) {
            entry = new ArrayList<Dictionary.Entry>();
        }
        return this.entry;
    }

    /**
     * Gets the value of the caseSensitive property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-12-04T11:34:41-07:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public String getCaseSensitive() {
        return caseSensitive;
    }

    /**
     * Sets the value of the caseSensitive property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-12-04T11:34:41-07:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public void setCaseSensitive(String value) {
        this.caseSensitive = value;
    }


    /**
     * <p>Java class for anonymous complex type.
     * 
     * <p>The following schema fragment specifies the expected content contained within this class.
     * 
     * <pre>
     * &lt;complexType>
     *   &lt;complexContent>
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *       &lt;sequence>
     *         &lt;element name="token" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
     *       &lt;/sequence>
     *     &lt;/restriction>
     *   &lt;/complexContent>
     * &lt;/complexType>
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "token"
    })
    @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-12-04T11:34:41-07:00", comments = "JAXB RI v2.2.8-b130911.1802")
    public static class Entry {

        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-12-04T11:34:41-07:00", comments = "JAXB RI v2.2.8-b130911.1802")
        protected List<String> token;

        /**
         * Gets the value of the token property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the token property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getToken().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link String }
         * 
         * 
         */
        @Generated(value = "com.sun.tools.internal.xjc.Driver", date = "2018-12-04T11:34:41-07:00", comments = "JAXB RI v2.2.8-b130911.1802")
        public List<String> getToken() {
            if (token == null) {
                token = new ArrayList<String>();
            }
            return this.token;
        }

    }

}
