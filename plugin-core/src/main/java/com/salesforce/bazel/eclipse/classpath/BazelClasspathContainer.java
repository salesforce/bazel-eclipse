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
package com.salesforce.bazel.eclipse.classpath;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaModelException;
import org.osgi.service.prefs.BackingStoreException;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.config.BazelProjectPreferences;
import com.salesforce.bazel.eclipse.config.EclipseProjectBazelTargets;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.model.BazelProblem;
import com.salesforce.bazel.sdk.model.BazelProject;
import com.salesforce.bazel.sdk.model.BazelProjectManager;
import com.salesforce.bazel.sdk.model.BazelWorkspace;

/**
 * Computes the classpath for a Bazel package and provides it to the JDT tooling in Eclipse.
 */
public class BazelClasspathContainer implements IClasspathContainer {
    public static final String CONTAINER_NAME = "com.salesforce.bazel.eclipse.BAZEL_CONTAINER";
    private static BazelProjectManager bazelProjectManager = new BazelProjectManager();
    
    private final IPath eclipseProjectPath;
    private final IProject eclipseProject;
    private final boolean eclipseProjectIsRoot;
    private final BazelClasspathContainerImpl impl;
    
    private static List<BazelClasspathContainerImpl> instances = new ArrayList<>();
    
    public BazelClasspathContainer(IProject eclipseProject)
            throws IOException, InterruptedException, BackingStoreException, JavaModelException,
            BazelCommandLineToolConfigurationException {
        this(eclipseProject, BazelPluginActivator.getResourceHelper());
    }
    
    BazelClasspathContainer(IProject eclipseProject, ResourceHelper resourceHelper)
            throws IOException, InterruptedException, BackingStoreException, JavaModelException,
            BazelCommandLineToolConfigurationException {
        this.eclipseProject = eclipseProject;
        this.eclipseProjectPath = eclipseProject.getLocation();
        this.eclipseProjectIsRoot = resourceHelper.isBazelRootProject(eclipseProject);
        
        BazelProject bazelProject = new BazelProject(eclipseProject.getName(), eclipseProject);
        bazelProjectManager.addProject(bazelProject);
        
        impl = new BazelClasspathContainerImpl(BazelPluginActivator.getBazelWorkspace(), bazelProject, 
        		resourceHelper.isBazelRootProject(eclipseProject), resourceHelper,
        		new EclipseImplicitClasspathHelper());
        instances.add(impl);
    }
    
    public static void clean() {
        for (BazelClasspathContainerImpl instance : instances) {
        	instance.clean();
        }
    }

    @Override
    public IClasspathEntry[] getClasspathEntries() {
        return impl.getClasspathEntries();
    }

    @Override
    public String getDescription() {
        return "Bazel Classpath Container";
    }

    @Override
    public int getKind() {
        return K_APPLICATION;
    }

    @Override
    public IPath getPath() {
        return eclipseProjectPath;
    }

    /**
     * Runs a build and returns true if no errors are returned.
     */
    public boolean isValid() throws BackingStoreException, IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        BazelWorkspace bazelWorkspace = BazelPluginActivator.getBazelWorkspace();
        if (bazelWorkspace == null) {
            return false;
        }
        File bazelWorkspaceRootDirectory = bazelWorkspace.getBazelWorkspaceRootDirectory();
        if (bazelWorkspaceRootDirectory == null) {
            return false;
        }
        BazelCommandManager bazelCommandManager = BazelPluginActivator.getBazelCommandManager();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);
        
        if (bazelWorkspaceCmdRunner != null) {
            if (this.eclipseProjectIsRoot) {
                return true;
            }
            EclipseProjectBazelTargets targets = BazelProjectPreferences.getConfiguredBazelTargets(this.eclipseProject.getProject(), false);
            List<BazelProblem> details = bazelWorkspaceCmdRunner.runBazelBuild(targets.getConfiguredTargets(), null, Collections.emptyList(), null, null);
            for (BazelProblem detail : details) {
                BazelPluginActivator.error(detail.toString());
            }
            return details.isEmpty();

        }
        return false;
    }

}
