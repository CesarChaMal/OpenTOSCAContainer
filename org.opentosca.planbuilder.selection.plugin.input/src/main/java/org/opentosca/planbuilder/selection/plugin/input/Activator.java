package org.opentosca.planbuilder.selection.plugin.input;

import org.opentosca.planbuilder.plugins.typebased.IScalingPlanBuilderSelectionPlugin;
import org.opentosca.planbuilder.selection.plugin.input.bpel.BPELSelectionInputPlugin;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

  private static BundleContext context;

  @SuppressWarnings("rawtypes")
  private ServiceRegistration<IScalingPlanBuilderSelectionPlugin> registration;

  static BundleContext getContext() {
    return Activator.context;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext )
   */
  @Override
  public void start(final BundleContext bundleContext) throws Exception {
    Activator.context = bundleContext;
    this.registration = Activator.context.registerService(IScalingPlanBuilderSelectionPlugin.class,
      new BPELSelectionInputPlugin(), null);

  }

  /*
   * (non-Javadoc)
   *
   * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
   */
  @Override
  public void stop(final BundleContext bundleContext) throws Exception {
    Activator.context = null;
    this.registration.unregister();
  }

}