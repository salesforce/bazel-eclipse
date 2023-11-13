package com.salesforce.bazel.eclipse.ui;

import static java.util.Objects.requireNonNull;

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
    public static final String IMG_VIEW_ARGUMENTS_TAB = "views/variable_tab.png";

    private static String bundleVersion;

    public static String getBundleVersion() {
        return requireNonNull(bundleVersion, "Bundle version not initialized. Is the UI bundle properly started?");
    }

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
    protected void initializeImageRegistry(ImageRegistry reg) {
        reg.put(ICON_BAZEL, imageDescriptorFromPlugin(PLUGIN_ID, "resources/bazelicon.gif"));
        reg.put(IMG_VIEW_ARGUMENTS_TAB, imageDescriptorFromPlugin(PLUGIN_ID, "resources/" + IMG_VIEW_ARGUMENTS_TAB));
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        bundleVersion = context.getBundle().getVersion().toString();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }
}
