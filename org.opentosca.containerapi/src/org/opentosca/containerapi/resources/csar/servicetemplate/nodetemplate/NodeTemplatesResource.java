package org.opentosca.containerapi.resources.csar.servicetemplate.nodetemplate;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.namespace.QName;

import org.opentosca.containerapi.osgi.servicegetter.ToscaServiceHandler;
import org.opentosca.containerapi.resources.utilities.ResourceConstants;
import org.opentosca.containerapi.resources.utilities.Utilities;
import org.opentosca.containerapi.resources.xlink.Reference;
import org.opentosca.containerapi.resources.xlink.References;
import org.opentosca.containerapi.resources.xlink.XLinkConstants;
import org.opentosca.core.model.csar.id.CSARID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeTemplatesResource {
	
	
	private final Logger log = LoggerFactory.getLogger(NodeTemplatesResource.class);
	private final CSARID csarId;
	private final QName serviceTemplateID;
	private final int serviceTemplateInstanceId;
	private UriInfo uriInfo;
	
	
	public NodeTemplatesResource(CSARID csarId, QName serviceTemplateID, int serviceTemplateInstanceId) {
		this.csarId = csarId;
		this.serviceTemplateID = serviceTemplateID;
		this.serviceTemplateInstanceId = serviceTemplateInstanceId;
	}
	
	@GET
	@Produces(ResourceConstants.LINKED_XML)
	public Response getReferencesXML(@Context UriInfo uriInfo) throws UnsupportedEncodingException {
		this.uriInfo = uriInfo;
		return Response.ok(getRefs().getXMLString()).build();
	}
	
	@GET
	@Produces(ResourceConstants.LINKED_JSON)
	public Response getReferencesJSON(@Context UriInfo uriInfo) throws UnsupportedEncodingException {
		this.uriInfo = uriInfo;
		return Response.ok(getRefs().getJSONString()).build();
	}
	
	public References getRefs() throws UnsupportedEncodingException {
		
		if (csarId == null) {
			return null;
		}
		
		References refs = new References();
		
		for (String ntID : ToscaServiceHandler.getToscaEngineService().getNodeTemplatesOfServiceTemplate(csarId, serviceTemplateID)) {
			refs.getReference().add(new Reference(Utilities.buildURI(uriInfo.getAbsolutePath().toString(), ntID), XLinkConstants.SIMPLE, ntID));
		}
		
		// selflink
		refs.getReference().add(new Reference(uriInfo.getAbsolutePath().toString(), XLinkConstants.SIMPLE, XLinkConstants.SELF));
		
		return refs;
	}
	
	
	@Path("{PlanIdLocalPart}")
	@Produces(ResourceConstants.TOSCA_JSON)
	public NodeTemplateResource getNodeTemplate(@Context UriInfo uriInfo, @PathParam("PlanIdLocalPart") String planIdLocalPart) throws URISyntaxException {
		return new NodeTemplateResource(csarId, serviceTemplateID,serviceTemplateInstanceId, planIdLocalPart);
	}
}