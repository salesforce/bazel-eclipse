package com.salesforce.bazel.eclipse.projectimport.flow;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.component.EclipseBazelWorkspaceContext;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProjectManager;

public abstract class AbstractImportFlowStep implements ImportFlow {
    private final BazelCommandManager commandManager;
    private final BazelProjectManager projectManager;
    private final ResourceHelper resourceHelper;

    public AbstractImportFlowStep(BazelCommandManager commandManager, BazelProjectManager projectManager,
            ResourceHelper resourceHelper) {
        this.commandManager = commandManager;
        this.projectManager = projectManager;
        this.resourceHelper = resourceHelper;
    }

    protected void closeBazelWorkspace() {
        EclipseBazelWorkspaceContext.getInstance().resetBazelWorkspace();
    }

    public BazelWorkspace getBazelWorkspace() {
        return ComponentContext.getInstance().getBazelWorkspace();
    }

    public BazelCommandManager getCommandManager() {
        return commandManager;
    }

    public BazelProjectManager getProjectManager() {
        return projectManager;
    }

    public ResourceHelper getResourceHelper() {
        return resourceHelper;
    }
}
