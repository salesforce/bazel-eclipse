package com.salesforce.bazel.eclipse.component;

import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

public class ComponentContext {
    private static ComponentContext instance;

    /**
     * ProjectManager manages all of the imported projects
     */
    private BazelProjectManager projectManager;
    /**
     * ResourceHelper is a useful singleton for looking up workspace/projects from the Eclipse environment
     */
    private ResourceHelper resourceHelper;
    /**
     * JavaCoreHelper is a useful singleton for working with Java projects in the Eclipse workspace
     */
    private JavaCoreHelper javaCoreHelper;
    /**
     * Looks up the operating environment (e.g. OS type)
     */
    private OperatingEnvironmentDetectionStrategy osStrategy;

    private ComponentContext() {}

    public static synchronized ComponentContext getInstance() {
        if (instance == null) {
            instance = new ComponentContext();
        }
        return instance;
    }

    public synchronized void initialize(BazelProjectManager projectMgr, ResourceHelper rh, JavaCoreHelper javac,
            OperatingEnvironmentDetectionStrategy osEnv) {
        setJavaCoreHelper(javac);
        setOsStrategy(osEnv);
        setProjectManager(projectMgr);
        setResourceHelper(rh);
    }

    public BazelProjectManager getProjectManager() {
        return projectManager;
    }

    public ResourceHelper getResourceHelper() {
        return resourceHelper;
    }

    public JavaCoreHelper getJavaCoreHelper() {
        return javaCoreHelper;
    }

    public OperatingEnvironmentDetectionStrategy getOsStrategy() {
        return osStrategy;
    }

    public BazelCommandManager getBazelCommandManager() {
        return EclipseBazelComponentFacade.getInstance().getBazelCommandManager();
    }

    /**
     * Returns the model abstraction for the Bazel workspace
     */
    public BazelWorkspace getBazelWorkspace() {
        return EclipseBazelComponentFacade.getInstance().getBazelWorkspace();
    }

    // the only setter for test purposes
    public void setResourceHelper(ResourceHelper resourceHelper) {
        this.resourceHelper = resourceHelper;
    }

    private void setProjectManager(BazelProjectManager projectManager) {
        this.projectManager = projectManager;
    }

    private void setJavaCoreHelper(JavaCoreHelper javaCoreHelper) {
        this.javaCoreHelper = javaCoreHelper;
    }

    private void setOsStrategy(OperatingEnvironmentDetectionStrategy osStrategy) {
        this.osStrategy = osStrategy;
    }
}
