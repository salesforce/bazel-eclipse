package com.salesforce.bazel.eclipse.component;

import com.salesforce.bazel.eclipse.activator.BazelEclipseExtensionPointDefinition;
import com.salesforce.bazel.sdk.aspect.BazelAspectLocation;

public class BazelAspectLocationComponentFacade extends AbstractExtensionPointComponentFacade<BazelAspectLocation> {
    private static BazelAspectLocationComponentFacade instance;

    private BazelAspectLocationComponentFacade() {}

    public static synchronized BazelAspectLocationComponentFacade getInstance() {
        if (instance == null) {
            instance = new BazelAspectLocationComponentFacade();
        }
        return instance;
    }

    @Override
    protected BazelEclipseExtensionPointDefinition getExtensionPointDefinition() {
        return BazelEclipseExtensionPointDefinition.BAZEL_ASPECT_LOCATION;
    }

}
