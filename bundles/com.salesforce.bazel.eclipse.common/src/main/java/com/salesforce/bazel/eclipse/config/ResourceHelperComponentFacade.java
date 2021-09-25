package com.salesforce.bazel.eclipse.config;

import com.salesforce.bazel.eclipse.activator.BazelEclipseExtensionPointDefinition;
import com.salesforce.bazel.eclipse.runtime.api.BaseResourceHelper;

public class ResourceHelperComponentFacade extends AbstractExtensionPointComponentFacade<BaseResourceHelper> {
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
