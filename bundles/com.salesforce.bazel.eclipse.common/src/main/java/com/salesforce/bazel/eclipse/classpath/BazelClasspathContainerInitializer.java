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

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.osgi.service.prefs.BackingStoreException;

import com.salesforce.bazel.eclipse.BazelNature;
import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.project.EclipseProjectUtils;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;

public class BazelClasspathContainerInitializer extends ClasspathContainerInitializer {
    private static final LogHelper LOG = LogHelper.log(BazelClasspathContainerInitializer.class);

    private static final String CLASSPATH_CONTAINER_UPDATE_JOB_NAME = "BazelClasspathContainerUpdate";

    // error state
    public static AtomicBoolean isCorrupt = new AtomicBoolean(false);

    @Override
    public void initialize(IPath eclipseProjectPath, IJavaProject eclipseJavaProject) throws CoreException {
        IProject eclipseProject = eclipseJavaProject.getProject();
        String eclipseProjectName = eclipseProject.getName();
        try {
            //remove projects added to the workspace after a corrupted package in identified
            if (isCorrupt.get()) {
                undo(eclipseJavaProject.getProject());
                return;
            }
            
            // ComponentContext is a lightweight wireup facility for collaborators; since we rely on it 
            // we verify the status here because our initialize() method gets invoked early in the plugin 
            // lifecycle and we want to verify plugin activation happened as expected
            ComponentContext context = ComponentContext.getInstance();
            if (!context.isInitialized()) {
                String message = "Failure initializing Bazel classpath container for project "+eclipseProjectName+" because the "
                        + "internal ComponentContext has not been initialized yet. This is typically a problem due to activation "+
                        "of a plugin not happening which is an internal tooling bug. Please report it to the tool owners.";
                LOG.error(message);
                throw new IllegalStateException(message);
            }

            // create the BazelProject if necessary
            BazelProjectManager bazelProjectManager = context.getProjectManager();
            BazelProject bazelProject = bazelProjectManager.getProject(eclipseProjectName);
            if (bazelProject == null) {
                bazelProject = new BazelProject(eclipseProject.getName(), eclipseProject);
                bazelProjectManager.addProject(bazelProject);
            }

            boolean isRootProject = eclipseJavaProject.getProject().getName().contains(BazelNature.BAZELWORKSPACE_PROJECT_BASENAME);
            IClasspathContainer container = getClasspathContainer(eclipseProject, isRootProject);

            if (isRootProject) {
                setClasspathContainerForProject(eclipseProjectPath, eclipseJavaProject, container);
            } else {
                setClasspathContainerForProject(eclipseProjectPath, eclipseJavaProject, container);
            }
        } catch (BazelCommandLineToolConfigurationException e) {
            String message = "Error while initializing Bazel classpath container for project "+eclipseProjectName+
                    " because the Bazel executable failed invocation. Root cause: "+e.getMessage();
            LOG.error(message, e);
            throw new IllegalStateException(message);
        } catch (Exception anyE) {
            String message = "Error while initializing Bazel classpath container for project "+eclipseProjectName+
                    ". Root cause: "+anyE.getMessage();
            LOG.error(message, anyE);
            throw new IllegalStateException(message);
        }
    }

    @Override
    public boolean canUpdateClasspathContainer(IPath containerPath, IJavaProject project) {
        return true;
    }

    @Override
    public void requestClasspathContainerUpdate(IPath containerPath, IJavaProject javaProject,
            IClasspathContainer containerSuggestion) throws CoreException {
        final boolean isRootProject = false; // this is wrong if the root project has a BUILD file ...
        IProject project = javaProject.getProject();
        flushProjectCaches(project);
        Job.create(CLASSPATH_CONTAINER_UPDATE_JOB_NAME, monitor -> {
            try {
                // let the ClasspathContainer recompute its entries
                IClasspathContainer container = getClasspathContainer(project, isRootProject);
                setClasspathContainerForProject(containerPath, javaProject, container, monitor);
                LOG.info("Updated classpath container of " + project.getName());
            } catch (IOException | InterruptedException | BackingStoreException e) {
                LOG.error("Error while updating Bazel classpath container.", e);
            } catch (BazelCommandLineToolConfigurationException e) {
                LOG.error("Bazel not found: " + e.getMessage());
            }
        }).schedule();
    }

    private static void flushProjectCaches(IProject project) {
        // get downstream projects of the given project
        JavaCoreHelper javaCoreHelper = ComponentContext.getInstance().getJavaCoreHelper();
        IJavaProject[] allImportedProjects = javaCoreHelper.getAllBazelJavaProjects(false);
        Set<IProject> downstreams = EclipseProjectUtils.getDownstreamProjectsOf(project, allImportedProjects);

        // flush caches
        BazelWorkspace bzlWs = ComponentContext.getInstance().getBazelWorkspace();
        BazelCommandManager bzlCmdMgr = ComponentContext.getInstance().getBazelCommandManager();
        BazelWorkspaceCommandRunner bzlWsCmdRunner = bzlCmdMgr.getWorkspaceCommandRunner(bzlWs);
        ComponentContext.getInstance().getProjectManager().flushCaches(project.getName(), bzlWsCmdRunner);
        for (IProject downstreamProject : downstreams) {
            ComponentContext.getInstance().getProjectManager().flushCaches(downstreamProject.getName(), bzlWsCmdRunner);
        }
    }

    private static void undo(IProject project) {
        if (ComponentContext.getInstance().getResourceHelper().getEclipseWorkspace().isTreeLocked()) {
            return;
        }

        try {
            project.delete(true, null);
        } catch (CoreException e) {
            LOG.error("error during undo", e);
        }
    }

    private static IClasspathContainer getClasspathContainer(IProject project, boolean isRootProject)
            throws JavaModelException, IOException, InterruptedException, BackingStoreException,
            BazelCommandLineToolConfigurationException {

        IClasspathContainer cp = null;
        if (isRootProject) {
            BazelGlobalSearchClasspathContainer searchIndex = new BazelGlobalSearchClasspathContainer(project);
            ComponentContext.getInstance().setGlobalSearchClasspathContainer(searchIndex);
            cp = searchIndex;
        } else {
            cp = new BazelClasspathContainer(project);
        }
        return cp;
    }

    private static void setClasspathContainerForProject(IPath projectPath, IJavaProject project,
            IClasspathContainer container) throws JavaModelException {
        setClasspathContainerForProject(projectPath, project, container, null);
    }

    private static void setClasspathContainerForProject(IPath projectPath, IJavaProject project,
            IClasspathContainer container, IProgressMonitor monitor) throws JavaModelException {
        JavaCoreHelper ch = ComponentContext.getInstance().getJavaCoreHelper();
        ch.setClasspathContainer(projectPath, new IJavaProject[] { project }, new IClasspathContainer[] { container },
            monitor);
    }
}
