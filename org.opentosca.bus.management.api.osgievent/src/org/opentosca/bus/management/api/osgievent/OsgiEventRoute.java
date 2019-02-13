package org.opentosca.bus.management.api.osgievent;

import org.apache.camel.builder.RouteBuilder;
import org.opentosca.bus.management.header.MBHeader;

/**
 * Route of the Management Bus-OSGiEvent-API.<br>
 * <br>
 *
 * Copyright 2013 IAAS University of Stuttgart <br>
 * <br>
 *
 * Incoming events are given here from the EventHandler to be routed to the Management Bus for
 * further processing. The response message is given back to the EventHandler.
 *
 *
 * @author Michael Zimmermann - zimmerml@studi.informatik.uni-stuttgart.de
 */
public class OsgiEventRoute extends RouteBuilder {

    protected static final String OPERATION_HEADER = "OPERATION";

    @Override
    public void configure() throws Exception {

        // Management Bus Endpoints
        final String MANAGEMENT_BUS_IA =
            "bean:org.opentosca.bus.management.service.IManagementBusService?method=invokeIA";
        final String MANAGEMENT_BUS_PLAN =
            "bean:org.opentosca.bus.management.service.IManagementBusService?method=invokePlan";

        this.from("direct:invoke").to("stream:out").process(exchange -> {

            exchange.getIn().setHeader(MBHeader.APIID_STRING.toString(), Activator.apiID);

            final String messageID =
                exchange.getIn().getHeader(MBHeader.PLANCORRELATIONID_STRING.toString(), String.class);
            if (messageID != null) {
                exchange.getIn().setMessageId(messageID);
                exchange.getIn().setHeader(MBHeader.SYNCINVOCATION_BOOLEAN.toString(), false);
            } else {
                exchange.getIn().setHeader(MBHeader.SYNCINVOCATION_BOOLEAN.toString(), true);
            }

        }).to("stream:out").choice()
            .when(header(OPERATION_HEADER).isEqualTo(OsgiEventOperations.INVOKE_IA.getHeaderValue()))
            .to("direct:invokeIA")
            .when(header(OPERATION_HEADER).isEqualTo(OsgiEventOperations.INVOKE_PLAN.getHeaderValue()))
            .to("direct:invokePlan").end();

        this.from("direct:invokeIA").to("stream:out").wireTap(MANAGEMENT_BUS_IA);
        this.from("direct:invokePlan").to("stream:out").to(MANAGEMENT_BUS_PLAN).end();

        this.from("direct-vm:" + Activator.apiID).recipientList(this.simple("direct:response${id}")).end();

    }
}
