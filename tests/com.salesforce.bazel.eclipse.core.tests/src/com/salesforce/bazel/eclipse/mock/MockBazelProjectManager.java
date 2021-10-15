package com.salesforce.bazel.eclipse.mock;

import com.salesforce.bazel.eclipse.config.AbstractBazelProjectManager;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;

public class MockBazelProjectManager extends AbstractBazelProjectManager {

    private final ResourceHelper resourceHelper;
    private final JavaCoreHelper javaCoreHelper;

    public MockBazelProjectManager(ResourceHelper resourceHelper, JavaCoreHelper javaCoreHelper) {
        this.resourceHelper = resourceHelper;
        this.javaCoreHelper = javaCoreHelper;
    }

    @Override
    protected ResourceHelper getResourceHelper() {
        return resourceHelper;
    }

    @Override
    protected JavaCoreHelper getJavaCoreHelper() {
        return javaCoreHelper;
    }

}
