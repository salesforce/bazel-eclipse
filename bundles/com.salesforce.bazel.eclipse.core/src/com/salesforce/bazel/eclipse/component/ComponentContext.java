package com.salesforce.bazel.eclipse.component;

import java.io.File;

import com.salesforce.bazel.eclipse.classpath.BazelGlobalSearchClasspathContainer;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.PreferenceStoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.utils.BazelCompilerUtils;
import com.salesforce.bazel.sdk.aspect.BazelAspectLocation;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.CommandBuilder;
import com.salesforce.bazel.sdk.console.CommandConsoleFactory;
import com.salesforce.bazel.sdk.lang.jvm.external.BazelExternalJarRuleManager;
import com.salesforce.bazel.sdk.model.BazelConfigurationManager;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

public class ComponentContext {
    private static ComponentContext instance;
    private static boolean initialized = false;

    public static synchronized ComponentContext getInstance() {
        if (instance == null) {
            instance = new ComponentContext();
        }
        return instance;
    }

    public static ComponentContext getInstanceCheckInitialized() throws IllegalStateException {
        var context = getInstance();
        if (!context.isInitialized()) {
            throw new IllegalStateException(
                    "ComponentContext has not been initialized yet. This is typically a problem due to activation of a plugin not happening which is an internal tooling bug. Please report it to the tool owners.");
        }
        return context;
    }

    /** ProjectManager manages all of the imported projects */
    private BazelProjectManager projectManager;
    /** ResourceHelper is a useful singleton for looking up workspace/projects from the Eclipse environment */
    private ResourceHelper resourceHelper;
    /** JavaCoreHelper is a useful singleton for working with Java projects in the Eclipse workspace */
    private JavaCoreHelper javaCoreHelper;
    /** Looks up the operating environment (e.g. OS type) */
    private OperatingEnvironmentDetectionStrategy osStrategy;
    /** Iteracts with preferences */
    private BazelConfigurationManager configurationManager;
    /** Plugin preferences helper */
    private PreferenceStoreHelper preferenceStoreHelper;
    /** Manager for working with external jars */
    private BazelExternalJarRuleManager bazelExternalJarRuleManager;
    /** Global search index of classes */
    private BazelGlobalSearchClasspathContainer globalSearchClasspathContainer;
    private BazelAspectLocation bazelAspectLocation;
    private CommandConsoleFactory consoleFactory;

    /** Facade that enables the plugin to execute the bazel command line tool outside of a workspace */
    private BazelCommandManager bazelCommandManager;

    private File bazelExecutablePath;

    private ComponentContext() {}

    public BazelAspectLocation getBazelAspectLocation() {
        return bazelAspectLocation;
    }

    public BazelCommandManager getBazelCommandManager() {
        return bazelCommandManager;
    }

    public File getBazelExecutablePath() {
        return bazelExecutablePath;
    }

    public BazelExternalJarRuleManager getBazelExternalJarRuleManager() {
        return bazelExternalJarRuleManager;
    }

    /**
     * Returns the model abstraction for the Bazel workspace
     */
    public BazelWorkspace getBazelWorkspace() {
        return EclipseBazelWorkspaceContext.getInstance().getBazelWorkspace();
    }

    public BazelConfigurationManager getConfigurationManager() {
        return configurationManager;
    }

    public CommandConsoleFactory getConsoleFactory() {
        return consoleFactory;
    }

    public BazelGlobalSearchClasspathContainer getGlobalSearchClasspathContainer() {
        return globalSearchClasspathContainer;
    }

    public JavaCoreHelper getJavaCoreHelper() {
        return javaCoreHelper;
    }

    public OperatingEnvironmentDetectionStrategy getOsStrategy() {
        return osStrategy;
    }

    public PreferenceStoreHelper getPreferenceStoreHelper() {
        return preferenceStoreHelper;
    }

    public BazelProjectManager getProjectManager() {
        return projectManager;
    }

    public ResourceHelper getResourceHelper() {
        return resourceHelper;
    }

    public synchronized void initialize(BazelProjectManager projectMgr, ResourceHelper rh, JavaCoreHelper javac,
            OperatingEnvironmentDetectionStrategy osEnv, BazelConfigurationManager configManager,
            PreferenceStoreHelper preferenceStoreHelper, BazelAspectLocation aspectLocation,
            CommandBuilder commandBuilder, CommandConsoleFactory consoleFactory) {
        setJavaCoreHelper(javac);
        setOsStrategy(osEnv);
        setProjectManager(projectMgr);
        setResourceHelper(rh);
        setConfigurationManager(configManager);
        setPreferenceStoreHelper(preferenceStoreHelper);
        setBazelExternalJarRuleManager(new BazelExternalJarRuleManager(getOsStrategy()));
        setBazelAspectLocation(aspectLocation);
        setConsoleFactory(consoleFactory);
        var path = (configManager.getBazelExecutablePath() != null) && !configManager.getBazelExecutablePath().isBlank()
                ? configManager.getBazelExecutablePath() : BazelCompilerUtils.getBazelPath();
        final var bazelExecutablePath = new File(path);
        setBazelExecutablePath(bazelExecutablePath);
        setBazelCommandManager(
            new BazelCommandManager(aspectLocation, commandBuilder, consoleFactory, bazelExecutablePath));
        initialized = true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    private void setBazelAspectLocation(BazelAspectLocation bazelAspectLocation) {
        this.bazelAspectLocation = bazelAspectLocation;
    }

    private void setBazelCommandManager(BazelCommandManager bazelCommandManager) {
        this.bazelCommandManager = bazelCommandManager;
    }

    private void setBazelExecutablePath(File bazelExecutablePath) {
        this.bazelExecutablePath = bazelExecutablePath;
    }

    private void setBazelExternalJarRuleManager(BazelExternalJarRuleManager externalJarRuleManager) {
        bazelExternalJarRuleManager = externalJarRuleManager;
    }

    private void setConfigurationManager(BazelConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    private void setConsoleFactory(CommandConsoleFactory consoleFactory) {
        this.consoleFactory = consoleFactory;
    }

    public void setGlobalSearchClasspathContainer(BazelGlobalSearchClasspathContainer globalSearchContainer) {
        globalSearchClasspathContainer = globalSearchContainer;
    }

    private void setJavaCoreHelper(JavaCoreHelper javaCoreHelper) {
        this.javaCoreHelper = javaCoreHelper;
    }

    private void setOsStrategy(OperatingEnvironmentDetectionStrategy osStrategy) {
        this.osStrategy = osStrategy;
    }

    private void setPreferenceStoreHelper(PreferenceStoreHelper preferenceStoreHelper) {
        this.preferenceStoreHelper = preferenceStoreHelper;
    }

    private void setProjectManager(BazelProjectManager projectManager) {
        this.projectManager = projectManager;
    }

    // the only setter for test purposes
    public void setResourceHelper(ResourceHelper resourceHelper) {
        this.resourceHelper = resourceHelper;
    }
}
