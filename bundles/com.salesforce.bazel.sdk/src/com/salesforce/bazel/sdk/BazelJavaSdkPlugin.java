package com.salesforce.bazel.sdk;

import static java.util.Objects.requireNonNull;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

public class BazelJavaSdkPlugin extends Plugin {

    private static String bundleVersion;

    public static String getBundleVersion() {
        return requireNonNull(bundleVersion,
            "Bundle version not initialized. If this is not running inside OSGi please implement support for an alternate way. Otherwise please ensure the SDK bundle is started properly.");
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        bundleVersion = context.getBundle().getVersion().toString();
    }
}
