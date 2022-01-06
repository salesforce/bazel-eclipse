package com.salesforce.bazel.eclipse.component;

import java.io.File;

import org.apache.commons.lang3.StringUtils;

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

    public static synchronized ComponentContext getInstance() {
        if (instance == null) {
            instance = new ComponentContext();
        }
        return instance;
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
        String path = StringUtils.isNotBlank(configManager.getBazelExecutablePath())
                ? configManager.getBazelExecutablePath() : BazelCompilerUtils.getBazelPath();
        final File bazelExecutablePath = new File(path);
        setBazelExecutablePath(bazelExecutablePath);
        setBazelCommandManager(
            new BazelCommandManager(aspectLocation, commandBuilder, consoleFactory, bazelExecutablePath));
        initialized = true;
    }
    
    public boolean isInitialized() {
        return initialized;
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
        return bazelCommandManager;
    }

    /**
     * Returns the model abstraction for the Bazel workspace
     */
    public BazelWorkspace getBazelWorkspace() {
        return EclipseBazelWorkspaceContext.getInstance().getBazelWorkspace();
    }

    // the only setter for test purposes
    public void setResourceHelper(ResourceHelper resourceHelper) {
        this.resourceHelper = resourceHelper;
    }

    public BazelConfigurationManager getConfigurationManager() {
        return configurationManager;
    }

    public PreferenceStoreHelper getPreferenceStoreHelper() {
        return preferenceStoreHelper;
    }

    public BazelExternalJarRuleManager getBazelExternalJarRuleManager() {
        return bazelExternalJarRuleManager;
    }

    public BazelGlobalSearchClasspathContainer getGlobalSearchClasspathContainer() {
        return globalSearchClasspathContainer;
    }

    public void setGlobalSearchClasspathContainer(BazelGlobalSearchClasspathContainer globalSearchContainer) {
        this.globalSearchClasspathContainer = globalSearchContainer;
    }

    public BazelAspectLocation getBazelAspectLocation() {
        return bazelAspectLocation;
    }

    public CommandConsoleFactory getConsoleFactory() {
        return consoleFactory;
    }

    public File getBazelExecutablePath() {
        return bazelExecutablePath;
    }

    private void setBazelCommandManager(BazelCommandManager bazelCommandManager) {
        this.bazelCommandManager = bazelCommandManager;
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

    private void setConfigurationManager(BazelConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    private void setPreferenceStoreHelper(PreferenceStoreHelper preferenceStoreHelper) {
        this.preferenceStoreHelper = preferenceStoreHelper;
    }

    private void setBazelExternalJarRuleManager(BazelExternalJarRuleManager externalJarRuleManager) {
        this.bazelExternalJarRuleManager = externalJarRuleManager;
    }

    private void setBazelAspectLocation(BazelAspectLocation bazelAspectLocation) {
        this.bazelAspectLocation = bazelAspectLocation;
    }

    private void setConsoleFactory(CommandConsoleFactory consoleFactory) {
        this.consoleFactory = consoleFactory;
    }

    private void setBazelExecutablePath(File bazelExecutablePath) {
        this.bazelExecutablePath = bazelExecutablePath;
    }

}
