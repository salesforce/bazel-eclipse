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
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.ui.actions.WorkspaceModifyOperation;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.abstractions.WorkProgressMonitor;
import com.salesforce.bazel.eclipse.classpath.BazelClasspathContainer;
import com.salesforce.bazel.eclipse.command.BazelCommandManager;
import com.salesforce.bazel.eclipse.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.eclipse.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.eclipse.config.BazelEclipseProjectFactory;
import com.salesforce.bazel.eclipse.config.BazelEclipseProjectSupport;
import com.salesforce.bazel.eclipse.logging.LogHelper;
import com.salesforce.bazel.eclipse.model.BazelLabel;
import com.salesforce.bazel.eclipse.model.BazelMarkerDetails;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseWorkProgressMonitor;

/**
 * Project builder that calls out to Bazel to run a workspace build.
 * <p>
 * Registered in plugin.xml to the builders extension point. <br/>
 * id="com.salesforce.bazel.eclipse.builder"<br/>
 * point="org.eclipse.core.resources.builders"
 */
public class BazelBuilder extends IncrementalProjectBuilder {
    static final LogHelper LOG = LogHelper.log(BazelBuilder.class);

    public static final String BUILDER_NAME = "com.salesforce.bazel.eclipse.builder";

    private final BazelMarkerManagerSingleton markerManager = BazelMarkerManagerSingleton.getInstance();

    @Override
    protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
        WorkProgressMonitor progressMonitor = new EclipseWorkProgressMonitor(monitor);
        IProject project = getProject();
        progressMonitor.beginTask("Bazel build", 1);
        
        BazelCommandManager bazelCommandManager = BazelPluginActivator.getBazelCommandManager();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = bazelCommandManager.getWorkspaceCommandRunner(BazelPluginActivator.getBazelWorkspaceRootDirectory());
        
        try {
            boolean buildSuccessful = buildProjects(bazelWorkspaceCmdRunner, Collections.singletonList(project), progressMonitor, monitor);
            if (buildSuccessful && !importInProgress()) {
                Set<IProject> downstreams = getDownstreamProjectsOf(project);
                buildProjects(bazelWorkspaceCmdRunner, downstreams, progressMonitor, monitor);
            }
        } catch (IOException | InterruptedException e) {
            LOG.error("Failed to build {}", e, project.getName());
        } catch (BazelCommandLineToolConfigurationException e) {
            LOG.error("Bazel not found: {} ", e.getMessage());
        } finally {
            progressMonitor.done();
        }
        return null;
    }

    @Override
    protected void clean(IProgressMonitor monitor) throws CoreException {
        // When cleaning the entire workspace, this clean method runs multiple times for every bazel package
        // this may not have a severe performance impact as bazel handles it efficiently but we may want to revisit
        // TODO: revisit if we want to clean only once when multiple targets are selected
        
        BazelCommandManager bazelCommandManager = BazelPluginActivator.getBazelCommandManager();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = bazelCommandManager.getWorkspaceCommandRunner(BazelPluginActivator.getBazelWorkspaceRootDirectory());
        
        if (bazelWorkspaceCmdRunner == null) {
            super.clean(monitor);
        } else {
            bazelWorkspaceCmdRunner.flushAspectInfoCache();
            bazelWorkspaceCmdRunner.runBazelClean(null);
        }

        BazelClasspathContainer.clean();
    }
    
    private boolean buildProjects(BazelWorkspaceCommandRunner cmdRunner, Collection<IProject> projects, WorkProgressMonitor progressMonitor, IProgressMonitor monitor)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException 
    {
        List<String> bazelTargets = Lists.newArrayList();
        Multimap<IProject, BazelLabel> projectToLabels = HashMultimap.create();
        
        // figure out the list of targets to build and map projects to targets
        for (IProject project : projects) {
            List<String> targets = BazelEclipseProjectSupport.getBazelTargetsForEclipseProject(project, false);
            bazelTargets.addAll(targets);
            List<BazelLabel> labels = targets.stream().map(t -> new BazelLabel(t)).collect(Collectors.toList());
            projectToLabels.putAll(project, labels);
        }
        
        if (bazelTargets.isEmpty()) {
            return true;
        } else {
            List<String> bazelBuildFlags = getAllBazelBuildFlags(projects);
        
            // run build
            List<BazelMarkerDetails> errors = cmdRunner.runBazelBuild(bazelTargets, progressMonitor, bazelBuildFlags);
            Multimap<IProject, BazelMarkerDetails> errorsByProject = paritionErrorsByProject(errors, projectToLabels);
            for (IProject project : projects) {
                publishProblemMarkers(project, monitor, errorsByProject.get(project), projectToLabels.get(project));
            }
            return errors.isEmpty();
        }
    }
    
    private Multimap<IProject, BazelMarkerDetails> paritionErrorsByProject(List<BazelMarkerDetails> errors, Multimap<IProject, BazelLabel> projectToLabels) {
        Multimap<IProject, BazelMarkerDetails> m = HashMultimap.create();
        for (BazelMarkerDetails error : errors) {
            for (IProject project : projectToLabels.keys()) {
                String resourcePath = error.getResourcePathRelativeToBazelPackage(projectToLabels.get(project));
                if (resourcePath != null) {
                    m.put(project, error);
                }
            }
        }
        return m;
    }
    
    private static List<String> getAllBazelBuildFlags(Collection<IProject> projects) {
        List<String> buildFlags = Lists.newArrayList();
        for (IProject project : projects) {
            buildFlags.addAll(BazelEclipseProjectSupport.getBazelBuildFlagsForEclipseProject(project));
        }
        return buildFlags;         
    }

    private static Set<IProject> getDownstreamProjectsOf(IProject upstream) {
        Set<IProject> downstreams = new HashSet<>();
        for (IJavaProject project : getAllJavaProjects()) {
            try {
                for (String requiredProjectName : project.getRequiredProjectNames()) {
                    if (upstream.getProject().getName().equals(requiredProjectName)) {
                        downstreams.add(project.getProject());
                    }
                }
            } catch (JavaModelException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return downstreams;
    }

    private static IJavaProject[] getAllJavaProjects() {
        IWorkspaceRoot workspaceRoot = BazelPluginActivator.getResourceHelper().getEclipseWorkspaceRoot();
        try {
            return BazelPluginActivator.getJavaCoreHelper().getJavaModelForWorkspace(workspaceRoot).getJavaProjects();
        } catch (JavaModelException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void publishProblemMarkers(IProject project, IProgressMonitor monitor, Collection<BazelMarkerDetails> errors, Collection<BazelLabel> labels) {
        run(monitor, new WorkspaceModifyOperation() {
            @Override
            protected void execute(IProgressMonitor monitor) throws CoreException {
                markerManager.clearProblemMarkersForProject(project);
                markerManager.publishProblemMarkersForProject(project, errors, labels);
            }
        });
    }

    private static void run(IProgressMonitor monitor, IRunnableWithProgress runnable) {
        try {
            runnable.run(monitor);
        } catch (InvocationTargetException | InterruptedException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private boolean importInProgress() {
        // what's the right way to do this?
        return BazelEclipseProjectFactory.importInProgress.get();
    }
}