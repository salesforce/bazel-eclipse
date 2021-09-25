package com.salesforce.bazel.eclipse.config;

import com.salesforce.bazel.eclipse.activator.BazelEclipseExtensionPointDefinition;
import com.salesforce.bazel.sdk.project.BazelProjectManager;

public class ProjectManagerComponentFacade extends AbstractExtensionPointComponentFacade<BazelProjectManager> {
    private static ProjectManagerComponentFacade instance;

    private ProjectManagerComponentFacade() {}

    public static synchronized ProjectManagerComponentFacade getInstance() {
        if (instance == null) {
            instance = new ProjectManagerComponentFacade();
        }
        return instance;
    }

    @Override
    protected BazelEclipseExtensionPointDefinition getExtensionPointDefinition() {
        return BazelEclipseExtensionPointDefinition.BAZEL_PROJECT_MANAGER;
    }

}
