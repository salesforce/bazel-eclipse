package com.salesforce.bazel.eclipse.mock;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.component.IComponentContextInitializer;

public class MockComponentContextInitializer implements IComponentContextInitializer {
    private final MockEclipse mockEclipse;

    public MockComponentContextInitializer(MockEclipse mockEclipse) {
        this.mockEclipse = mockEclipse;
    }

    @Override
    public void initialize() {
        ComponentContext.getInstance().initialize(mockEclipse.getProjectManager(), mockEclipse.getMockResourceHelper(),
            mockEclipse.getMockJavaCoreHelper(), mockEclipse.getOsEnvStrategy(), mockEclipse.getConfigManager(),
            mockEclipse.getMockCorePreferencesStoreHelper(), mockEclipse.getMockBazelAspectLocation(),
            mockEclipse.getMockCommandBuilder(), mockEclipse.getMockCommandConsole());
    }

}
