package org.opentosca.planbuilder;

import java.util.List;

import javax.xml.namespace.QName;

import org.opentosca.planbuilder.model.plan.ARelationshipTemplateActivity;
import org.opentosca.planbuilder.model.plan.AbstractActivity;
import org.opentosca.planbuilder.model.plan.AbstractPlan;
import org.opentosca.planbuilder.model.plan.ActivityType;
import org.opentosca.planbuilder.model.tosca.AbstractDefinitions;
import org.opentosca.planbuilder.model.tosca.AbstractNodeTemplate;
import org.opentosca.planbuilder.model.tosca.AbstractRelationshipTemplate;
import org.opentosca.planbuilder.plugins.IPlanBuilderPolicyAwareTypePlugin;
import org.opentosca.planbuilder.plugins.IPlanBuilderTypePlugin;
import org.opentosca.planbuilder.plugins.context.PlanContext;
import org.opentosca.planbuilder.plugins.registry.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractPlanBuilder {

    protected final PluginRegistry pluginRegistry = new PluginRegistry();

    private final static Logger LOG = LoggerFactory.getLogger(AbstractPlanBuilder.class);

    /**
     * <p>
     * Creates a List of plans for the specified values csarName, definitions and serviceTemplateId.
     * Where csarName denotes the fileName of the CSAR, definitions denotes the Definitions document
     * and serviceTemplateId a QName denoting the ServiceTemplate inside the Definitions document.
     * </p>
     *
     * @param csarName the file name of the CSAR as String
     * @param definitions the Definitions document as AbstractDefinitions Object
     * @param serviceTemplateId a QName denoting a ServiceTemplate inside the Definitions document
     * @return a List of plans for the given ServiceTemplate, if the ServiceTemplate denoted by the
     *         given QName isn't found inside the Definitions document an empty List is returned
     *         instead
     */
    abstract public List<AbstractPlan> buildPlansForServiceTemplate(String csarName, AbstractDefinitions definitions,
                                                                    QName serviceTemplateId);

    /**
     * <p>
     * Returns a List of plans (Build/Termination/Management) for the ServiceTemplates contained in
     * the given Definitions document of the given CSAR.
     * </p>
     *
     * @param csarName the file name of CSAR
     * @param definitions a AbstractDefinitions Object denoting the Definitions document
     * @return a List of plans for each ServiceTemplate contained inside the Definitions document
     */
    abstract public List<AbstractPlan> buildPlansForCSAR(String csarName, AbstractDefinitions definitions);

    /**
     * <p>
     * Checks whether there is any generic plugin, that can handle the given NodeTemplate
     * </p>
     *
     * @param nodeTemplate an AbstractNodeTemplate denoting a NodeTemplate
     * @return true if there is any generic plugin which can handle the given NodeTemplate, else
     *         null
     */
    public IPlanBuilderTypePlugin findTypePlugin(final AbstractNodeTemplate nodeTemplate) {
        for (final IPlanBuilderTypePlugin plugin : this.pluginRegistry.getGenericPlugins()) {
            AbstractPlanBuilder.LOG.debug("Checking whether Generic Plugin " + plugin.getID()
                + " can handle NodeTemplate " + nodeTemplate.getId());
            if (plugin.canHandle(nodeTemplate)) {
                AbstractPlanBuilder.LOG.info("Found GenericPlugin {} that can handle NodeTemplate {}", plugin.getID(),
                                             nodeTemplate.getId());
                return plugin;
            }
        }
        return null;
    }

    /**
     * <p>
     * Checks whether there is any generic plugin, that can handle the given NodeTemplate
     * </p>
     *
     * @param nodeTemplate an AbstractNodeTemplate denoting a NodeTemplate
     * @return true if there is any generic plugin which can handle the given NodeTemplate, else
     *         false
     */
    public IPlanBuilderPolicyAwareTypePlugin findPolicyAwareTypePlugin(final AbstractNodeTemplate nodeTemplate) {
        for (final IPlanBuilderPolicyAwareTypePlugin plugin : this.pluginRegistry.getPolicyAwareTypePlugins()) {
            AbstractPlanBuilder.LOG.debug("Checking whether Generic Plugin " + plugin.getID()
                + " can handle NodeTemplate " + nodeTemplate.getId());
            if (plugin.canHandlePolicyAware(nodeTemplate)) {
                AbstractPlanBuilder.LOG.info("Found GenericPlugin {} that can handle NodeTemplate {}", plugin.getID(),
                                             nodeTemplate.getId());
                return plugin;
            }
        }
        return null;
    }

    /**
     * <p>
     * Checks whether there is any generic plugin, that can handle the given RelationshipTemplate
     * </p>
     *
     * @param nodeTemplate an AbstractNodeTemplate denoting a NodeTemplate
     * @return true if there is any generic plugin which can handle the given NodeTemplate, else
     *         false
     */
    public IPlanBuilderTypePlugin findTypePlugin(final AbstractRelationshipTemplate relationshipTemplate) {
        for (final IPlanBuilderTypePlugin plugin : this.pluginRegistry.getGenericPlugins()) {
            AbstractPlanBuilder.LOG.debug("Checking whether Type Plugin " + plugin.getID() + " can handle NodeTemplate "
                + relationshipTemplate.getId());
            if (plugin.canHandle(relationshipTemplate)) {
                AbstractPlanBuilder.LOG.info("Found TypePlugin {} that can handle NodeTemplate {}", plugin.getID(),
                                             relationshipTemplate.getId());
                return plugin;
            }
        }
        return null;
    }

    /**
     * <p>
     * Takes the first occurence of a generic plugin which can handle the given RelationshipTemplate
     * </p>
     *
     * @param context a TemplatePlanContext which was initialized for the given RelationshipTemplate
     * @param nodeTemplate a RelationshipTemplate as an AbstractRelationshipTemplate
     * @return returns true if there was a generic plugin which could handle the given
     *         RelationshipTemplate and execution was successful, else false
     */
    public boolean handleWithTypePlugin(final PlanContext context,
                                        final AbstractRelationshipTemplate relationshipTemplate) {
        for (final IPlanBuilderTypePlugin plugin : this.pluginRegistry.getGenericPlugins()) {
            if (plugin.canHandle(relationshipTemplate)) {
                AbstractPlanBuilder.LOG.info("Handling relationshipTemplate {} with generic plugin {}",
                                             relationshipTemplate.getId(), plugin.getID());
                return plugin.handle(context);
            }
        }
        return false;
    }

    /**
     * Returns an AbstractActivity from the given list with the reference relationship Template and
     * activity type
     *
     * @param activities a List of Plan activities
     * @param relationshipTemplate the relationshipTemplate the activity belongs to
     * @param type the type of the activity
     * @return an AbstractActivity
     */
    protected AbstractActivity findRelationshipTemplateActivity(final List<AbstractActivity> activities,
                                                                final AbstractRelationshipTemplate relationshipTemplate,
                                                                final ActivityType type) {
        return activities.stream().filter(activity -> activity.getType().equals(type))
                         .filter(activity -> activity instanceof ARelationshipTemplateActivity)
                         .filter(activity -> ((ARelationshipTemplateActivity) activity).getRelationshipTemplate()
                                                                                       .equals(relationshipTemplate))
                         .findFirst().orElse(null);
    }
}
