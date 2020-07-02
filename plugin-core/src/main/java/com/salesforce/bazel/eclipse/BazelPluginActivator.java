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

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.salesforce.bazel.eclipse.config.BazelAspectLocationImpl;
import com.salesforce.bazel.eclipse.config.BazelEclipseProjectFactory;
import com.salesforce.bazel.eclipse.config.EclipseBazelProjectManager;
import com.salesforce.bazel.eclipse.config.EclipseBazelConfigurationManager;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseConsole;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseJavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseResourceHelper;
import com.salesforce.bazel.sdk.abstractions.BazelAspectLocation;
import com.salesforce.bazel.sdk.abstractions.CommandConsoleFactory;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.command.CommandBuilder;
import com.salesforce.bazel.sdk.command.shell.ShellCommandBuilder;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.model.OperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.model.BazelConfigurationManager;
import com.salesforce.bazel.sdk.model.RealOperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.project.BazelProjectManager;

/**
 * The activator class controls the Bazel Eclipse plugin life cycle
 */
public class BazelPluginActivator extends AbstractUIPlugin {
    static final LogHelper LOG = LogHelper.log(BazelPluginActivator.class);
    // we log to the LOG object by default, but if we detect it could not be configured, we set this to true and log to sys.err instead
    private static boolean logToSystemErr = false;

    // The plug-in ID
    public static final String PLUGIN_ID = "com.salesforce.bazel.eclipse.core"; //$NON-NLS-1$

    // GLOBAL COLLABORATORS
    // TODO move the collaborators to some other place, perhaps a dedicated static context object
    
    // The shared instance
    private static BazelPluginActivator plugin;

    /**
     * The Bazel workspace that is in scope.
     * Currently, we only support one Bazel workspace in an Eclipse workspace so this is a static singleton.
     */
    private static BazelWorkspace bazelWorkspace = null;
    
    /**
     * Facade that enables the plugin to execute the bazel command line tool outside of a workspace
     */
    private static BazelCommandManager bazelCommandManager;
    
    /**
     * Runs bazel commands in the loaded workspace.
     */
    private static BazelWorkspaceCommandRunner bazelWorkspaceCommandRunner;
    
    /**
     * ProjectManager manages all of the imported projects
     */
    private static BazelProjectManager bazelProjectManager;
    
    /**
     * ResourceHelper is a useful singleton for looking up workspace/projects from the Eclipse environment
     */
    private static ResourceHelper resourceHelper;
    
    /**
     * JavaCoreHelper is a useful singleton for working with Java projects in the Eclipse workspace
     */
    private static JavaCoreHelper javaCoreHelper;
    
    /**
     * Looks up the operating environment (e.g. OS type)
     */
    private static OperatingEnvironmentDetectionStrategy osEnvStrategy;
    
    /**
     * Iteracts with preferences
     */
    private static BazelConfigurationManager configurationManager;

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
        EclipseLoggerFacade.install(context.getBundle());
        super.start(context);
        BazelAspectLocation aspectLocation = new BazelAspectLocationImpl();
        CommandConsoleFactory consoleFactory = new EclipseConsole();
        CommandBuilder  commandBuilder = new ShellCommandBuilder(consoleFactory);
        ResourceHelper eclipseResourceHelper = new EclipseResourceHelper();
        JavaCoreHelper eclipseJavaCoreHelper = new EclipseJavaCoreHelper();
        BazelProjectManager projectMgr = new EclipseBazelProjectManager(eclipseResourceHelper, eclipseJavaCoreHelper);
        OperatingEnvironmentDetectionStrategy osEnvStrategy = new RealOperatingEnvironmentDetectionStrategy();
        BazelConfigurationManager configManager = new EclipseBazelConfigurationManager(eclipseResourceHelper);
        
        startInternal(aspectLocation, commandBuilder, consoleFactory, projectMgr, eclipseResourceHelper, eclipseJavaCoreHelper, 
        		osEnvStrategy, configManager);
    }

    /**
     * This is the inner entrypoint where the initialization really begins. Both the real activation entrypoint
     * (when running in Eclipse, seen above) and the mocking framework call in here. When running for real,
     * the passed collaborators are all the real ones, when running mock tests the collaborators are mocks.
     */
    public void startInternal(BazelAspectLocation aspectLocation, CommandBuilder commandBuilder, CommandConsoleFactory consoleFactory, 
    		BazelProjectManager projectMgr, ResourceHelper rh, JavaCoreHelper javac, OperatingEnvironmentDetectionStrategy osEnv, 
    		BazelConfigurationManager configMgr) throws Exception {
        // reset internal state (this is so tests run in a clean env)
        bazelWorkspace = null;
        bazelWorkspaceCommandRunner = null;
        
        // global collaborators
        resourceHelper = rh;
        plugin = this;
        javaCoreHelper = javac;
        osEnvStrategy = osEnv;
        bazelProjectManager = projectMgr;
        configurationManager = configMgr;

        // Get the bazel executable path from the settings, defaults to /usr/local/bin/bazel
        String bazelPath = configurationManager.getBazelExecutablePath();
        File bazelPathFile = new File(bazelPath);

        // Build the command manager for the workspace (runs Bazel commands)
        bazelCommandManager = new BazelCommandManager(aspectLocation, commandBuilder, consoleFactory, bazelPathFile);

        // setup a listener, if the user changes the path to Bazel executable notify the command manager
        configurationManager.setBazelExecutablePathListener(bazelCommandManager);

        // Get the bazel workspace path from the settings: 
        //   ECLIPSE_WS_ROOT/.metadata/.plugins/org.eclipse.core.runtime/.settings/com.salesforce.bazel.eclipse.core.prefs
        String bazelWorkspacePathFromPrefs = configurationManager.getBazelWorkspacePath();
        if (bazelWorkspacePathFromPrefs != null && !bazelWorkspacePathFromPrefs.isEmpty()) {
            String workspaceName = BazelEclipseProjectFactory.getBazelWorkspaceName(bazelWorkspacePathFromPrefs);
            this.setBazelWorkspaceRootDirectory(workspaceName, new File(bazelWorkspacePathFromPrefs));
        } else {
            LOG.info("The workspace path property is missing from preferences, which means this is either a new Eclipse workspace or a corrupt one.");
        }
    }
    
    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        bazelCommandManager = null;
        resourceHelper = null;
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
        return bazelWorkspace.hasBazelWorkspaceRootDirectory();
    }

    /**
     * Returns the model abstraction for the Bazel workspace
     */
    public static BazelWorkspace getBazelWorkspace() {
        return bazelWorkspace;
    }

    /**
     * Returns the location on disk where the Bazel workspace is located. There must be a WORKSPACE file
     * in this location. Prior to importing/opening a Bazel workspace, this location will be null
     */
    public static File getBazelWorkspaceRootDirectory() {
        return bazelWorkspace.getBazelWorkspaceRootDirectory();
    }

    /**
     * Sets the location on disk where the Bazel workspace is located. There must be a WORKSPACE file
     * in this location. Changing this location is a big deal, so use this method only during setup/import.
     */
    public void setBazelWorkspaceRootDirectory(String workspaceName, File rootDirectory) {
        File workspaceFile = new File(rootDirectory, "WORKSPACE");
        if (!workspaceFile.exists()) {
            workspaceFile = new File(rootDirectory, "WORKSPACE.bazel");
            if (!workspaceFile.exists()) {
                new Throwable().printStackTrace();
                BazelPluginActivator.error("BazelPluginActivator could not set the Bazel workspace directory as there is no WORKSPACE file here: "+rootDirectory.getAbsolutePath());
                return;
            }
        }
        bazelWorkspace = new BazelWorkspace(workspaceName, rootDirectory, osEnvStrategy);
        bazelWorkspace.setBazelWorkspaceMetadataStrategy(getWorkspaceCommandRunner());

        // write it to the preferences file
        configurationManager.setBazelWorkspacePath(rootDirectory.getAbsolutePath());
    }
    
    
    /**
     * Returns the unique instance of {@link BazelCommandManager}, the facade enables the plugin to execute the bazel
     * command line tool.
     */
    public static BazelCommandManager getBazelCommandManager() {
        return bazelCommandManager;
    }

    /**
     * Once the workspace is set, the workspace command runner is available. Otherwise returns null
     */
    public BazelWorkspaceCommandRunner getWorkspaceCommandRunner() {
        if (bazelWorkspaceCommandRunner == null) {
            if (bazelWorkspace == null) {
                return null;
            }
            if (bazelWorkspace.hasBazelWorkspaceRootDirectory()) {
                bazelWorkspaceCommandRunner = bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);
            }
        }
        return bazelWorkspaceCommandRunner;
    }
    
    /**
     * Returns the manager for imported projects
     * @return
     */
    public static BazelProjectManager getBazelProjectManager() {
    	return bazelProjectManager;
    }
    
    /**
     * Returns the unique instance of {@link ResourceHelper}, this helper helps retrieve workspace and project
     * objects from the environment
     */
    public static ResourceHelper getResourceHelper() {
        return resourceHelper;
    }

    /**
     * Returns the unique instance of {@link JavaCoreHelper}, this helper helps manipulate the Java configuration
     * of a Java project
     */
    public static JavaCoreHelper getJavaCoreHelper() {
        return javaCoreHelper;
    }
    
    /**
     * Provides details of the operating environment (OS, real vs. tests, etc)
     */
    public OperatingEnvironmentDetectionStrategy getOperatingEnvironmentDetectionStrategy() {
        return osEnvStrategy;
    }
    
    /**
     * Returns the config manager for projects
     */
    public BazelConfigurationManager getConfigurationManager() {
    	return configurationManager;
    }
    
    // LOGGING
    
    /**
     * Log an error to the eclipse log.
     */
    public static void error(String message) {
        if (logToSystemErr) {
            System.err.println(message);
        } else {
            LOG.error(message);
        }
    }

    /**
     * Log an error to the eclipse log, with an attached exception.
     */
    public static void error(String message, Throwable exception) {
        if (logToSystemErr) {
            exception.printStackTrace();
            System.err.println(message);
        } else {
            LOG.error(message, exception);
        }
    }

    /**
     * Log an info message to the eclipse log.
     */
    public static void info(String message) {
        if (logToSystemErr) {
            System.err.println(message);
        } else {
            LOG.info(message);
        }
    }

    /**
     * Log an info message to the eclipse log.
     */
    public static void debug(String message) {
        LOG.debug(message);
    }

    /**
     * If there is a failure in configuring the logging subsytem, this method gets called such that logging is
     * sent to System.err
     */
    public static void logToSystemErr() {
        logToSystemErr = true;
    }

    
    // TEST ONLY
    
    /**
     * For some partial mocked tests, setting the ResourceHelper (which is used widely) without
     * fully initializing the plugin can be faster, and less code. Do NOT use this method otherwise.
     */
    public static void setResourceHelperForTests(ResourceHelper rh) {
        resourceHelper = rh;
    }

}
