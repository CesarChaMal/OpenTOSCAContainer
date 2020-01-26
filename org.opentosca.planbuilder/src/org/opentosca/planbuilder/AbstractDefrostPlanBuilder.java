package org.opentosca.planbuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;

import org.opentosca.container.core.tosca.convention.Types;
import org.opentosca.planbuilder.model.plan.AbstractActivity;
import org.opentosca.planbuilder.model.plan.AbstractPlan;
import org.opentosca.planbuilder.model.plan.AbstractPlan.Link;
import org.opentosca.planbuilder.model.plan.ActivityType;
import org.opentosca.planbuilder.model.plan.NodeTemplateActivity;
import org.opentosca.planbuilder.model.plan.RelationshipTemplateActivity;
import org.opentosca.planbuilder.model.tosca.AbstractDefinitions;
import org.opentosca.planbuilder.model.tosca.AbstractNodeTemplate;
import org.opentosca.planbuilder.model.tosca.AbstractPolicy;
import org.opentosca.planbuilder.model.tosca.AbstractRelationshipTemplate;
import org.opentosca.planbuilder.model.tosca.AbstractServiceTemplate;
import org.opentosca.planbuilder.model.utils.ModelUtils;

public abstract class AbstractDefrostPlanBuilder extends AbstractSimplePlanBuilder {

    public static AbstractPlan generatePOG(final String id, final AbstractDefinitions definitions,
                                           final AbstractServiceTemplate serviceTemplate,
                                           final Collection<AbstractNodeTemplate> nodeTemplates,
                                           final Collection<AbstractRelationshipTemplate> relationshipTemplates) {
        final Collection<AbstractActivity> activities = new ArrayList<>();
        final Set<Link> links = new HashSet<>();
        final Map<AbstractNodeTemplate, AbstractActivity> nodeMapping = new HashMap<>();
        final Map<AbstractRelationshipTemplate, AbstractActivity> relationMapping = new HashMap<>();
        generateDOGActivitesAndLinks(activities, links, nodeMapping, nodeTemplates, relationMapping,
                                     relationshipTemplates);

        final AbstractPlan plan =
            new AbstractPlan(id, AbstractPlan.PlanType.BUILD, definitions, serviceTemplate, activities, links) {

            };
        return plan;
    }

    public static AbstractPlan generateDOG(final String id, final AbstractDefinitions definitions,
                                           final AbstractServiceTemplate serviceTemplate) {

        final Collection<AbstractActivity> activities = new ArrayList<>();
        final Set<Link> links = new HashSet<>();
        final Map<AbstractNodeTemplate, AbstractActivity> nodeMapping = new HashMap<>();
        final Map<AbstractRelationshipTemplate, AbstractActivity> relationMapping = new HashMap<>();

        generateDOGActivitesAndLinks(activities, links, nodeMapping,
                                     ModelUtils.getNonVolatileNodeTemplates(serviceTemplate), relationMapping,
                                     ModelUtils.getNonVolatileRelationshipTemplates(serviceTemplate));

        final AbstractPlan plan =
            new AbstractPlan(id, AbstractPlan.PlanType.BUILD, definitions, serviceTemplate, activities, links) {

            };
        return plan;
    }

    private static void generateDOGActivitesAndLinks(final Collection<AbstractActivity> activities,
                                                     final Set<Link> links,
                                                     final Map<AbstractNodeTemplate, AbstractActivity> nodeActivityMapping,
                                                     final Collection<AbstractNodeTemplate> nodeTemplates,
                                                     final Map<AbstractRelationshipTemplate, AbstractActivity> relationActivityMapping,
                                                     final Collection<AbstractRelationshipTemplate> relationshipTemplates) {
        final Collection<AbstractNodeTemplate> nodeToStart =
            AbstractDefrostPlanBuilder.calculateNodesToStart(nodeTemplates);

        for (final AbstractNodeTemplate nodeTemplate : nodeTemplates) {

            if (nodeToStart.contains(nodeTemplate)) {
                final AbstractActivity activity =
                    new NodeTemplateActivity(nodeTemplate.getId() + "_no_activity", ActivityType.NONE, nodeTemplate);
                activities.add(activity);
                nodeActivityMapping.put(nodeTemplate, activity);

            } else if (AbstractDefrostPlanBuilder.hasFreezeableComponentPolicy(nodeTemplate)) {
                final AbstractActivity activity = new NodeTemplateActivity(nodeTemplate.getId() + "_defrost_activity",
                    ActivityType.DEFROST, nodeTemplate);
                activities.add(activity);
                nodeActivityMapping.put(nodeTemplate, activity);

            } else {
                final AbstractActivity activity = new NodeTemplateActivity(
                    nodeTemplate.getId() + "_provisioning_activity", ActivityType.PROVISIONING, nodeTemplate);
                activities.add(activity);
                nodeActivityMapping.put(nodeTemplate, activity);
            }

        }

        for (final AbstractRelationshipTemplate relationshipTemplate : relationshipTemplates) {
            final AbstractActivity activity =
                new RelationshipTemplateActivity(relationshipTemplate.getId() + "_provisioning_activity",
                    ActivityType.PROVISIONING, relationshipTemplate);
            activities.add(activity);
            relationActivityMapping.put(relationshipTemplate, activity);
        }

        for (final AbstractRelationshipTemplate relationshipTemplate : relationshipTemplates) {
            final AbstractActivity activity = relationActivityMapping.get(relationshipTemplate);
            final QName baseType = ModelUtils.getRelationshipBaseType(relationshipTemplate);
            if (baseType.equals(Types.connectsToRelationType)) {
                links.add(new Link(nodeActivityMapping.get(relationshipTemplate.getSource()), activity));
                links.add(new Link(nodeActivityMapping.get(relationshipTemplate.getTarget()), activity));
            } else if (baseType.equals(Types.dependsOnRelationType) | baseType.equals(Types.hostedOnRelationType)
                | baseType.equals(Types.deployedOnRelationType)) {
                    links.add(new Link(nodeActivityMapping.get(relationshipTemplate.getTarget()), activity));
                    links.add(new Link(activity, nodeActivityMapping.get(relationshipTemplate.getSource())));
                }

        }
    }

    private static Collection<AbstractNodeTemplate> calculateNodesToStart(final Collection<AbstractNodeTemplate> nodes) {
        final Collection<AbstractNodeTemplate> nodesToStart = new HashSet<>();

        for (final AbstractNodeTemplate node : nodes) {
            final List<AbstractNodeTemplate> nodesToSink = new ArrayList<>();
            ModelUtils.getNodesFromNodeToSink(node, Types.hostedOnRelationType, nodesToSink);
            for (final AbstractNodeTemplate nodeToSink : nodesToSink) {
                if (!nodeToSink.equals(node) && AbstractDefrostPlanBuilder.hasFreezeableComponentPolicy(nodeToSink)) {
                    nodesToStart.add(node);
                    break;
                }
            }
        }

        return nodesToStart;
    }

    protected static boolean hasFreezeableComponentPolicy(final AbstractNodeTemplate nodeTemplate) {
        for (final AbstractPolicy policy : nodeTemplate.getPolicies()) {
            if (policy.getType().getId().equals(Types.freezableComponentPolicy)) {
                return true;
            }
        }
        return false;
    }
}
