package com.salesforce.bazel.eclipse.ui;

import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class BazelUIPlugin extends AbstractUIPlugin {

    // The plug-in ID
    public static final String PLUGIN_ID = "com.salesforce.bazel.eclipse.ui"; //$NON-NLS-1$

    // The shared instance
    private static BazelUIPlugin plugin;

    public static final String ICON_BAZEL = "icon_bazel";

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static BazelUIPlugin getDefault() {
        return plugin;
    }

    /**
     * The constructor
     */
    public BazelUIPlugin() {}

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }

    @Override
    protected void initializeImageRegistry(ImageRegistry reg) {
        reg.put(ICON_BAZEL, imageDescriptorFromPlugin(PLUGIN_ID, "resources/bazelicon.gif"));
    }
}
