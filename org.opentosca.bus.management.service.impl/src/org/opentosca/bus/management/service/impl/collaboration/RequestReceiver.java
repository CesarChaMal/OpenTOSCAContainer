package org.opentosca.bus.management.service.impl.collaboration;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.commons.lang3.StringUtils;
import org.opentosca.bus.management.collaboration.model.BodyType;
import org.opentosca.bus.management.collaboration.model.CollaborationMessage;
import org.opentosca.bus.management.collaboration.model.DiscoveryRequest;
import org.opentosca.bus.management.collaboration.model.Doc;
import org.opentosca.bus.management.collaboration.model.IAInvocationRequest;
import org.opentosca.bus.management.collaboration.model.KeyValueMap;
import org.opentosca.bus.management.collaboration.model.KeyValueType;
import org.opentosca.bus.management.deployment.plugin.IManagementBusDeploymentPluginService;
import org.opentosca.bus.management.discovery.plugin.IManagementBusDiscoveryPluginService;
import org.opentosca.bus.management.header.MBHeader;
import org.opentosca.bus.management.invocation.plugin.IManagementBusInvocationPluginService;
import org.opentosca.bus.management.service.impl.Activator;
import org.opentosca.bus.management.service.impl.ManagementBusServiceImpl;
import org.opentosca.bus.management.service.impl.collaboration.route.ReceiveRequestRoute;
import org.opentosca.bus.management.service.impl.servicehandler.ServiceHandler;
import org.opentosca.container.core.common.Settings;
import org.opentosca.container.core.model.csar.id.CSARID;
import org.opentosca.container.core.model.endpoint.wsdl.WSDLEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * This class provides methods which can be invoked by remote OpenTOSCA Containers. The methods are
 * consumer endpoints of the collaboration request route ({@link ReceiveRequestRoute}).<br>
 * <br>
 *
 * Copyright 2018 IAAS University of Stuttgart
 */
public class RequestReceiver {

    private final static Logger LOG = LoggerFactory.getLogger(RequestReceiver.class);

    /**
     * Perform device/service discovery for the transferred topology fragment. The topology fragment
     * has to be passed as part of the {@link CollaborationMessage} in the message body of the
     * exchange. The method sends a reply to the topic specified in the headers of the incoming
     * exchange if the discovery is successful and adds the deployment location as header to the
     * outgoing exchange. Otherwise no response is send.
     *
     * @param exchange the exchange containing the needed information as headers and body
     */
    public void invokeDiscovery(final Exchange exchange) {

        RequestReceiver.LOG.debug("Received remote operation call for device/service discovery");
        final Message message = exchange.getIn();

        // check whether the request contains the needed header fields to send a response
        final Map<String, Object> headers = getResponseHeaders(message);
        if (Objects.isNull(headers)) {
            RequestReceiver.LOG.error("Request does not contain all needed header fields to send a response. Aborting operation!");
            return;
        }

        if (message.getBody() instanceof CollaborationMessage) {
            final CollaborationMessage collMsg = (CollaborationMessage) message.getBody();
            final BodyType body = collMsg.getBody();

            if (Objects.nonNull(body)) {
                final DiscoveryRequest discoveryRequest = body.getDiscoveryRequest();
                if (Objects.nonNull(discoveryRequest)) {

                    final Optional<IManagementBusDiscoveryPluginService> op =
                        DeploymentDistributionDecisionMaker.getDiscoveryPlugin(discoveryRequest);

                    if (op.isPresent()) {
                        LOG.debug("Found suited discovery plug-in.");
                        final IManagementBusDiscoveryPluginService plugin = op.get();

                        if (plugin.invokeDiscovery(discoveryRequest)) {
                            RequestReceiver.LOG.debug("Device/service discovery was successful. Sending response to requestor...");
                            RequestReceiver.LOG.debug("Broker: {} Topic: {} Correlation: {}",
                                                      headers.get(MBHeader.MQTTBROKERHOSTNAME_STRING.toString()),
                                                      headers.get(MBHeader.MQTTTOPIC_STRING.toString()),
                                                      headers.get(MBHeader.CORRELATIONID_STRING.toString()));

                            // add the deployment location as operation result to the headers and
                            // send response
                            headers.put(MBHeader.DEPLOYMENTLOCATION_STRING.toString(),
                                        Settings.OPENTOSCA_CONTAINER_HOSTNAME);
                            sendEmptyBodyResponse(headers);
                        } else {
                            // if matching is not successful, no response is needed
                            RequestReceiver.LOG.debug("Device/service discovery was not successful.");
                        }
                    } else {
                        RequestReceiver.LOG.error("Unable to find suited discovery plugin.");
                    }
                } else {
                    RequestReceiver.LOG.error("Body contains no DiscoveryRequest. Aborting operation!");
                }
            } else {
                RequestReceiver.LOG.error("Collaboration message contains no body. Aborting operation!");
            }
        } else {
            RequestReceiver.LOG.error("Message body has invalid class: {}. Aborting operation!",
                                      message.getBody().getClass());
        }
    }

    /**
     * Deploy the IA that is specified in the incoming exchange by using the Management Bus
     * deployment Plug-ins.
     *
     * @param exchange the exchange containing the needed information as header fields
     */
    public void invokeIADeployment(Exchange exchange) {

        RequestReceiver.LOG.debug("Received remote operation call for IA deployment.");
        final Message message = exchange.getIn();

        // abort if the request is not directed to this OpenTOSCA Container
        if (!isDestinationLocal(message)) {
            RequestReceiver.LOG.debug("Request is directed to another OpenTOSCA Container. Ignoring request!");
            return;
        }

        // check whether the request contains the needed header fields to send a response
        final Map<String, Object> headers = getResponseHeaders(message);
        if (Objects.isNull(headers)) {
            RequestReceiver.LOG.error("Request does not contain all needed header fields to send a response. Aborting operation!");
            return;
        }

        // create IA unique String from given message for synchronization
        final String identifier = getUniqueSynchronizationString(message);
        if (Objects.isNull(identifier)) {
            RequestReceiver.LOG.error("Missing information to create unique String for synchronization!");
            return;
        }

        // retrieve needed data from the headers
        final String triggeringContainer =
            message.getHeader(MBHeader.TRIGGERINGCONTAINER_STRING.toString(), String.class);
        final String deploymentLocation = Settings.OPENTOSCA_CONTAINER_HOSTNAME;
        final QName typeImplementationID =
            message.getHeader(MBHeader.TYPEIMPLEMENTATIONID_QNAME.toString(), QName.class);
        final String implementationArtifactName =
            message.getHeader(MBHeader.IMPLEMENTATIONARTIFACTNAME_STRING.toString(), String.class);
        final URI serviceInstanceID = message.getHeader(MBHeader.SERVICEINSTANCEID_URI.toString(), URI.class);
        final CSARID csarID = message.getHeader(MBHeader.CSARID.toString(), CSARID.class);
        final QName portType = message.getHeader(MBHeader.PORTTYPE_QNAME.toString(), QName.class);
        final String artifactType = message.getHeader(MBHeader.ARTIFACTTYPEID_STRING.toString(), String.class);
        final Long serviceTemplateInstanceID =
            Long.parseLong(StringUtils.substringAfterLast(serviceInstanceID.toString(), "/"));

        logInformation(triggeringContainer, deploymentLocation, typeImplementationID, implementationArtifactName,
                       csarID, portType, artifactType, serviceTemplateInstanceID);

        // URI of the deployed IA
        URI endpointURI = null;

        // Prevent two threads from trying to deploy the same IA concurrently and avoid the deletion
        // of an IA after successful checking that an IA is already deployed.
        synchronized (ManagementBusServiceImpl.getLockForString(identifier)) {

            RequestReceiver.LOG.debug("Got lock for operations on the given IA. Checking if IA is already deployed...");

            final List<WSDLEndpoint> endpoints =
                ServiceHandler.endpointService.getWSDLEndpointsForNTImplAndIAName(triggeringContainer,
                                                                                  deploymentLocation,
                                                                                  typeImplementationID,
                                                                                  implementationArtifactName);

            if (Objects.nonNull(endpoints) && endpoints.size() > 0) {
                // This case should not happen, as the 'master' Container sends only one deployment
                // request per IA and intercepts all other deployment actions if there is already an
                // endpoint.
                endpointURI = endpoints.get(0).getURI();

                RequestReceiver.LOG.warn("IA is already deployed. Storing only one endpoint at the remote side. Endpoint URI: {}",
                                         endpointURI);
            } else {
                RequestReceiver.LOG.debug("IA not yet deployed. Trying to deploy...");

                final IManagementBusDeploymentPluginService deploymentPlugin =
                    ServiceHandler.deploymentPluginServices.get(artifactType);

                if (Objects.nonNull(deploymentPlugin)) {
                    RequestReceiver.LOG.debug("Deployment plug-in: {}. Deploying IA...", deploymentPlugin.toString());

                    // execute deployment via corresponding plug-in
                    exchange = deploymentPlugin.invokeImplementationArtifactDeployment(exchange);
                    endpointURI = exchange.getIn().getHeader(MBHeader.ENDPOINT_URI.toString(), URI.class);

                    // store new endpoint for the IA
                    final WSDLEndpoint endpoint =
                        new WSDLEndpoint(endpointURI, portType, triggeringContainer, deploymentLocation, csarID,
                            serviceTemplateInstanceID, null, typeImplementationID, implementationArtifactName);
                    ServiceHandler.endpointService.storeWSDLEndpoint(endpoint);
                } else {
                    RequestReceiver.LOG.error("No matching deployment plug-in found. Aborting deployment!");
                }
            }
        }

        RequestReceiver.LOG.debug("Sending response message containing endpoint URI: {}", endpointURI);

        // add the endpoint URI as operation result to the headers and send response
        headers.put(MBHeader.ENDPOINT_URI.toString(), endpointURI);
        sendEmptyBodyResponse(headers);
    }

    /**
     * Undeploy the IA that is specified in the incoming exchange by using the Management Bus
     * deployment Plug-ins.
     *
     * @param exchange the exchange containing the needed information as header fields
     */
    public void invokeIAUndeployment(Exchange exchange) {

        RequestReceiver.LOG.debug("Received remote operation call for IA undeployment.");
        final Message message = exchange.getIn();

        // abort if the request is not directed to this OpenTOSCA Container
        if (!isDestinationLocal(message)) {
            RequestReceiver.LOG.debug("Request is directed to another OpenTOSCA Container. Ignoring request!");
            return;
        }

        // check whether the request contains the needed header fields to send a response
        final Map<String, Object> headers = getResponseHeaders(message);
        if (Objects.isNull(headers)) {
            RequestReceiver.LOG.error("Request does not contain all needed header fields to send a response. Aborting operation!");
            return;
        }

        // create IA unique String from given message for synchronization
        final String identifier = getUniqueSynchronizationString(message);
        if (Objects.isNull(identifier)) {
            RequestReceiver.LOG.error("Missing information to create unique String for synchronization!");
            return;
        }

        boolean undeploymentState = false;

        // retrieve needed data from the headers
        final String triggeringContainer =
            message.getHeader(MBHeader.TRIGGERINGCONTAINER_STRING.toString(), String.class);
        final String deploymentLocation = Settings.OPENTOSCA_CONTAINER_HOSTNAME;
        final QName typeImplementationID =
            message.getHeader(MBHeader.TYPEIMPLEMENTATIONID_QNAME.toString(), QName.class);
        final String implementationArtifactName =
            message.getHeader(MBHeader.IMPLEMENTATIONARTIFACTNAME_STRING.toString(), String.class);
        final String artifactType = message.getHeader(MBHeader.ARTIFACTTYPEID_STRING.toString(), String.class);

        RequestReceiver.LOG.debug("Undeployment of IA: Triggering Container: {}, Deployment location: {}, NodeTypeImplementation ID: {}, IA name: {}, Type: {}",
                                  triggeringContainer, deploymentLocation, typeImplementationID,
                                  implementationArtifactName, artifactType);

        // Prevent two threads from trying to deploy the same IA concurrently and avoid the deletion
        // of an IA after successful checking that an IA is already deployed.
        synchronized (ManagementBusServiceImpl.getLockForString(identifier)) {

            RequestReceiver.LOG.debug("Got lock for operations on the given IA. Getting endpoints fot the IA...");

            // get all endpoints for the given parameters
            final List<WSDLEndpoint> endpoints =
                ServiceHandler.endpointService.getWSDLEndpointsForNTImplAndIAName(triggeringContainer,
                                                                                  deploymentLocation,
                                                                                  typeImplementationID,
                                                                                  implementationArtifactName);

            if (Objects.nonNull(endpoints) && endpoints.size() > 0) {

                // only one endpoint is stored for remote IAs
                final WSDLEndpoint endpoint = endpoints.get(0);
                ServiceHandler.endpointService.removeWSDLEndpoint(endpoint);

                final IManagementBusDeploymentPluginService deploymentPlugin =
                    ServiceHandler.deploymentPluginServices.get(artifactType);

                if (Objects.nonNull(deploymentPlugin)) {
                    RequestReceiver.LOG.debug("Undeploying IA...");

                    exchange = deploymentPlugin.invokeImplementationArtifactUndeployment(exchange);
                    undeploymentState =
                        exchange.getIn().getHeader(MBHeader.OPERATIONSTATE_BOOLEAN.toString(), boolean.class);
                } else {
                    RequestReceiver.LOG.error("No matching plug-in found. Aborting deployment!");
                }
            } else {
                RequestReceiver.LOG.error("No enpoint found for this IA. Undeployment not possible!");
            }
        }

        RequestReceiver.LOG.debug("Sending response message containing undeployment state: {}", undeploymentState);

        // add the undeployment state as operation result to the headers and send response
        headers.put(MBHeader.OPERATIONSTATE_BOOLEAN.toString(), undeploymentState);
        sendEmptyBodyResponse(headers);
    }

    /**
     * Invoke an IA which is managed by this OpenTOSCA Container based on the request of another
     * Container. The request contains all needed input parameters and the endpoint of the invoked
     * IA.
     *
     * @param exchange the exchange containing the needed information as headers and body
     */
    public void invokeIAOperation(final Exchange exchange) {

        RequestReceiver.LOG.debug("Received remote operation call for invocation of an IA operation.");
        final Message message = exchange.getIn();

        // abort if the request is not directed to this OpenTOSCA Container
        if (!isDestinationLocal(message)) {
            RequestReceiver.LOG.debug("Request is directed to another OpenTOSCA Container. Ignoring request!");
            return;
        }

        // check whether the request contains the needed header fields to send a response
        final Map<String, Object> headers = getResponseHeaders(message);
        if (headers != null) {

            if (message.getBody() instanceof CollaborationMessage) {
                final CollaborationMessage collMsg = (CollaborationMessage) message.getBody();
                final BodyType body = collMsg.getBody();

                if (body != null) {
                    final IAInvocationRequest request = body.getIAInvocationRequest();

                    if (request != null) {

                        RequestReceiver.LOG.debug("Request is valid. Checking for input parameters...");

                        if (request.getParams() != null) {
                            RequestReceiver.LOG.debug("Request contains input parameters as HashMap:");

                            final HashMap<String, String> inputParamMap = new HashMap<>();

                            for (final KeyValueType inputParam : request.getParams().getKeyValuePair()) {
                                RequestReceiver.LOG.debug("Key: {}, Value: {}", inputParam.getKey(),
                                                          inputParam.getValue());
                                inputParamMap.put(inputParam.getKey(), inputParam.getValue());
                            }

                            message.setBody(inputParamMap, HashMap.class);
                        } else {
                            if (request.getDoc() != null) {
                                RequestReceiver.LOG.debug("Request contains input parameters a Document");

                                try {
                                    final DocumentBuilderFactory dFact = DocumentBuilderFactory.newInstance();
                                    final DocumentBuilder build = dFact.newDocumentBuilder();
                                    final Document document = build.newDocument();

                                    final Element element = request.getDoc().getAny();

                                    document.adoptNode(element);
                                    document.appendChild(element);

                                    message.setBody(document, Document.class);
                                }
                                catch (final Exception e) {
                                    RequestReceiver.LOG.error("Unable to parse Document: {}", e.getMessage());
                                }
                            } else {
                                RequestReceiver.LOG.warn("Request contains no input parameters.");
                                message.setBody(null);
                            }
                        }

                        final String invocationType =
                            message.getHeader(MBHeader.INVOCATIONTYPE_STRING.toString(), String.class);

                        if (invocationType != null) {

                            // call the operation with the related invocation plug-in
                            final IManagementBusInvocationPluginService invocationPlugin =
                                ServiceHandler.invocationPluginServices.get(invocationType);
                            if (invocationPlugin != null) {
                                RequestReceiver.LOG.debug("Invoking IA with plug-in: {}", invocationPlugin.getClass());
                                final Exchange response = invocationPlugin.invoke(exchange);

                                final Object responseBody = response.getIn().getBody();

                                // object to transmitt output parameters to the calling
                                // Container
                                final IAInvocationRequest invocationResponse = new IAInvocationRequest();

                                if (responseBody instanceof HashMap) {
                                    RequestReceiver.LOG.debug("Response contains output parameters as HashMap");

                                    @SuppressWarnings("unchecked")
                                    final HashMap<String, String> paramsMap = (HashMap<String, String>) responseBody;

                                    final KeyValueMap invocationResponseMap = new KeyValueMap();
                                    final List<KeyValueType> invocationResponsePairs =
                                        invocationResponseMap.getKeyValuePair();

                                    for (final Entry<String, String> param : paramsMap.entrySet()) {
                                        invocationResponsePairs.add(new KeyValueType(param.getKey(), param.getValue()));
                                    }

                                    invocationResponse.setParams(invocationResponseMap);
                                } else {
                                    if (body instanceof Document) {
                                        RequestReceiver.LOG.debug("Response contains output parameters as Document.");

                                        final Document document = (Document) body;
                                        invocationResponse.setDoc(new Doc(document.getDocumentElement()));
                                    } else {
                                        RequestReceiver.LOG.warn("No output parameters defined!");
                                    }
                                }

                                // send response to calling Container
                                final CollaborationMessage reply =
                                    new CollaborationMessage(new KeyValueMap(), new BodyType(invocationResponse));
                                Activator.producer.sendBodyAndHeaders("direct:SendMQTT", reply, headers);
                            } else {
                                RequestReceiver.LOG.error("No invocation plug-in found for invocation type: {}",
                                                          invocationType);
                            }
                        } else {
                            RequestReceiver.LOG.error("No invocation type specified for the IA!");
                        }
                    } else {
                        RequestReceiver.LOG.error("Body contains no IAInvocationRequest. Aborting operation!");
                    }
                } else {
                    RequestReceiver.LOG.error("Collaboration message contains no body. Aborting operation!");
                }
            } else {
                RequestReceiver.LOG.error("Message body has invalid class: {}. Aborting operation!",
                                          message.getBody().getClass());
            }
        } else {
            RequestReceiver.LOG.error("Request does not contain all needed header fields to send a response. Aborting operation!");
        }
    }

    /**
     * Send a response with the given headers and an empty message body.
     *
     * @param headers the headers for the response message
     */
    private void sendEmptyBodyResponse(final Map<String, Object> headers) {
        final CollaborationMessage replyBody = new CollaborationMessage(new KeyValueMap(), null);
        Activator.producer.sendBodyAndHeaders("direct:SendMQTT", replyBody, headers);
    }

    /**
     * Get the header fields that are needed to respond to a request as Map.
     *
     * @param message the request message
     * @return the Map containing the header fields for the response if the needed header fields are
     *         found in the request message, <tt>null</tt> otherwise
     */
    private Map<String, Object> getResponseHeaders(final Message message) {

        // extract header field
        final String broker = message.getHeader(MBHeader.MQTTBROKERHOSTNAME_STRING.toString(), String.class);
        final String replyTopic = message.getHeader(MBHeader.REPLYTOTOPIC_STRING.toString(), String.class);
        final String correlation = message.getHeader(MBHeader.CORRELATIONID_STRING.toString(), String.class);

        // reply is only possible if all headers are set
        if (broker != null && replyTopic != null && correlation != null) {

            // add the header fields to the header map and return it
            final Map<String, Object> headers = new HashMap<>();
            headers.put(MBHeader.MQTTBROKERHOSTNAME_STRING.toString(), broker);
            headers.put(MBHeader.MQTTTOPIC_STRING.toString(), replyTopic);
            headers.put(MBHeader.CORRELATIONID_STRING.toString(), correlation);

            return headers;
        } else {
            // header fields are missing and therefore no response possible
            return null;
        }
    }

    /**
     * Check whether the request is directed to the local OpenTOSCA Container / Management Bus. This
     * is the case if the {@link MBHeader#DEPLOYMENTLOCATION_STRING} header field equals the local
     * host name.
     *
     * @param message the request message
     * @return <tt>true</tt> if the request is directed to this Management Bus, <tt>false</tt>
     *         otherwise
     */
    private boolean isDestinationLocal(final Message message) {

        final String deploymentLocation =
            message.getHeader(MBHeader.DEPLOYMENTLOCATION_STRING.toString(), String.class);
        RequestReceiver.LOG.debug("Deplyoment location header: {}", deploymentLocation);

        return deploymentLocation != null && deploymentLocation.equals(Settings.OPENTOSCA_CONTAINER_HOSTNAME);
    }

    /**
     * Create a String that uniquely identifies the IA that has to be deployed/undeployed for the
     * given request message. The String can be used to synchronize all operations that are
     * concerned with that IA.
     *
     * @param message the request message
     * @return a String that uniquely identifies the IA or <tt>null</tt> if needed header fields are
     *         missing
     */
    private String getUniqueSynchronizationString(final Message message) {

        final String triggeringContainer =
            message.getHeader(MBHeader.TRIGGERINGCONTAINER_STRING.toString(), String.class);
        final String deploymentLocation = Settings.OPENTOSCA_CONTAINER_HOSTNAME;
        final QName typeImplementationID =
            message.getHeader(MBHeader.TYPEIMPLEMENTATIONID_QNAME.toString(), QName.class);
        final String implementationArtifactName =
            message.getHeader(MBHeader.IMPLEMENTATIONARTIFACTNAME_STRING.toString(), String.class);

        if (triggeringContainer != null && deploymentLocation != null && typeImplementationID != null
            && implementationArtifactName != null) {

            return triggeringContainer + "/" + deploymentLocation + "/" + typeImplementationID.toString() + "/"
                + implementationArtifactName;
        } else {
            return null;
        }
    }

    /**
     * Log the provided information.
     *
     * @param triggeringContainer
     * @param deploymentLocation
     * @param typeImplementationID
     * @param implementationArtifactName
     * @param csarID
     * @param portType
     * @param artifactType
     * @param serviceTemplateInstanceID
     */
    private void logInformation(final String triggeringContainer, final String deploymentLocation,
                                final QName typeImplementationID, final String implementationArtifactName,
                                final CSARID csarID, final QName portType, final String artifactType,
                                final Long serviceTemplateInstanceID) {

        RequestReceiver.LOG.debug("Triggering Container: {}", triggeringContainer);
        RequestReceiver.LOG.debug("CSARID: {}", csarID);
        RequestReceiver.LOG.debug("ServiceTemplateInstance ID: {}", serviceTemplateInstanceID);
        RequestReceiver.LOG.debug("Deployment location: {}", deploymentLocation);
        RequestReceiver.LOG.debug("TypeImplementation: {}", typeImplementationID);
        RequestReceiver.LOG.debug("IA name: {}", implementationArtifactName);
        RequestReceiver.LOG.debug("ArtifactType: {}", artifactType);
        RequestReceiver.LOG.debug("Port type: {}", portType);
    }
}
