package com.salesforce.bazel.eclipse.component;

import com.salesforce.bazel.eclipse.component.internal.BazelAspectLocationComponentFacade;
import com.salesforce.bazel.eclipse.component.internal.JavaCoreHelperComponentFacade;
import com.salesforce.bazel.eclipse.component.internal.ProjectManagerComponentFacade;
import com.salesforce.bazel.eclipse.component.internal.ResourceHelperComponentFacade;
import com.salesforce.bazel.eclipse.config.EclipseBazelConfigurationManager;
import com.salesforce.bazel.eclipse.preferences.BazelPreferenceKeys;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.PreferenceStoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.runtime.impl.EclipePreferenceStoreHelper;
import com.salesforce.bazel.sdk.aspect.BazelAspectLocation;
import com.salesforce.bazel.sdk.command.shell.ShellCommandBuilder;
import com.salesforce.bazel.sdk.command.shell.ShellEnvironment;
import com.salesforce.bazel.sdk.console.CommandConsoleFactory;
import com.salesforce.bazel.sdk.model.BazelConfigurationManager;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.workspace.RealOperatingEnvironmentDetectionStrategy;

public class EclipseComponentContextInitializer implements IComponentContextInitializer {
    private final String prefsScope;
    private final CommandConsoleFactory consoleFactory;

    public EclipseComponentContextInitializer(String prefsScope, CommandConsoleFactory consoleFactory) {
        this.prefsScope = prefsScope;
        this.consoleFactory = consoleFactory;
    }

    public void initialize() {

        ResourceHelper resourceHelper = ResourceHelperComponentFacade.getInstance().getComponent();
        JavaCoreHelper javaCoreHelper = JavaCoreHelperComponentFacade.getInstance().getComponent();
        OperatingEnvironmentDetectionStrategy osStrategy = new RealOperatingEnvironmentDetectionStrategy();
        BazelProjectManager projectManager = ProjectManagerComponentFacade.getInstance().getComponent();
        PreferenceStoreHelper eclipsePrefsHelper = new EclipePreferenceStoreHelper(prefsScope);
        BazelConfigurationManager configManager = new EclipseBazelConfigurationManager(eclipsePrefsHelper);
        BazelAspectLocation bazelAspectLocation = BazelAspectLocationComponentFacade.getInstance().getComponent();

        ShellCommandBuilder commandBuilder = new ShellCommandBuilder(consoleFactory, new ShellEnvironment() {

            @Override
            public boolean launchWithBashEnvironment() {
                // TODO check for OS
                return eclipsePrefsHelper.getBoolean(BazelPreferenceKeys.BAZEL_USE_SHELL_ENVIRONMENT_PREF_NAME);
            }

        });

        ComponentContext.getInstance().initialize(projectManager, resourceHelper, javaCoreHelper, osStrategy,
            configManager, eclipsePrefsHelper, bazelAspectLocation, commandBuilder,
            consoleFactory);
    }
}
