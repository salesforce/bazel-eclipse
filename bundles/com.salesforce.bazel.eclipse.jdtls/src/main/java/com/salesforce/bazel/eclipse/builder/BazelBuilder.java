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
package com.salesforce.bazel.eclipse.builder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.JavaCore;
import org.slf4j.Logger;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.component.EclipseBazelWorkspaceContext;
import com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants;
import com.salesforce.bazel.eclipse.project.EclipseProjectUtils;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.project.BazelProjectOld;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;

/**
 * Project builder that calls out to Bazel to run a workspace build.
 * <p>
 * Registered in plugin.xml to the builders extension point. <br/>
 * $SLASH_OK xml id="com.salesforce.bazel.eclipse.builder"<br/>
 * $SLASH_OK xml point="org.eclipse.core.resources.builders"
 */
public class BazelBuilder extends IncrementalProjectBuilder {

    public static final String BUILDER_NAME = "com.salesforce.bazel.eclipse.builder";

    private static Logger LOG = org.slf4j.LoggerFactory.getLogger(BazelBuilder.class);

    private static List<String> getAllBazelBuildFlags(Collection<IProject> projects) {
        List<String> buildFlags = new ArrayList<>();
        var bazelProjectManager = ComponentContext.getInstance().getProjectManager();

        for (IProject project : projects) {
            var projectName = project.getName();
            var bazelProject = bazelProjectManager.getProject(projectName);
            buildFlags.addAll(bazelProjectManager.getBazelBuildFlagsForProject(bazelProject));
        }
        return buildFlags;
    }

    @Override
    protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
        var progressMonitor = WorkProgressMonitor.NOOP;
        var project = getProject();
        progressMonitor.beginTask("Bazel build", 1);

        var bazelCommandManager = ComponentContext.getInstance().getBazelCommandManager();
        var javaCoreHelper = ComponentContext.getInstance().getJavaCoreHelper();
        var bazelWorkspace = ComponentContext.getInstance().getBazelWorkspace();
        var resourceHelper = ComponentContext.getInstance().getResourceHelper();
        if (bazelWorkspace == null) {
            return new IProject[] {};
        }
        var bazelWorkspaceCmdRunner = bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);
        if (bazelWorkspaceCmdRunner == null) {
            return new IProject[] {};
        }

        try {
            var buildSuccessful =
                    buildProjects(bazelWorkspaceCmdRunner, Collections.singletonList(project), progressMonitor);
            if (buildSuccessful) {
                var allImportedProjects = javaCoreHelper.getAllBazelJavaProjects(true);
                var rootWorkspaceProject = Arrays.stream(allImportedProjects)
                        .filter(p -> resourceHelper.isBazelRootProject(p.getProject())).findFirst().get().getProject();
                var downstreamProjects = EclipseProjectUtils.getDownstreamProjectsOf(project, allImportedProjects);
                buildProjects(bazelWorkspaceCmdRunner, downstreamProjects, progressMonitor);

                maybeUpdateClasspathContainer(project, javaCoreHelper);
            }
        } catch (BazelCommandLineToolConfigurationException e) {
            LOG.error("Bazel executable not found in {}", e);
        } catch (Exception e) {
            LOG.error("Failed to build", e);
        } finally {
            progressMonitor.done();
        }
        return null;
    }

    private boolean buildProjects(BazelWorkspaceCommandRunner cmdRunner, Collection<IProject> projects,
            WorkProgressMonitor progressMonitor)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        Set<String> bazelTargets = new TreeSet<>();
        var bazelProjectManager = ComponentContext.getInstance().getProjectManager();
        List<BazelProjectOld> bazelProjects = new ArrayList<>();

        // figure out the list of targets to build
        for (IProject project : projects) {
            var projectName = project.getName();
            var bazelProject = bazelProjectManager.getProject(projectName);
            var activatedTargets = bazelProjectManager.getConfiguredBazelTargets(bazelProject, false);
            bazelTargets.addAll(activatedTargets.getConfiguredTargets());
            bazelProjects.add(bazelProject);
        }

        LOG.debug("Bazel build targets {}", bazelTargets);

        if (bazelTargets.isEmpty()) {
            return true;
        }
        var bazelBuildFlags = getAllBazelBuildFlags(projects);
        var errors = cmdRunner.runBazelBuild(bazelTargets, bazelBuildFlags, progressMonitor);
        bazelProjectManager.getBazelLabelToProjectMap(bazelProjects);

        var successful = errors.isEmpty();
        if (!successful) {
            LOG.debug("Bazel build errors", errors);
        }
        return successful;
    }

    @Override
    protected void clean(IProgressMonitor monitor) throws CoreException {
        // When cleaning the entire workspace, this clean method runs multiple times for every bazel package
        // this may not have a severe performance impact as bazel handles it efficiently but we may want to revisit
        // TODO: revisit if we want to clean only once when multiple targets are selected

        var bazelCommandManager = ComponentContext.getInstance().getBazelCommandManager();
        var bazelWorkspace = EclipseBazelWorkspaceContext.getInstance().getBazelWorkspace();
        var bazelWorkspaceCmdRunner = bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);

        if (bazelWorkspaceCmdRunner == null) {
            super.clean(monitor);
        } else {
            bazelWorkspaceCmdRunner.flushAspectInfoCache();
        }
    }

    void maybeUpdateClasspathContainer(IProject project, JavaCoreHelper javaCoreHelper) throws CoreException {
        var delta = getDelta(project);
        if (delta == null) {
            // arguably we should refresh the classpath container by default in this case (?)
        } else if (ResourceDeltaInspector.deltaHasChangedBuildFiles(delta)) {
            // we request a classpath container update only if detect a BUILD file change
            // this should also consider added or removed BUILD files (?)
            var javaProject = javaCoreHelper.getJavaProjectForProject(project);
            var cpInit = JavaCore.getClasspathContainerInitializer(BazelCoreSharedContstants.CLASSPATH_CONTAINER_ID);
            cpInit.requestClasspathContainerUpdate(Path.fromPortableString(BazelCoreSharedContstants.CLASSPATH_CONTAINER_ID),
                javaProject, null);
        }
    }
}
