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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import com.google.common.collect.Lists;
import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.classpath.BazelClasspathContainer;
import com.salesforce.bazel.eclipse.config.BazelEclipseProjectFactory;
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
 * id="com.salesforce.bazel.eclipse.builder"<br/>
 * point="org.eclipse.core.resources.builders"
 */
public class BazelBuilder extends IncrementalProjectBuilder {

    public static final String BUILDER_NAME = "com.salesforce.bazel.eclipse.builder";

    private static final AtomicBoolean REGISTERED_EL_CHANGE_LISTENER = new AtomicBoolean(false);
    private static final LogHelper LOG = LogHelper.log(BazelBuilder.class);

    // we only need one instance of this one
    private static JDTWarningPublisher warningPublisher = new JDTWarningPublisher();

    public BazelBuilder() {
        if (!REGISTERED_EL_CHANGE_LISTENER.getAndSet(true)) {
            // is there a better way/place to register a singleton?
            JavaCore.addElementChangedListener(warningPublisher);
        }
    }

    @Override
    protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
        WorkProgressMonitor progressMonitor = new EclipseWorkProgressMonitor(monitor);
        IProject project = getProject();
        progressMonitor.beginTask("Bazel build", 1);

        // TODO there is something seriously wrong with Eclipse's resource change mechanism
        // To be fixed in https://github.com/salesforce/bazel-eclipse/issues/145
        // We are getting FULL_BUILD commands even if files have not changed.
        // See also the commented out line below for refreshProjectClasspath
        //System.out.println(">> Eclipse signaled that project ["+project.getName()+"] is dirty with kind ["+kind+"]");

        BazelCommandManager bazelCommandManager = BazelPluginActivator.getBazelCommandManager();
        JavaCoreHelper javaCoreHelper = BazelPluginActivator.getJavaCoreHelper();
        BazelWorkspace bazelWorkspace = BazelPluginActivator.getBazelWorkspace();
        ResourceHelper resourceHelper = BazelPluginActivator.getResourceHelper();
        if (bazelWorkspace == null) {
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
            if (buildSuccessful && !importInProgress()) {
                IJavaProject[] allImportedProjects = javaCoreHelper.getAllBazelJavaProjects(true);
                IProject rootWorkspaceProject = Arrays.stream(allImportedProjects)
                        .filter(p -> resourceHelper.isBazelRootProject(p.getProject())).collect(onlyElement())
                        .getProject();
                Set<IProject> downstreamProjects = getDownstreamProjectsOf(project, allImportedProjects);
                buildProjects(bazelWorkspaceCmdRunner, downstreamProjects, progressMonitor, rootWorkspaceProject,
                    monitor);

                // TODO this is too slow, we need to fix this in https://github.com/salesforce/bazel-eclipse/issues/145
                // when you uncomment this, make sure to also un-ignore the related test in BazelBuilderTest
                //refreshProjectClasspath(project, progressMonitor, monitor, bazelWorkspaceCmdRunner);
            }
        } catch (BazelCommandLineToolConfigurationException e) {
            LOG.error("Bazel not found: {} ", e.getMessage());
        } catch (Exception e) {
            LOG.error("Failed to build: " + e.getMessage(), e);
            e.printStackTrace();
        } finally {
            progressMonitor.done();
        }
        return null;
    }

    void refreshProjectClasspath(IProject project, WorkProgressMonitor progressMonitor, IProgressMonitor monitor,
            BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner) throws Exception {
        String pname = project.getName();
        BazelProject bazelProject = BazelPluginActivator.getBazelProjectManager().getProject(pname);
        BazelProjectManager projMgr = BazelPluginActivator.getBazelProjectManager();
        String packageLabel = projMgr.getBazelLabelForProject(bazelProject);
        System.out.println("Refreshing the classpath for project [" + pname + "] for package [" + packageLabel + "]");

        // Force update of classpath container and the aspect cache
        BazelClasspathContainer.clean();

        // Clean the aspect cache, and reload
        Set<String> flushedTargets = bazelWorkspaceCmdRunner.flushAspectInfoCacheForPackage(packageLabel);
        bazelWorkspaceCmdRunner.getAspectPackageInfos(flushedTargets, progressMonitor, "refreshProjectClasspath");

        // Clean the query cache and reload
        bazelWorkspaceCmdRunner.flushQueryCache(packageLabel);
        bazelWorkspaceCmdRunner.queryBazelTargetsInBuildFile(progressMonitor, packageLabel);

        // refresh the project immediately to reload classpath
        project.refreshLocal(IResource.DEPTH_ONE, monitor);

        // If a BUILD file added a reference from this project to another project in the Eclipse workspace, it is likely
        // the project ref update failed because the resource tree was locked. Retry any queued project updates now.
        // This operation is a no-op if no deferred updates are necessary
        ResourceHelper resourceHelper = BazelPluginActivator.getResourceHelper();
        resourceHelper.applyDeferredProjectDescriptionUpdates();

        // Force refresh of GUI
        project.touch(monitor);
        //System.out.println("Done refreshing the classpath for project ["+pname+"] for package ["+packageLabel+"]");
    }

    @Override
    protected void clean(IProgressMonitor monitor) throws CoreException {
        // When cleaning the entire workspace, this clean method runs multiple times for every bazel package
        // this may not have a severe performance impact as bazel handles it efficiently but we may want to revisit
        // TODO: revisit if we want to clean only once when multiple targets are selected

        BazelCommandManager bazelCommandManager = BazelPluginActivator.getBazelCommandManager();
        BazelWorkspace bazelWorkspace = BazelPluginActivator.getBazelWorkspace();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner =
                bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);

        if (bazelWorkspaceCmdRunner == null) {
            super.clean(monitor);
        } else {
            bazelWorkspaceCmdRunner.flushAspectInfoCache();
            bazelWorkspaceCmdRunner.runBazelClean(null);
        }

        BazelClasspathContainer.clean();
    }

    private boolean buildProjects(BazelWorkspaceCommandRunner cmdRunner, Collection<IProject> projects,
            WorkProgressMonitor progressMonitor, IProject rootProject, IProgressMonitor monitor)
            throws IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        Set<String> bazelTargets = new TreeSet<>();
        BazelProjectManager bazelProjectManager = BazelPluginActivator.getBazelProjectManager();
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
        } else {
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
    }

    private static List<String> getAllBazelBuildFlags(Collection<IProject> projects) {
        List<String> buildFlags = Lists.newArrayList();
        BazelProjectManager bazelProjectManager = BazelPluginActivator.getBazelProjectManager();

        for (IProject project : projects) {
            String projectName = project.getName();
            BazelProject bazelProject = bazelProjectManager.getProject(projectName);
            buildFlags.addAll(bazelProjectManager.getBazelBuildFlagsForProject(bazelProject));
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
    private static void collectDownstreamProjects(IProject upstream, Set<IProject> downstreams,
            IJavaProject[] allImportedProjects) {
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
