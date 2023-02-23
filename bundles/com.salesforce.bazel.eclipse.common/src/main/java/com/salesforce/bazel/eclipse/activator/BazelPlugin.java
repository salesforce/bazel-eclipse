/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.eclipse.activator;

import static java.util.Objects.requireNonNull;

import java.io.File;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

import com.salesforce.bazel.eclipse.BazelCommonContstants;
import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.component.EclipseBazelWorkspaceContext;
import com.salesforce.bazel.eclipse.component.EclipseComponentContextInitializer;
import com.salesforce.bazel.eclipse.core.BazelModelManager;
import com.salesforce.bazel.eclipse.logging.EclipseLoggerFacade;
import com.salesforce.bazel.sdk.init.BazelJavaSDKInit;
import com.salesforce.bazel.sdk.init.JvmRuleInit;
import com.salesforce.bazel.sdk.workspace.BazelWorkspaceScanner;

/**
 * The activator class controls the Bazel Eclipse plugin life cycle
 */
public class BazelPlugin extends Plugin implements BazelCommonContstants {
    private static volatile BazelPlugin plugin;

    public static BazelPlugin getInstance() {
        return requireNonNull(plugin, "not initialized");
    }

    private BazelModelManager bazelModelManager;

    public BazelModelManager getBazelModelManager() {
        return requireNonNull(bazelModelManager, "not initialized");
    }

    /**
     * Sets the location on disk where the Bazel workspace is located. There must be a WORKSPACE file in this location.
     * Changing this location is a big deal, so use this method only during setup/import.
     */
    public void setBazelWorkspaceRootDirectory(String workspaceName, File rootDirectory) {
        EclipseBazelWorkspaceContext.getInstance().setBazelWorkspaceRootDirectory(workspaceName, rootDirectory);
        // write it to the preferences file
        ComponentContext.getInstance().getConfigurationManager().setBazelWorkspacePath(rootDirectory.getAbsolutePath());
    }

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        super.start(bundleContext);
        plugin = this;

        // setup the logger
        EclipseLoggerFacade.install(getBundle());

        // initialize the SDK, tell it to load the JVM rules support
        BazelJavaSDKInit.initialize("Bazel Eclipse", "bzleclipse");
        JvmRuleInit.initialize();

        // initialize the compontent Context
        var contextInitializer = new EclipseComponentContextInitializer("com.salesforce.bazel.eclipse.core", // use the old and soon new plug-in id
                new ExtensibleConsoleFactory(), getStateLocation());
        contextInitializer.initialize();

        // setup a listener, if the user changes the path to Bazel executable notify the command manager
        ComponentContext.getInstance().getConfigurationManager()
                .setBazelExecutablePathListener(ComponentContext.getInstance().getBazelCommandManager());

        // Get the bazel workspace path from the settings:
        //   ECLIPSE_WS_ROOT/.metadata/.plugins/org.eclipse.core.runtime/.settings/com.salesforce.bazel.eclipse.core.prefs
        var bazelWorkspacePathFromPrefs =
                ComponentContext.getInstance().getConfigurationManager().getBazelWorkspacePath();
        if ((bazelWorkspacePathFromPrefs != null) && !bazelWorkspacePathFromPrefs.isEmpty()) {
            var workspaceName = BazelWorkspaceScanner.getBazelWorkspaceName(bazelWorkspacePathFromPrefs);
            var workspaceRoot = new File(bazelWorkspacePathFromPrefs);
            setBazelWorkspaceRootDirectory(workspaceName, workspaceRoot);
        }

        bazelModelManager = new BazelModelManager(getStateLocation());
        bazelModelManager.initialize(ResourcesPlugin.getWorkspace());
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);

        bazelModelManager.shutdown();
    }
}
