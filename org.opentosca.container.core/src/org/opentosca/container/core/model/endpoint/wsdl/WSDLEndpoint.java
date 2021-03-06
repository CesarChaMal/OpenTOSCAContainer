package org.opentosca.container.core.model.endpoint.wsdl;

import java.net.URI;
import java.util.Map;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.xml.namespace.QName;

import org.eclipse.persistence.annotations.Convert;
import org.eclipse.persistence.annotations.Converter;
import org.opentosca.container.core.model.csar.id.CSARID;
import org.opentosca.container.core.model.endpoint.GenericEndpoint;

/**
 * This class Represents a WSDL-Endpoint (an endpoint which points to a SOAP-Operation of a WSDL).
 * For the fields of this class refer to the WSDL operation element in the TOSCA-Specification.
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Table(name = WSDLEndpoint.tableName,
       uniqueConstraints = @UniqueConstraint(columnNames = {"portType", "csarId", "managingContainer",
                                                            "serviceInstanceID"}))
public class WSDLEndpoint extends GenericEndpoint {

    // Table Name
    protected final static String tableName = "WSDLEndpoint";

    // Converter to Convert QNames to String, and back from String to QName.
    // Used when persisting, so we can Query for QName-Objects.
    @Basic
    @Converter(name = "QNameConverter", converterClass = org.opentosca.container.core.common.jpa.QNameConverter.class)
    @Convert("QNameConverter")
    @Column(name = "PortType")
    private QName PortType;

    // NodeTypeImplementation/RelationshipTypeImplementation and IA name are there to identify
    // specific IAs
    @Basic
    @Convert("QNameConverter")
    @Column(name = "TypeImplementation")
    private QName TypeImplementation;

    @Basic
    @Column(name = "IaName")
    private String IaName;

    // only the planid is used for plan endpoints, cause in tosca the id for a
    // plan must be unique in the targetnamespace
    @Basic
    @Convert("QNameConverter")
    @Column(name = "PlanId")
    private QName PlanId;

    public WSDLEndpoint() {
        super();
    }

    // if planid is set serviceInstanceID, nodeTypeimpl and iaName must be "null"
    public WSDLEndpoint(final URI uri, final QName portType, final String triggeringContainer,
                        final String managingContainer, final CSARID csarId, final Long serviceInstanceID,
                        final QName planid, final QName nodeTypeImplementation, final String iaName, final Map<String,String> metadata) {
        super(uri, triggeringContainer, managingContainer, csarId, serviceInstanceID, metadata);
        setPortType(portType);
        setIaName(iaName);
        setPlanId(planid);
        setTypeImplementation(nodeTypeImplementation);
    }

    public QName getPortType() {
        return this.PortType;
    }

    public void setPortType(final QName portType) {
        this.PortType = portType;
    }

    public QName getTypeImplementation() {
        return this.TypeImplementation;
    }

    public void setTypeImplementation(final QName nodeTypeImplementation) {
        this.TypeImplementation = nodeTypeImplementation;
    }

    public QName getPlanId() {
        return this.PlanId;
    }

    public void setPlanId(final QName planId) {
        this.PlanId = planId;
    }

    public String getIaName() {
        return this.IaName;
    }

    public void setIaName(final String iaName) {
        this.IaName = iaName;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof WSDLEndpoint)) {
            return false;
        }

        final WSDLEndpoint compareEndpoint = (WSDLEndpoint) o;
        if (compareEndpoint.getId() != getId()) {
            return false;
        }
        if (!compareEndpoint.getCSARId().equals(getCSARId())) {
            return false;
        }
        return true;
    }

}
