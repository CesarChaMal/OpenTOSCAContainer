// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference
// Implementation, v2.2.4-2
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a>
// Any modifications to this file will be lost upon recompilation of the source schema.
// Generated on: 2018.07.05 at 09:07:58 PM CEST

package org.opentosca.bus.management.service.impl.collaboration.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

/**
 * <p>
 * The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="BodyType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;choice>
 *         &lt;element name="InstanceDataMatchingRequest" type="{http://collaboration.org/schema}InstanceDataMatchingRequest" minOccurs="0"/>
 *         &lt;element name="IAInvocationRequest" type="{http://collaboration.org/schema}IAInvocationRequest" minOccurs="0"/>
 *       &lt;/choice>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 *
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "BodyType", propOrder = {"instanceDataMatchingRequest", "iaInvocationRequest"})
public class BodyType {

    @XmlElement(name = "InstanceDataMatchingRequest")
    protected InstanceDataMatchingRequest instanceDataMatchingRequest;
    @XmlElement(name = "IAInvocationRequest")
    protected IAInvocationRequest iaInvocationRequest;

    public BodyType() {}

    public BodyType(final IAInvocationRequest iaInvocationRequest) {
        this.iaInvocationRequest = iaInvocationRequest;
    }

    public BodyType(final InstanceDataMatchingRequest instanceDataMatchingRequest) {
        this.instanceDataMatchingRequest = instanceDataMatchingRequest;
    }

    /**
     * Gets the value of the instanceDataMatchingRequest property.
     *
     * @return possible object is {@link InstanceDataMatchingRequest }
     *
     */
    public InstanceDataMatchingRequest getInstanceDataMatchingRequest() {
        return this.instanceDataMatchingRequest;
    }

    /**
     * Sets the value of the instanceDataMatchingRequest property.
     *
     * @param value allowed object is {@link InstanceDataMatchingRequest }
     *
     */
    public void setInstanceDataMatchingRequest(final InstanceDataMatchingRequest value) {
        this.instanceDataMatchingRequest = value;
    }

    /**
     * Gets the value of the iaInvocationRequest property.
     *
     * @return possible object is {@link IAInvocationRequest }
     *
     */
    public IAInvocationRequest getIAInvocationRequest() {
        return this.iaInvocationRequest;
    }

    /**
     * Sets the value of the iaInvocationRequest property.
     *
     * @param value allowed object is {@link IAInvocationRequest }
     *
     */
    public void setIAInvocationRequest(final IAInvocationRequest value) {
        this.iaInvocationRequest = value;
    }
}
