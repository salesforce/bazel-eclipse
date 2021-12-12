package com.salesforce.bazel.eclipse.component;

import com.salesforce.bazel.eclipse.config.EclipseBazelConfigurationManager;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.PreferenceStoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.runtime.impl.EclipePreferenceStoreHelper;
import com.salesforce.bazel.sdk.model.BazelConfigurationManager;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

public class EclipseComponentContextInitializer implements IComponentContextInitializer {
    private final String prefsScope;

    public EclipseComponentContextInitializer(String prefsScope) {
        this.prefsScope = prefsScope;
    }

    public void initialize() {
        ResourceHelper resourceHelper = ResourceHelperComponentFacade.getInstance().getComponent();
        JavaCoreHelper javaCoreHelper = JavaCoreHelperComponentFacade.getInstance().getComponent();
        OperatingEnvironmentDetectionStrategy osStrategy =
                EclipseBazelComponentFacade.getInstance().getOsDetectionStrategy();
        BazelProjectManager projectManager = ProjectManagerComponentFacade.getInstance().getComponent();
        PreferenceStoreHelper eclipsePrefsHelper = new EclipePreferenceStoreHelper(prefsScope);
        BazelConfigurationManager configManager = new EclipseBazelConfigurationManager(eclipsePrefsHelper);

        ComponentContext.getInstance().initialize(projectManager, resourceHelper, javaCoreHelper, osStrategy,
            configManager, eclipsePrefsHelper);
    }
}
