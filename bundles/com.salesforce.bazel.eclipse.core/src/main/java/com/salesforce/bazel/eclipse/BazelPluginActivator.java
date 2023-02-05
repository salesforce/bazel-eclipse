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
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.component.EclipseBazelWorkspaceContext;
import com.salesforce.bazel.eclipse.component.EclipseComponentContextInitializer;
import com.salesforce.bazel.eclipse.component.IComponentContextInitializer;
import com.salesforce.bazel.eclipse.logging.EclipseLoggerFacade;
import com.salesforce.bazel.eclipse.project.BazelPluginResourceChangeListener;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseConsole;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseJavaCoreHelper;
import com.salesforce.bazel.sdk.command.CommandBuilder;
import com.salesforce.bazel.sdk.command.shell.ShellCommandBuilder;
import com.salesforce.bazel.sdk.console.CommandConsoleFactory;
import com.salesforce.bazel.sdk.init.BazelJavaSDKInit;
import com.salesforce.bazel.sdk.init.JvmRuleInit;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.workspace.BazelWorkspaceScanner;

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
        CommandConsoleFactory consoleFactory = new EclipseConsole();
        JavaCoreHelper eclipseJavaCoreHelper = new EclipseJavaCoreHelper();

        // initialize the SDK, tell it to load the JVM rules support
        BazelJavaSDKInit.initialize("Bazel Eclipse", "bzleclipse");
        JvmRuleInit.initialize();

        startInternal(new EclipseComponentContextInitializer(getBundle().getSymbolicName(), new EclipseConsole()),
            consoleFactory, eclipseJavaCoreHelper);
    }

    /**
     * This is the inner entrypoint where the initialization really begins. Both the real activation entrypoint (when
     * running in Eclipse, seen above) and the mocking framework call in here. When running for real, the passed
     * collaborators are all the real ones, when running mock tests the collaborators are mocks.
     */
    public void startInternal(IComponentContextInitializer componentCtxInitializer,
            CommandConsoleFactory consoleFactory, JavaCoreHelper javac) throws Exception {
        // reset internal state (this is so tests run in a clean env)
        EclipseBazelWorkspaceContext.getInstance().resetBazelWorkspace();

        // global collaborators
        componentCtxInitializer.initialize();
        plugin = this;

        // setup a listener, if the user changes the path to Bazel executable notify the command manager
        ComponentContext.getInstance().getConfigurationManager()
                .setBazelExecutablePathListener(ComponentContext.getInstance().getBazelCommandManager());

        // Get the bazel workspace path from the settings:
        //   ECLIPSE_WS_ROOT/.metadata/.plugins/org.eclipse.core.runtime/.settings/com.salesforce.bazel.eclipse.core.prefs
        String bazelWorkspacePathFromPrefs =
                ComponentContext.getInstance().getConfigurationManager().getBazelWorkspacePath();
        if ((bazelWorkspacePathFromPrefs != null) && !bazelWorkspacePathFromPrefs.isEmpty()) {
            String workspaceName = BazelWorkspaceScanner.getBazelWorkspaceName(bazelWorkspacePathFromPrefs);
            setBazelWorkspaceRootDirectory(workspaceName, new File(bazelWorkspacePathFromPrefs));
        } else {
            LOG.info(
                "The workspace path property is missing from preferences, which means this is either a new Eclipse workspace or a corrupt one.");
        }

        // insert our global resource listener into the workspace
        IWorkspace eclipseWorkspace = ComponentContext.getInstance().getResourceHelper().getEclipseWorkspace();
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
     * Sets the location on disk where the Bazel workspace is located. There must be a WORKSPACE file in this location.
     * Changing this location is a big deal, so use this method only during setup/import.
     */
    public void setBazelWorkspaceRootDirectory(String workspaceName, File rootDirectory) {
        EclipseBazelWorkspaceContext.getInstance().setBazelWorkspaceRootDirectory(workspaceName, rootDirectory);
        // write it to the preferences file
        ComponentContext.getInstance().getConfigurationManager().setBazelWorkspacePath(rootDirectory.getAbsolutePath());
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
