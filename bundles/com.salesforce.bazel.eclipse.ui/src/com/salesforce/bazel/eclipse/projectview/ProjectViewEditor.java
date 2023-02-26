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
package com.salesforce.bazel.eclipse.projectview;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.source.DefaultAnnotationHover;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.ui.editors.text.TextFileDocumentProvider;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditor;

import com.salesforce.bazel.eclipse.builder.BazelProblemMarkerManager;
import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.component.EclipseBazelWorkspaceContext;
import com.salesforce.bazel.eclipse.project.BazelPackageContentAssistProcessor;
import com.salesforce.bazel.eclipse.projectimport.ProjectImporter;
import com.salesforce.bazel.eclipse.projectimport.ProjectImporterFactory;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelProblem;
import com.salesforce.bazel.sdk.project.BazelProjectOld;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.ProjectView;
import com.salesforce.bazel.sdk.project.ProjectViewConstants;
import com.salesforce.bazel.sdk.project.ProjectViewPackageLocation;

public class ProjectViewEditor extends AbstractDecoratedTextEditor {

    private static final String CONFIRMATION_TEXT =
            "Update the Bazel Project(s) in your Workspace to match the content of this file?";

    private static final String PROJECT_VIEW_RESOURCE = File.separator + ProjectViewConstants.PROJECT_VIEW_FILE_NAME;

    private static IJavaProject[] getAllJavaBazelProjects() {
        return ComponentContext.getInstance().getJavaCoreHelper().getAllBazelJavaProjects(false);
    }

    private static IProject getBazelRootProject() {
        for (IJavaProject project : ComponentContext.getInstance().getJavaCoreHelper().getAllBazelJavaProjects(true)) {
            if (ComponentContext.getInstance().getResourceHelper().isBazelRootProject(project.getProject())) {
                return project.getProject();
            }
        }
        throw new IllegalStateException("Root project not found");
    }

    private final IProject rootProject;
    private final File rootDirectory;
    private final ProjectViewPackageLocation rootPackage;
    private final BazelProblemMarkerManager markerManager;

    private final BazelProjectManager projectManager;

    private final ResourceHelper resourceHelper;

    public ProjectViewEditor() {
        rootProject = getBazelRootProject();
        rootDirectory = EclipseBazelWorkspaceContext.getInstance().getBazelWorkspace().getBazelWorkspaceRootDirectory();
        rootPackage = new ProjectViewPackageLocation(rootDirectory, "");
        markerManager = new BazelProblemMarkerManager(getClass().getName());
        projectManager = ComponentContext.getInstance().getProjectManager();
        resourceHelper = ComponentContext.getInstance().getResourceHelper();
        setDocumentProvider(new TextFileDocumentProvider());
        super.setSourceViewerConfiguration(new SourceViewerConfiguration() {
            @Override
            public IAnnotationHover getAnnotationHover(ISourceViewer sourceViewer) {
                return new DefaultAnnotationHover();
            }

            @Override
            public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
                var ca = new ContentAssistant();
                IContentAssistProcessor cap = new BazelPackageContentAssistProcessor();
                ca.setContentAssistProcessor(cap, IDocument.DEFAULT_CONTENT_TYPE);
                ca.setInformationControlCreator(getInformationControlCreator(sourceViewer));
                return ca;
            }
        });
    }

    private void deleteProjects(IJavaProject[] projects, IProgressMonitor monitor) throws CoreException {
        // already runs in workspace modify
        for (IJavaProject project : projects) {
            resourceHelper.deleteProject(project.getProject(), monitor);
        }
    }

    @Override
    protected void editorSaved() {
        super.editorSaved();
        var projectViewContent = getSourceViewer().getTextWidget().getText();
        List<BazelPackageLocation> invalidPackages = new ArrayList<>();
        var proposedProjectView = ProjectViewProcessor
                .resolvePackages(new ProjectView(rootDirectory, projectViewContent), invalidPackages);
        List<BazelProblem> problems = new ArrayList<>();
        for (BazelPackageLocation invalidPackage : invalidPackages) {
            var lineNumber = proposedProjectView.getLineNumber(invalidPackage);
            problems.add(BazelProblem.createError(PROJECT_VIEW_RESOURCE, lineNumber,
                "Bad Bazel Package: " + invalidPackage.getBazelPackageFSRelativePath()));
        }
        var updateProblems = Job.createSystem("Updating problem markers", (var monitor) -> {
            markerManager.clearAndPublish(problems, rootProject, monitor);
        });
        updateProblems.setPriority(Job.SHORT);
        updateProblems.schedule();
        if (problems.isEmpty()) {
            // build the current project state and compare it to the proposed state
            var importedProjects = getAllJavaBazelProjects();
            var configuredTargets = getConfiguredTargetsFor(importedProjects);
            var importedPackages = getUniquePackages(configuredTargets);
            var projectView = new ProjectView(rootDirectory, importedPackages, configuredTargets);
            proposedProjectView.addDefaultTargets();
            if (!proposedProjectView.equals(projectView)) {
                var ok =
                        MessageDialog.openConfirm(getSite().getShell(), "Update Imported Projects", CONFIRMATION_TEXT);
                if (ok) {
                    WorkspaceJob updateProjects = new WorkspaceJob("Updating problem markers") {
                        @Override
                        public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
                            try {
                                var subMonitor = SubMonitor.convert(monitor, 100);
                                deleteProjects(importedProjects, subMonitor.newChild(20));

                                var projectsToImport = proposedProjectView.getDirectories();
                                var importerFactory =
                                        new ProjectImporterFactory(rootPackage, projectsToImport);
                                var projectImporter = importerFactory.build();
                                projectImporter.run(subMonitor.newChild(80));
                            } finally {
                                if (monitor != null) {
                                    monitor.done();
                                }
                            }

                            return Status.OK_STATUS;
                        }
                    };
                    updateProjects.setPriority(Job.LONG);
                    updateProjects.schedule();
                }
            }
        }
    }

    private List<BazelLabel> getConfiguredTargetsFor(IJavaProject[] projects) {
        List<BazelLabel> allTargets = new ArrayList<>();
        for (IJavaProject project : projects) {
            var projectName = project.getProject().getName();
            var bazelProject = projectManager.getProject(projectName);
            var targets = projectManager.getConfiguredBazelTargets(bazelProject, false).getConfiguredTargets();
            if ((targets == null) || targets.isEmpty()) {
                targets = Collections.emptySet();
            }
            for (String t : targets) {
                allTargets.add(new BazelLabel(t));
            }
        }
        return allTargets;
    }

    private List<BazelPackageLocation> getUniquePackages(List<BazelLabel> labels) {
        Set<String> handledPackages = new HashSet<>();
        List<BazelPackageLocation> packageLocations = new ArrayList<>();
        for (BazelLabel label : labels) {
            var packagePath = label.getPackagePath();
            if (!handledPackages.contains(packagePath)) {
                packageLocations.add(new ProjectViewPackageLocation(rootDirectory, packagePath));
            }
        }
        return packageLocations;
    }
}
