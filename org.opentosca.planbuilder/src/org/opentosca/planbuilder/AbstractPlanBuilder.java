package org.opentosca.planbuilder;

import org.opentosca.planbuilder.model.tosca.AbstractNodeTemplate;
import org.opentosca.planbuilder.plugins.registry.PluginRegistry;

public abstract class AbstractPlanBuilder {

    protected final PluginRegistry pluginRegistry = new PluginRegistry();

    public boolean isRunning(final AbstractNodeTemplate nodeTemplate) {
        if (nodeTemplate.getProperties() != null) {
            final String val = nodeTemplate.getProperties().asMap().get("State");
            return val != null && val.equals("Running");
        } else {
            return false;
        }
    }

    /**
     * Returns the number of the plugins registered with this planbuilder
     *
     * @return integer denoting the count of plugins
     */
    public int registeredPlugins() {
        return this.pluginRegistry.getTypePlugins().size() + this.pluginRegistry.getDaPlugins().size()
            + this.pluginRegistry.getIaPlugins().size() + this.pluginRegistry.getPostPlugins().size()
            + this.pluginRegistry.getProvPlugins().size();
    }
}
