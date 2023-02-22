package com.salesforce.bazel.eclipse.component;

import org.eclipse.core.runtime.IPath;

import com.salesforce.bazel.eclipse.config.BazelAspectLocationImpl;
import com.salesforce.bazel.eclipse.config.EclipseBazelConfigurationManager;
import com.salesforce.bazel.eclipse.config.EclipseBazelProjectManager;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.PreferenceStoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.runtime.impl.EclipePreferenceStoreHelper;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseJavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseResourceHelper;
import com.salesforce.bazel.sdk.aspect.BazelAspectLocation;
import com.salesforce.bazel.sdk.command.shell.ShellCommandBuilder;
import com.salesforce.bazel.sdk.console.CommandConsoleFactory;
import com.salesforce.bazel.sdk.model.BazelConfigurationManager;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.workspace.RealOperatingEnvironmentDetectionStrategy;

public class EclipseComponentContextInitializer implements IComponentContextInitializer {
    private final String prefsScope;
    private final CommandConsoleFactory consoleFactory;
    private IPath stateLocation;

    public EclipseComponentContextInitializer(String prefsScope, CommandConsoleFactory consoleFactory, IPath stateLocation) {
        this.prefsScope = prefsScope;
        this.consoleFactory = consoleFactory;
        this.stateLocation = stateLocation;
    }

    @Override
    public void initialize() {

        ResourceHelper resourceHelper = new EclipseResourceHelper();
        JavaCoreHelper javaCoreHelper = new EclipseJavaCoreHelper();
        OperatingEnvironmentDetectionStrategy osStrategy = new RealOperatingEnvironmentDetectionStrategy();
        BazelProjectManager projectManager = new EclipseBazelProjectManager();
        PreferenceStoreHelper eclipsePrefsHelper = new EclipePreferenceStoreHelper(prefsScope);
        BazelConfigurationManager configManager = new EclipseBazelConfigurationManager(eclipsePrefsHelper);
        BazelAspectLocation bazelAspectLocation = new BazelAspectLocationImpl();

        ShellCommandBuilder commandBuilder = new ShellCommandBuilder(consoleFactory, new EclipseShellEnvironment(eclipsePrefsHelper));

        ComponentContext.getInstance().initialize(projectManager, resourceHelper, javaCoreHelper, osStrategy,
            configManager, eclipsePrefsHelper, bazelAspectLocation, commandBuilder,
            consoleFactory, stateLocation.toFile());
    }
}
