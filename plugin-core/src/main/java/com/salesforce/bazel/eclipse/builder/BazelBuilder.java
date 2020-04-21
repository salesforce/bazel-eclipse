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

import static com.google.common.collect.MoreCollectors.onlyElement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.abstractions.WorkProgressMonitor;
import com.salesforce.bazel.eclipse.classpath.BazelClasspathContainer;
import com.salesforce.bazel.eclipse.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.eclipse.command.BazelCommandManager;
import com.salesforce.bazel.eclipse.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.eclipse.config.BazelEclipseProjectFactory;
import com.salesforce.bazel.eclipse.config.BazelEclipseProjectSupport;
import com.salesforce.bazel.eclipse.config.BazelProjectPreferences;
import com.salesforce.bazel.eclipse.config.EclipseProjectBazelTargets;
import com.salesforce.bazel.eclipse.logging.LogHelper;
import com.salesforce.bazel.eclipse.model.BazelLabel;
import com.salesforce.bazel.eclipse.model.BazelMarkerDetails;
import com.salesforce.bazel.eclipse.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseWorkProgressMonitor;

/**
 * Project builder that calls out to Bazel to run a workspace build.
 * <p>
 * Registered in plugin.xml to the builders extension point. <br/>
 * id="com.salesforce.bazel.eclipse.builder"<br/>
 * point="org.eclipse.core.resources.builders"
 */
public class BazelBuilder extends IncrementalProjectBuilder {

    private static final LogHelper LOG = LogHelper.log(BazelBuilder.class);

    public static final String BUILDER_NAME = "com.salesforce.bazel.eclipse.builder";

    @Override
    protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
        WorkProgressMonitor progressMonitor = new EclipseWorkProgressMonitor(monitor);
        IProject project = getProject();
        progressMonitor.beginTask("Bazel build", 1);

        BazelCommandManager bazelCommandManager = BazelPluginActivator.getBazelCommandManager();
        JavaCoreHelper javaCoreHelper = BazelPluginActivator.getJavaCoreHelper();
        BazelWorkspace bazelWorkspace = BazelPluginActivator.getBazelWorkspace();
        ResourceHelper resourceHelper = BazelPluginActivator.getResourceHelper();
        if (bazelWorkspace == null) {
            return new IProject[] {};
        }
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);
        if (bazelWorkspaceCmdRunner == null) {
            return new IProject[] {};
        }

        try {
            boolean buildSuccessful = buildProjects(bazelWorkspaceCmdRunner, Collections.singletonList(project), progressMonitor, Optional.empty(), monitor);
            if (buildSuccessful && !importInProgress()) {
                IJavaProject[] allImportedProjects = javaCoreHelper.getAllBazelJavaProjects(true);
                IProject rootWorkspaceProject = Arrays.stream(allImportedProjects)
                        .filter(p -> resourceHelper.isBazelRootProject(p.getProject()))
                        .collect(onlyElement()).getProject();
                Set<IProject> downstreamProjects = getDownstreamProjectsOf(project, allImportedProjects);
                buildProjects(bazelWorkspaceCmdRunner, downstreamProjects, progressMonitor, Optional.of(rootWorkspaceProject), monitor);
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
        BazelWorkspace bazelWorkspace = BazelPluginActivator.getBazelWorkspace();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);

        if (bazelWorkspaceCmdRunner == null) {
            super.clean(monitor);
        } else {
            bazelWorkspaceCmdRunner.flushAspectInfoCache();
            bazelWorkspaceCmdRunner.runBazelClean(null);
        }

        BazelClasspathContainer.clean();
    }

    private boolean buildProjects(BazelWorkspaceCommandRunner cmdRunner, Collection<IProject> projects, WorkProgressMonitor progressMonitor, Optional<IProject> rootProject, IProgressMonitor monitor)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException
    {
        Set<String> bazelTargets = new TreeSet<>();
        Map<BazelLabel, IProject> labelToProject = new HashMap<>();

        // figure out the list of targets to build and map targets to projects
        for (IProject project : projects) {
            EclipseProjectBazelTargets activatedTargets = BazelProjectPreferences.getConfiguredBazelTargets(project, false);
            bazelTargets.addAll(activatedTargets.getConfiguredTargets());
            List<BazelLabel> labels = activatedTargets.getConfiguredTargets().stream().map(t -> new BazelLabel(t)).collect(Collectors.toList());
            for (BazelLabel label : labels) {
                labelToProject.merge(label, project, (k1, k2) -> {
                    throw new IllegalStateException("Duplicate label: " + label + " - this is bug");
                });
            }
        }

        if (bazelTargets.isEmpty()) {
            return true;
        } else {
            List<String> bazelBuildFlags = getAllBazelBuildFlags(projects);
            // now run the actual build
            List<BazelMarkerDetails> errors = cmdRunner.runBazelBuild(bazelTargets, progressMonitor, bazelBuildFlags);
            // show build errors in the "Problems View" - this has to run even
            // if there are no errors because it also takes care of removing
            // previous errors
            handleBuildErrors(projects, errors, labelToProject, rootProject, monitor);
            return errors.isEmpty();
        }
    }

    // assigns errors to owning projects, and published the errors in the "Problems View"
    private static void handleBuildErrors(Collection<IProject> projects, List<BazelMarkerDetails> errors, Map<BazelLabel, IProject> labelToProject, Optional<IProject> rootProject, IProgressMonitor monitor) {
        Multimap<IProject, BazelMarkerDetails> errorsByProject = assignErrorsToOwningProject(errors, labelToProject, rootProject);
        if (rootProject.isPresent()) {
            projects = new ArrayList<>(projects);
            projects.add(rootProject.get());
        }
        publishErrors(projects, errorsByProject, monitor);
    }

    // this needs to be called even when there are no errors, as it clears the "Problems View"
    private static void publishErrors(Collection<IProject> projects, Multimap<IProject, BazelMarkerDetails> errorsByProject, IProgressMonitor monitor) {
        for (IProject project : projects) {
            BazelEclipseProjectSupport.publishProblemMarkers(project, monitor, errorsByProject.get(project));
        }
    }

    static final String UNKNOWN_PROJECT_ERROR_MSG_PREFIX = "ERROR IN UNKNOWN PROJECT: ";

    // maps the specified errors to the project instances they belong to, and returns that mapping
    static Multimap<IProject, BazelMarkerDetails> assignErrorsToOwningProject(List<BazelMarkerDetails> errors, Map<BazelLabel, IProject> labelToProject, Optional<IProject> rootProject) {
        Multimap<IProject, BazelMarkerDetails> projectToErrors = HashMultimap.create();
        List<BazelMarkerDetails> remainingErrors = new ArrayList<>(errors);
        for (BazelMarkerDetails error : errors) {
            BazelLabel owningLabel = error.getOwningLabel(labelToProject.keySet());
            if (owningLabel != null) {
                IProject project = labelToProject.get(owningLabel);
                projectToErrors.put(project, error.toErrorWithRelativizedResourcePath(owningLabel));
                remainingErrors.remove(error);
            }
        }
        if (!remainingErrors.isEmpty()) {
            rootProject.ifPresentOrElse(p -> {
                projectToErrors.putAll(p, remainingErrors.stream()
                        .map(e -> e.toGenericWorkspaceLevelError(UNKNOWN_PROJECT_ERROR_MSG_PREFIX))
                        .collect(Collectors.toList()));
            }, () -> {
                // getting here is a bug - at least log the errors we didn't assign to any project
                for (BazelMarkerDetails error : remainingErrors) {
                    LOG.error("Unhandled error: " + error);
                }
            });
        }
        return projectToErrors;
    }

    private static List<String> getAllBazelBuildFlags(Collection<IProject> projects) {
        List<String> buildFlags = Lists.newArrayList();
        for (IProject project : projects) {
            buildFlags.addAll(BazelProjectPreferences.getBazelBuildFlagsForEclipseProject(project));
        }
        return buildFlags;
    }

    static Set<IProject> getDownstreamProjectsOf(IProject project, IJavaProject[] allImportedProjects) {
        Set<IProject> downstreamProjects = new LinkedHashSet<>(); // cannot be a TreeSet because Project doesn't implement Comparable
        collectDownstreamProjects(project, downstreamProjects, allImportedProjects);
        return downstreamProjects;
    }

    // determines all downstream projects, including transitives, of the specified "upstream" project, by looking at the
    // specified "allImportedProjects", and adds them to the specified "downstreams" Set.
    private static void collectDownstreamProjects(IProject upstream, Set<IProject> downstreams, IJavaProject[] allImportedProjects) {
        for (IJavaProject project : allImportedProjects) {
            try {
                for (String requiredProjectName : project.getRequiredProjectNames()) {
                    String upstreamProjectName = upstream.getName();
                    if (upstreamProjectName.equals(requiredProjectName)) {
                        IProject downstream = project.getProject();
                        if (!downstreams.contains(downstream)) {
                            downstreams.add(downstream);
                            collectDownstreamProjects(downstream, downstreams, allImportedProjects);
                        }
                    }
                }
            } catch (JavaModelException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private boolean importInProgress() {
        // what's the right way to do this?
        return BazelEclipseProjectFactory.importInProgress.get();
    }
}
