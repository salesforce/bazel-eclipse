package com.salesforce.bazel.eclipse.component.internal;

import com.salesforce.bazel.eclipse.activator.BazelEclipseExtensionPointDefinition;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;

public class ResourceHelperComponentFacade extends AbstractExtensionPointComponentFacade<ResourceHelper> {
    private static ResourceHelperComponentFacade instance;

    private ResourceHelperComponentFacade() {
    }

    public static synchronized ResourceHelperComponentFacade getInstance() {
        if (instance == null) {
            instance = new ResourceHelperComponentFacade();
        }
        return instance;
    }

    @Override
    protected BazelEclipseExtensionPointDefinition getExtensionPointDefinition() {
        return BazelEclipseExtensionPointDefinition.BAZEL_RESOURCE_HELPER;
    }

}
