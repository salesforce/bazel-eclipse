/*-
 *
 */
package com.salesforce.bazel.scipls;

import static java.util.Objects.requireNonNull;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

/**
 * Plug-in (OSGi bundle) activator for SCIP bases Java LS.
 */
public class BazelScipLsPlugin extends Plugin {

    private static BazelScipLsPlugin plugin;

    public static BazelScipLsPlugin getInstance() {
        return requireNonNull(plugin, "not initialized");
    }

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        super.start(bundleContext);
        plugin = this;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }
}
