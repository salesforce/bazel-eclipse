package com.salesforce.bazel.eclipse.projectview;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
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
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.editors.text.TextFileDocumentProvider;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditor;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.builder.BazelProblemMarkerManager;
import com.salesforce.bazel.eclipse.config.BazelEclipseProjectSupport;
import com.salesforce.bazel.eclipse.project.BazelPackageContentAssistProcessor;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.wizard.BazelProjectImporter;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelProblem;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.ProjectView;
import com.salesforce.bazel.sdk.project.ProjectViewConstants;
import com.salesforce.bazel.sdk.project.ProjectViewPackageLocation;

public class ProjectViewEditor extends AbstractDecoratedTextEditor {

    private static final String CONFIRMATION_TEXT =
            "Update the Bazel Project(s) in your Workspace to match the content of this file?";

    private static final LogHelper LOG = LogHelper.log(ProjectViewEditor.class);

    private static final String PROJECT_VIEW_RESOURCE = File.separator + ProjectViewConstants.PROJECT_VIEW_FILE_NAME;

    private final IProject rootProject;
    private final File rootDirectory;
    private final ProjectViewPackageLocation rootPackage;
    private final BazelProblemMarkerManager markerManager;
    private final BazelProjectManager projectManager;
    private final ResourceHelper resourceHelper;

    public ProjectViewEditor() {
        this.rootProject = getBazelRootProject();
        this.rootDirectory = BazelPluginActivator.getBazelWorkspace().getBazelWorkspaceRootDirectory();
        this.rootPackage = new ProjectViewPackageLocation(this.rootDirectory, "");
        this.markerManager = new BazelProblemMarkerManager(getClass().getName());
        this.projectManager = BazelPluginActivator.getBazelProjectManager();
        this.resourceHelper = BazelPluginActivator.getResourceHelper();
        setDocumentProvider(new TextFileDocumentProvider());
        super.setSourceViewerConfiguration(new SourceViewerConfiguration() {
            @Override
            public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
                ContentAssistant ca = new ContentAssistant();
                IContentAssistProcessor cap = new BazelPackageContentAssistProcessor();
                ca.setContentAssistProcessor(cap, IDocument.DEFAULT_CONTENT_TYPE);
                ca.setInformationControlCreator(getInformationControlCreator(sourceViewer));
                return ca;
            }

            @Override
            public IAnnotationHover getAnnotationHover(ISourceViewer sourceViewer) {
                return new DefaultAnnotationHover();
            }
        });
    }

    @Override
    protected void editorSaved() {
        super.editorSaved();
        String projectViewContent = getSourceViewer().getTextWidget().getText();
        ProjectView proposedProjectView = new ProjectView(this.rootDirectory, projectViewContent);
        List<BazelPackageLocation> invalidPackages = new ArrayList<>();
        proposedProjectView = ProjectViewProcessor.resolvePackages(proposedProjectView, invalidPackages);
        List<BazelProblem> problems = new ArrayList<>();
        for (BazelPackageLocation invalidPackage : invalidPackages) {
            int lineNumber = proposedProjectView.getLineNumber(invalidPackage);
            problems.add(BazelProblem.createError(PROJECT_VIEW_RESOURCE, lineNumber,
                "Bad Bazel Package: " + invalidPackage.getBazelPackageFSRelativePath()));
        }
        markerManager.clearAndPublish(problems, this.rootProject, getProgressMonitor());
        if (problems.isEmpty()) {
            // build the current project state and compare it to the proposed state
            IJavaProject[] importedProjects = getAllJavaBazelProjects();
            List<BazelLabel> configuredTargets = getConfiguredTargetsFor(importedProjects);
            List<BazelPackageLocation> importedPackages = getUniquePackages(configuredTargets);
            ProjectView projectView = new ProjectView(rootDirectory, importedPackages, configuredTargets);
            proposedProjectView.addDefaultTargets();
            if (proposedProjectView.equals(projectView)) {
                // no change, nothing to do
                LOG.info("The Bazel Packages in the " + ProjectViewConstants.PROJECT_VIEW_FILE_NAME
                        + " file match the set of Eclipse Projects currently imported");
            } else {
                boolean ok = MessageDialog.openConfirm(this.getSite().getShell(), "Update Imported Projects",
                    CONFIRMATION_TEXT);
                if (ok) {
                    deleteProjects(importedProjects);
                    List<BazelPackageLocation> projectsToImport = proposedProjectView.getDirectories();
                    BazelProjectImporter.run(this.rootPackage, projectsToImport);
                }
            }
        }
    }

    private List<BazelLabel> getConfiguredTargetsFor(IJavaProject[] projects) {
        List<BazelLabel> allTargets = new ArrayList<>();
        for (IJavaProject project : projects) {
            String projectName = project.getProject().getName();
            BazelProject bazelProject = projectManager.getProject(projectName);
            Set<String> targets = projectManager.getConfiguredBazelTargets(bazelProject, false).getConfiguredTargets();
            if (targets == null || targets.isEmpty()) {
                LOG.info("No configured targets for " + projectName);
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
            String packagePath = label.getPackagePath();
            if (!handledPackages.contains(packagePath)) {
                packageLocations.add(new ProjectViewPackageLocation(this.rootDirectory, packagePath));
            }
        }
        return packageLocations;
    }

    private void deleteProjects(IJavaProject[] projects) {
        BazelEclipseProjectSupport.runWithProgress(getProgressMonitor(), new WorkspaceModifyOperation() {
            @Override
            protected void execute(IProgressMonitor monitor) throws CoreException {
                for (IJavaProject project : projects) {
                    resourceHelper.deleteProject(project.getProject(), monitor);
                }
            }
        });
    }

    private static IJavaProject[] getAllJavaBazelProjects() {
        return BazelPluginActivator.getJavaCoreHelper().getAllBazelJavaProjects(false);
    }

    private static IProject getBazelRootProject() {
        for (IJavaProject project : BazelPluginActivator.getJavaCoreHelper().getAllBazelJavaProjects(true)) {
            if (BazelPluginActivator.getResourceHelper().isBazelRootProject(project.getProject())) {
                return project.getProject();
            }
        }
        throw new IllegalStateException("Root project not found");
    }
}
