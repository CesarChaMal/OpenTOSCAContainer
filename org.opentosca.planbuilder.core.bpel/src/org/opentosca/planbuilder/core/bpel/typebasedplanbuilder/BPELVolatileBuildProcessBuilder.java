package org.opentosca.planbuilder.core.bpel.typebasedplanbuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;

import org.opentosca.container.core.tosca.convention.Interfaces;
import org.opentosca.container.core.tosca.convention.Types;
import org.opentosca.planbuilder.AbstractVolatilePlanBuilder;
import org.opentosca.planbuilder.core.bpel.handlers.BPELFinalizer;
import org.opentosca.planbuilder.core.bpel.handlers.BPELPlanHandler;
import org.opentosca.planbuilder.core.bpel.handlers.CorrelationIDInitializer;
import org.opentosca.planbuilder.core.bpel.tosca.handlers.NodeRelationInstanceVariablesHandler;
import org.opentosca.planbuilder.core.bpel.tosca.handlers.PropertyVariableHandler;
import org.opentosca.planbuilder.core.bpel.tosca.handlers.ServiceTemplateBoundaryPropertyMappingsToOutputHandler;
import org.opentosca.planbuilder.core.bpel.tosca.handlers.SimplePlanBuilderServiceInstanceHandler;
import org.opentosca.planbuilder.model.plan.AbstractPlan;
import org.opentosca.planbuilder.model.plan.ActivityType;
import org.opentosca.planbuilder.model.plan.bpel.BPELPlan;
import org.opentosca.planbuilder.model.plan.bpel.BPELScope;
import org.opentosca.planbuilder.model.tosca.AbstractDefinitions;
import org.opentosca.planbuilder.model.tosca.AbstractServiceTemplate;
import org.opentosca.planbuilder.model.utils.ModelUtils;
import org.opentosca.planbuilder.plugins.context.Property2VariableMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copyright 2020 IAAS University of Stuttgart <br>
 * <br>
 *
 * Plan builder to create plans that provision the volatile components of a ServiceTemplate.
 */
public class BPELVolatileBuildProcessBuilder extends AbstractVolatilePlanBuilder {

    final static Logger LOG = LoggerFactory.getLogger(BPELVolatileBuildProcessBuilder.class);

    private BPELPlanHandler planHandler;

    private NodeRelationInstanceVariablesHandler nodeRelationInstanceHandler;

    private final BPELFinalizer finalizer;

    private SimplePlanBuilderServiceInstanceHandler serviceInstanceHandler;

    private PropertyVariableHandler propertyInitializer;

    private final CorrelationIDInitializer correlationHandler;

    private NodeRelationInstanceVariablesHandler instanceVarsHandler;

    private final ServiceTemplateBoundaryPropertyMappingsToOutputHandler propertyOutputInitializer;

    public BPELVolatileBuildProcessBuilder() {
        this.finalizer = new BPELFinalizer();
        this.correlationHandler = new CorrelationIDInitializer();
        this.propertyOutputInitializer = new ServiceTemplateBoundaryPropertyMappingsToOutputHandler();
        try {
            this.planHandler = new BPELPlanHandler();
            this.nodeRelationInstanceHandler = new NodeRelationInstanceVariablesHandler(this.planHandler);
            this.propertyInitializer = new PropertyVariableHandler(this.planHandler);
            this.instanceVarsHandler = new NodeRelationInstanceVariablesHandler(this.planHandler);
            this.serviceInstanceHandler = new SimplePlanBuilderServiceInstanceHandler();
        }
        catch (final ParserConfigurationException e) {
            LOG.error("Error while initializing BPELPlanHandler", e);
        }
    }

    @Override
    public BPELPlan buildPlan(final String csarName, final AbstractDefinitions definitions,
                              final AbstractServiceTemplate serviceTemplate) {

        // generate abstract plan with activities on volatile components and their connected relations
        final String processName = ModelUtils.makeValidNCName(serviceTemplate.getId() + "_volatileBuildPlan");
        final String processNamespace = serviceTemplate.getTargetNamespace() + "_volatileBuildPlan";
        final AbstractPlan volatileBuildPlan =
            AbstractVolatilePlanBuilder.generateVOG(new QName(processName, processNamespace).toString(), definitions,
                                                    serviceTemplate);
        LOG.debug("Abstract volatile build plan: {}", volatileBuildPlan.toString());

        // generate BPEL skeleton for the abstract plan and define interface of the plan
        final BPELPlan volatileBPELBuildPlan =
            this.planHandler.createEmptyBPELPlan(processNamespace, processName, volatileBuildPlan,
                                                 Interfaces.OPENTOSCA_DECLARATIVE_INTERFACE_PLAN_LIFECYCLE_INITIATE_VOLATILE);
        volatileBPELBuildPlan.setTOSCAInterfaceName(Interfaces.OPENTOSCA_DECLARATIVE_INTERFACE_PLAN_LIFECYCLE);
        volatileBPELBuildPlan.setTOSCAOperationname(Interfaces.OPENTOSCA_DECLARATIVE_INTERFACE_PLAN_LIFECYCLE_INITIATE_VOLATILE);

        this.correlationHandler.addCorrellationID(volatileBPELBuildPlan);

        // add required variables for the instance data handling
        this.planHandler.initializeBPELSkeleton(volatileBPELBuildPlan, csarName);
        this.nodeRelationInstanceHandler.addInstanceURLVarToTemplatePlans(volatileBPELBuildPlan, serviceTemplate);
        this.nodeRelationInstanceHandler.addInstanceIDVarToTemplatePlans(volatileBPELBuildPlan, serviceTemplate);

        // create variables for the properties of all Node- and RelationshipTemplates
        final Property2VariableMapping propMap =
            this.propertyInitializer.initializePropertiesAsVariables(volatileBPELBuildPlan, serviceTemplate, true);

        this.propertyOutputInitializer.initializeBuildPlanOutput(definitions, volatileBPELBuildPlan, propMap,
                                                                 serviceTemplate);

        this.planHandler.registerExtension("http://www.apache.org/ode/bpel/extensions/bpel4restlight", true,
                                           volatileBPELBuildPlan);

        // add variable to handle the service instance id of the instance the plan belongs to
        this.serviceInstanceHandler.addServiceInstanceHandlingFromInput(volatileBPELBuildPlan);
        final String serviceTemplateUrl =
            this.serviceInstanceHandler.findServiceTemplateUrlVariableName(volatileBPELBuildPlan);
        final String serviceInstanceId =
            this.serviceInstanceHandler.findServiceInstanceIdVarName(volatileBPELBuildPlan);
        final String serviceInstanceUrl =
            this.serviceInstanceHandler.findServiceInstanceUrlVariableName(volatileBPELBuildPlan);

        // load instance data from existing instances
        this.serviceInstanceHandler.appendInitPropertyVariablesFromServiceInstanceData(volatileBPELBuildPlan, propMap,
                                                                                       serviceTemplateUrl,
                                                                                       serviceTemplate,
                                                                                       "?state=STARTED&amp;state=CREATED&amp;state=CONFIGURED");
        this.instanceVarsHandler.addNodeInstanceFindLogic(volatileBPELBuildPlan,
                                                          "?state=STARTED&amp;state=CREATED&amp;state=CONFIGURED&amp;serviceInstanceId=$bpelvar["
                                                              + serviceInstanceId + "]",
                                                          serviceTemplate);
        this.instanceVarsHandler.addPropertyVariableUpdateBasedOnNodeInstanceID(volatileBPELBuildPlan, propMap,
                                                                                serviceTemplate);
        this.instanceVarsHandler.addRelationInstanceFindLogic(volatileBPELBuildPlan,
                                                              "?state=CREATED&amp;state=INITIAL&amp;serviceInstanceId=$bpelvar["
                                                                  + serviceInstanceId + "]",
                                                              serviceTemplate);

        // TODO: add provisioning logic
        runPlugins(volatileBPELBuildPlan, propMap, serviceInstanceUrl, serviceInstanceId, serviceTemplateUrl, csarName);


        // remove invalid parts from the plan
        this.finalizer.finalize(volatileBPELBuildPlan);
        return volatileBPELBuildPlan;
    }

    @Override
    public List<AbstractPlan> buildPlans(final String csarName, final AbstractDefinitions definitions) {
        final List<AbstractPlan> plans = new ArrayList<>();
        for (final AbstractServiceTemplate serviceTemplate : definitions.getServiceTemplates()) {

            if (ModelUtils.containsPolicyWithName(serviceTemplate, Types.volatilePolicyType)) {
                LOG.debug("Generating VolatileBuildPlan for ServiceTemplate {}", serviceTemplate.getQName().toString());
                final BPELPlan newVolatileBuildPlan = buildPlan(csarName, definitions, serviceTemplate);

                if (Objects.nonNull(newVolatileBuildPlan)) {
                    LOG.debug("Created VolatileBuildPlan successfully.");
                    plans.add(newVolatileBuildPlan);
                }
            }
        }
        return plans;
    }

    /**
     * TODO
     */
    private void runPlugins(final BPELPlan buildPlan, final Property2VariableMapping map,
                            final String serviceInstanceUrl, final String serviceInstanceID,
                            final String serviceTemplateUrl, final String csarFileName) {

        for (final BPELScope bpelScope : buildPlan.getTemplateBuildPlans()) {
            if (bpelScope.getActivity().getType().equals(ActivityType.PROVISIONING)) {
                LOG.debug("Handling provisioning activity {}!", bpelScope.getActivity().toString());
                // TODO
            }
        }
    }
}
