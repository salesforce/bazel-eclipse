/**
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.salesforce.bazel.eclipse;

import java.io.File;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.salesforce.bazel.eclipse.classpath.BazelGlobalSearchClasspathContainer;
import com.salesforce.bazel.eclipse.component.BazelAspectLocationComponentFacade;
import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.component.IComponentContextInitializer;
import com.salesforce.bazel.eclipse.component.EclipseBazelComponentFacade;
import com.salesforce.bazel.eclipse.component.EclipseComponentContextInitializer;
import com.salesforce.bazel.eclipse.component.ProjectManagerComponentFacade;
import com.salesforce.bazel.eclipse.component.ResourceHelperComponentFacade;
import com.salesforce.bazel.eclipse.config.EclipseBazelConfigurationManager;
import com.salesforce.bazel.eclipse.logging.EclipseLoggerFacade;
import com.salesforce.bazel.eclipse.project.BazelPluginResourceChangeListener;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.PreferenceStoreResourceHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseConsole;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseJavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.impl.PreferenceStoreEclipeResourceHelper;
import com.salesforce.bazel.sdk.aspect.BazelAspectLocation;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.command.CommandBuilder;
import com.salesforce.bazel.sdk.command.shell.ShellCommandBuilder;
import com.salesforce.bazel.sdk.console.CommandConsoleFactory;
import com.salesforce.bazel.sdk.init.BazelJavaSDKInit;
import com.salesforce.bazel.sdk.init.JvmRuleInit;
import com.salesforce.bazel.sdk.lang.jvm.external.BazelExternalJarRuleManager;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelConfigurationManager;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.workspace.BazelWorkspaceScanner;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.workspace.RealOperatingEnvironmentDetectionStrategy;

/**
 * The activator class controls the Bazel Eclipse plugin life cycle
 */
public class BazelPluginActivator extends AbstractUIPlugin {
    static final LogHelper LOG = LogHelper.log(BazelPluginActivator.class);

    // The plug-in IDs
    public static final String CORE_PLUGIN_ID = "com.salesforce.bazel.eclipse.core"; //$NON-NLS-1$
    public static final String SDK_PLUGIN_ID = "com.salesforce.bazel-java-sdk"; //$NON-NLS-1$

    // GLOBAL COLLABORATORS
    // TODO move the collaborators to some other place, perhaps a dedicated static context object

    // The shared instance
    private static BazelPluginActivator plugin;

    /**
     * PreferenceStoreResourceHelper to access {@link IPreferenceStore}
     */
    private static PreferenceStoreResourceHelper preferenceStoreResourceHelper;

    /**
     * Iteracts with preferences
     */
    private static BazelConfigurationManager configurationManager;

    /**
     * Manager for working with external jars
     */
    private static BazelExternalJarRuleManager externalJarRuleManager;

    /**
     * Global search index of classes
     */
    private static BazelGlobalSearchClasspathContainer globalSearchContainer;

    // LIFECYCLE

    /**
     * The constructor
     */
    public BazelPluginActivator() {}

    /**
     * This is the real activation entrypoint when running the core plugin in Eclipse.
     */
    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        EclipseLoggerFacade.install(getBundle());
        BazelAspectLocation aspectLocation = BazelAspectLocationComponentFacade.getInstance().getComponent();
        CommandConsoleFactory consoleFactory = new EclipseConsole();
        CommandBuilder commandBuilder = new ShellCommandBuilder(consoleFactory);
        PreferenceStoreResourceHelper eclipseResourceHelper = new PreferenceStoreEclipeResourceHelper();
        ResourceHelper resourceHelper = ResourceHelperComponentFacade.getInstance().getComponent();
        JavaCoreHelper eclipseJavaCoreHelper = new EclipseJavaCoreHelper();
        BazelProjectManager projectMgr = ProjectManagerComponentFacade.getInstance().getComponent();
        OperatingEnvironmentDetectionStrategy osEnvStrategy = new RealOperatingEnvironmentDetectionStrategy();
        BazelConfigurationManager configManager = new EclipseBazelConfigurationManager(eclipseResourceHelper);
        BazelExternalJarRuleManager externalJarRuleManager = new BazelExternalJarRuleManager(osEnvStrategy);

        // initialize the SDK, tell it to load the JVM rules support
        BazelJavaSDKInit.initialize("Bazel Eclipse", "bzleclipse");
        JvmRuleInit.initialize();
        
        startInternal(new EclipseComponentContextInitializer(), aspectLocation, commandBuilder, consoleFactory, projectMgr, resourceHelper, eclipseResourceHelper,
            eclipseJavaCoreHelper, osEnvStrategy, configManager, externalJarRuleManager);
    }

    /**
     * This is the inner entrypoint where the initialization really begins. Both the real activation entrypoint (when
     * running in Eclipse, seen above) and the mocking framework call in here. When running for real, the passed
     * collaborators are all the real ones, when running mock tests the collaborators are mocks.
     */
    public void startInternal(IComponentContextInitializer componentCtxInitializer, BazelAspectLocation aspectLocation, CommandBuilder commandBuilder,
            CommandConsoleFactory consoleFactory, BazelProjectManager projectMgr, ResourceHelper rh,
            PreferenceStoreResourceHelper crh, JavaCoreHelper javac, OperatingEnvironmentDetectionStrategy osEnv,
            BazelConfigurationManager configMgr, BazelExternalJarRuleManager externalJarRuleMgr) throws Exception {
        // reset internal state (this is so tests run in a clean env)
        EclipseBazelComponentFacade.getInstance().resetBazelWorkspace();

        // global collaborators
        componentCtxInitializer.initialize();
        preferenceStoreResourceHelper = crh;
        plugin = this;
        configurationManager = configMgr;
        externalJarRuleManager = externalJarRuleMgr;

        // Get the bazel executable path from the settings
        String bazelPath = configurationManager.getBazelExecutablePath();

        EclipseBazelComponentFacade.getInstance().setCommandManager(aspectLocation, commandBuilder, consoleFactory,
            bazelPath);

        // setup a listener, if the user changes the path to Bazel executable notify the command manager
        configurationManager.setBazelExecutablePathListener(getBazelCommandManager());

        // Get the bazel workspace path from the settings:
        //   ECLIPSE_WS_ROOT/.metadata/.plugins/org.eclipse.core.runtime/.settings/com.salesforce.bazel.eclipse.core.prefs
        String bazelWorkspacePathFromPrefs = configurationManager.getBazelWorkspacePath();
        if ((bazelWorkspacePathFromPrefs != null) && !bazelWorkspacePathFromPrefs.isEmpty()) {
            String workspaceName = BazelWorkspaceScanner.getBazelWorkspaceName(bazelWorkspacePathFromPrefs);
            setBazelWorkspaceRootDirectory(workspaceName, new File(bazelWorkspacePathFromPrefs));
        } else {
            LOG.info(
                "The workspace path property is missing from preferences, which means this is either a new Eclipse workspace or a corrupt one.");
        }

        // insert our global resource listener into the workspace
        IWorkspace eclipseWorkspace = getResourceHelper().getEclipseWorkspace();
        BazelPluginResourceChangeListener resourceChangeListener = new BazelPluginResourceChangeListener();
        eclipseWorkspace.addResourceChangeListener(resourceChangeListener);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        super.stop(context);
    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static BazelPluginActivator getInstance() {
        return plugin;
    }

    // COLLABORATORS
    // TODO move the collaborators to some other place, perhaps a dedicated static context object

    /**
     * Has the Bazel workspace location been imported/loaded? This is a good sanity check before doing any operation
     * related to Bazel or Bazel Java projects.
     */
    public static boolean hasBazelWorkspaceRootDirectory() {
        return EclipseBazelComponentFacade.getInstance().hasBazelWorkspaceRootDirectory();
    }

    /**
     * Returns the model abstraction for the Bazel workspace
     */
    public static BazelWorkspace getBazelWorkspace() {
        return EclipseBazelComponentFacade.getInstance().getBazelWorkspace();
    }

    /**
     * Returns the location on disk where the Bazel workspace is located. There must be a WORKSPACE file in this
     * location. Prior to importing/opening a Bazel workspace, this location will be null
     */
    public static File getBazelWorkspaceRootDirectory() {
        return EclipseBazelComponentFacade.getInstance().getBazelWorkspaceRootDirectory();
    }

    /**
     * Sets the location on disk where the Bazel workspace is located. There must be a WORKSPACE file in this location.
     * Changing this location is a big deal, so use this method only during setup/import.
     */
    public void setBazelWorkspaceRootDirectory(String workspaceName, File rootDirectory) {
        EclipseBazelComponentFacade.getInstance().setBazelWorkspaceRootDirectory(workspaceName, rootDirectory);
        // write it to the preferences file
        configurationManager.setBazelWorkspacePath(rootDirectory.getAbsolutePath());
    }

    /**
     * Returns the unique instance of {@link BazelCommandManager}, the facade enables the plugin to execute the bazel
     * command line tool.
     */
    public static BazelCommandManager getBazelCommandManager() {
        return EclipseBazelComponentFacade.getInstance().getBazelCommandManager();
    }

    /**
     * Once the workspace is set, the workspace command runner is available. Otherwise returns null
     */
    public BazelWorkspaceCommandRunner getWorkspaceCommandRunner() {
        return EclipseBazelComponentFacade.getInstance().getWorkspaceCommandRunner();
    }

    /**
     * Returns the manager for imported projects
     *
     * @return
     */
    public static BazelProjectManager getBazelProjectManager() {
        return ComponentContext.getInstance().getProjectManager();
    }

    /**
     * Returns the unique instance of {@link ResourceHelper}, this helper helps retrieve workspace and project objects
     * from the environment
     */
    public static ResourceHelper getResourceHelper() {
        return ComponentContext.getInstance().getResourceHelper();
    }

    public static PreferenceStoreResourceHelper getPreferenceStoreResourceHelper() {
        return preferenceStoreResourceHelper;
    }

    /**
     * Returns the unique instance of {@link JavaCoreHelper}, this helper helps manipulate the Java configuration of a
     * Java project
     */
    public static JavaCoreHelper getJavaCoreHelper() {
        return ComponentContext.getInstance().getJavaCoreHelper();
    }

    /**
     * Provides details of the operating environment (OS, real vs. tests, etc)
     */
    public OperatingEnvironmentDetectionStrategy getOperatingEnvironmentDetectionStrategy() {
        return ComponentContext.getInstance().getOsStrategy();
    }

    /**
     * Returns the config manager for projects
     */
    public BazelConfigurationManager getConfigurationManager() {
        return configurationManager;
    }

    public BazelExternalJarRuleManager getBazelExternalJarRuleManager() {
        return externalJarRuleManager;
    }

    public void setGlobalSearchClasspathContainer(BazelGlobalSearchClasspathContainer index) {
        globalSearchContainer = index;
    }

    public BazelGlobalSearchClasspathContainer getGlobalSearchClasspathContainer() {
        return globalSearchContainer;
    }

    // DANGER ZONE

    /**
     * User is deleting the Bazel Workspace project from the Eclipse workspace. Do what we can here. To reset back to
     * initial state, but hard to guarantee that this will be perfect. If the user does NOT also delete the Bazel
     * workspace code projects, there could be trouble.
     */
    public void closeBazelWorkspace() {
        // now forget about the workspace
        EclipseBazelComponentFacade.getInstance().resetBazelWorkspace();
    }

    // TEST ONLY

    /**
     * For some partial mocked tests, setting the ResourceHelper (which is used widely) without fully initializing the
     * plugin can be faster, and less code. Do NOT use this method otherwise.
     */
    public static void setResourceHelperForTests(ResourceHelper rh) {
        ComponentContext.getInstance().setResourceHelper(rh);
    }

}
