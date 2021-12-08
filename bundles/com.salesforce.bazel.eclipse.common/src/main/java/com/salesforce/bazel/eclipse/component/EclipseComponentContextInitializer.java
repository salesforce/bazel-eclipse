package com.salesforce.bazel.eclipse.component;

import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

public class EclipseComponentContextInitializer implements IComponentContextInitializer {

    public void initialize() {
        ResourceHelper resourceHelper = ResourceHelperComponentFacade.getInstance().getComponent();
        JavaCoreHelper javaCoreHelper = JavaCoreHelperComponentFacade.getInstance().getComponent();
        OperatingEnvironmentDetectionStrategy osStrategy =
                EclipseBazelComponentFacade.getInstance().getOsDetectionStrategy();
        BazelProjectManager projectManager = ProjectManagerComponentFacade.getInstance().getComponent();

        ComponentContext.getInstance().initialize(projectManager, resourceHelper, javaCoreHelper, osStrategy);
    }
}
