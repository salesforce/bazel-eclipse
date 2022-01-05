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
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import com.salesforce.bazel.eclipse.classpath.BazelClasspathContainer;
import com.salesforce.bazel.eclipse.classpath.BazelGlobalSearchClasspathContainer;
import com.salesforce.bazel.eclipse.classpath.IClasspathContainerConstants;
import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.component.EclipseBazelWorkspaceContext;
import com.salesforce.bazel.eclipse.launch.BazelTestClasspathProvider;
import com.salesforce.bazel.eclipse.project.EclipseProjectUtils;
import com.salesforce.bazel.eclipse.projectimport.ProjectImporterFactory;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseWorkProgressMonitor;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelProblem;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.BazelProjectTargets;
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

    private static final AtomicBoolean REGISTERED_EL_CHANGE_LISTENER = new AtomicBoolean(false);
    private static final LogHelper LOG = LogHelper.log(BazelBuilder.class);

    // we only need one instance of this one
    private static JDTWarningPublisher warningPublisher = new JDTWarningPublisher();

    public BazelBuilder() {
        if (!REGISTERED_EL_CHANGE_LISTENER.getAndSet(true)) {
            // is there a better way or place to register a singleton?
            JavaCore.addElementChangedListener(warningPublisher);
        }
    }

    @Override
    protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
        if (importInProgress()) {
            // during import we run bazel build through a different code path
            return null;
        }
        WorkProgressMonitor progressMonitor = new EclipseWorkProgressMonitor(monitor);
        IProject project = getProject();
        progressMonitor.beginTask("Bazel build", 1);

        BazelCommandManager bazelCommandManager = ComponentContext.getInstance().getBazelCommandManager();
        JavaCoreHelper javaCoreHelper = ComponentContext.getInstance().getJavaCoreHelper();
        BazelWorkspace bazelWorkspace = EclipseBazelWorkspaceContext.getInstance().getBazelWorkspace();
        ResourceHelper resourceHelper = ComponentContext.getInstance().getResourceHelper();
        if (bazelWorkspace == null) {
            LOG.warn("Ignoring a Bazel build request, as the Bazel Workspace is not set.");
            BazelProblem problem = BazelProblem.createError("BUILD", 1,
                "The Bazel Workspace project has been deleted from the Eclipse Workspace.");
            List<BazelProblem> problems = Arrays.asList(problem);
            BazelProblemMarkerManager markerManager = new BazelProblemMarkerManager("BazelBuilder");
            markerManager.publish(problems, project, monitor);
            return new IProject[] {};
        }
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner =
                bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);
        if (bazelWorkspaceCmdRunner == null) {
            return new IProject[] {};
        }

        try {
            boolean buildSuccessful = buildProjects(bazelWorkspaceCmdRunner, Collections.singletonList(project),
                progressMonitor, null, monitor);
            if (buildSuccessful) {
                IJavaProject[] allImportedProjects = javaCoreHelper.getAllBazelJavaProjects(true);
                IProject rootWorkspaceProject = Arrays.stream(allImportedProjects)
                        .filter(p -> resourceHelper.isBazelRootProject(p.getProject())).findFirst().get().getProject();
                Set<IProject> downstreamProjects =
                        EclipseProjectUtils.getDownstreamProjectsOf(project, allImportedProjects);
                buildProjects(bazelWorkspaceCmdRunner, downstreamProjects, progressMonitor, rootWorkspaceProject,
                    monitor);

                maybeUpdateClasspathContainer(project, javaCoreHelper);
            }
        } catch (BazelCommandLineToolConfigurationException e) {
            LOG.error("Bazel not found: {} ", e.getMessage());
        } catch (Exception e) {
            LOG.error("Failed to build: " + e.getMessage(), e);
        } finally {
            progressMonitor.done();
        }
        return null;
    }

    void maybeUpdateClasspathContainer(IProject project, JavaCoreHelper javaCoreHelper) throws CoreException {
        IResourceDelta delta = getDelta(project);
        if (delta == null) {
            // arguably we should refresh the classpath container by default in this case (?)
        } else if (ResourceDeltaInspector.deltaHasChangedBuildFiles(delta)) {
            // we request a classpath container update only if detect a BUILD file change
            // this should also consider added or removed BUILD files (?)
            IJavaProject javaProject = javaCoreHelper.getJavaProjectForProject(project);
            ClasspathContainerInitializer cpInit =
                    JavaCore.getClasspathContainerInitializer(IClasspathContainerConstants.CONTAINER_NAME);
            cpInit.requestClasspathContainerUpdate(Path.fromPortableString(IClasspathContainerConstants.CONTAINER_NAME),
                javaProject, null);
        }
    }

    @Override
    protected void clean(IProgressMonitor monitor) throws CoreException {
        // When cleaning the entire workspace, this clean method runs multiple times for every bazel package
        // this may not have a severe performance impact as bazel handles it efficiently but we may want to revisit
        // TODO: revisit if we want to clean only once when multiple targets are selected

        BazelCommandManager bazelCommandManager = ComponentContext.getInstance().getBazelCommandManager();
        BazelWorkspace bazelWorkspace = EclipseBazelWorkspaceContext.getInstance().getBazelWorkspace();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner =
                bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);

        if (bazelWorkspaceCmdRunner == null) {
            super.clean(monitor);
        } else {
            bazelWorkspaceCmdRunner.flushAspectInfoCache();

            // TODO make a pref to enable a bazel clean, but in almost any circumstance 'bazel clean' is not correct
            // https://github.com/salesforce/bazel-eclipse/issues/185 // $SLASH_OK url
            // bazelWorkspaceCmdRunner.runBazelClean(null);
        }

        // TODO implement a clean() listener and let impls register with us, instead of hacking like this
        BazelClasspathContainer.clean();
        BazelGlobalSearchClasspathContainer.clean();
        BazelTestClasspathProvider.clean();
    }

    private boolean buildProjects(BazelWorkspaceCommandRunner cmdRunner, Collection<IProject> projects,
            WorkProgressMonitor progressMonitor, IProject rootProject, IProgressMonitor monitor)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        Set<String> bazelTargets = new TreeSet<>();
        BazelProjectManager bazelProjectManager = ComponentContext.getInstance().getProjectManager();
        List<BazelProject> bazelProjects = new ArrayList<>();

        // figure out the list of targets to build
        for (IProject project : projects) {
            String projectName = project.getName();
            BazelProject bazelProject = bazelProjectManager.getProject(projectName);
            BazelProjectTargets activatedTargets = bazelProjectManager.getConfiguredBazelTargets(bazelProject, false);
            bazelTargets.addAll(activatedTargets.getConfiguredTargets());
            bazelProjects.add(bazelProject);
        }

        if (bazelTargets.isEmpty()) {
            return true;
        }
        List<String> bazelBuildFlags = getAllBazelBuildFlags(projects);
        List<BazelProblem> errors = cmdRunner.runBazelBuild(bazelTargets, bazelBuildFlags, progressMonitor);
        // publish errors (even if no errors, this must run so that previous errors are cleared)
        Map<BazelLabel, BazelProject> labelToProject = bazelProjectManager.getBazelLabelToProjectMap(bazelProjects);
        BazelErrorPublisher errorPublisher = new BazelErrorPublisher(rootProject, projects, labelToProject);
        errorPublisher.publish(errors, monitor);
        // also publish warnings
        warningPublisher.publish(projects, monitor);
        return errors.isEmpty();
    }

    private static List<String> getAllBazelBuildFlags(Collection<IProject> projects) {
        List<String> buildFlags = new ArrayList<>();
        BazelProjectManager bazelProjectManager = ComponentContext.getInstance().getProjectManager();

        for (IProject project : projects) {
            String projectName = project.getName();
            BazelProject bazelProject = bazelProjectManager.getProject(projectName);
            buildFlags.addAll(bazelProjectManager.getBazelBuildFlagsForProject(bazelProject));
        }
        return buildFlags;
    }

    private boolean importInProgress() {
        // what's the right way to do this?
        return ProjectImporterFactory.importInProgress.get();
    }
}
