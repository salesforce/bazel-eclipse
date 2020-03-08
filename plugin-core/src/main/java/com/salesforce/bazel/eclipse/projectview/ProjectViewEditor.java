package com.salesforce.bazel.eclipse.projectview;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

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
import com.salesforce.bazel.eclipse.config.BazelEclipseProjectSupport;
import com.salesforce.bazel.eclipse.logging.LogHelper;
import com.salesforce.bazel.eclipse.model.BazelLabel;
import com.salesforce.bazel.eclipse.model.BazelMarkerDetails;
import com.salesforce.bazel.eclipse.model.BazelPackageLocation;
import com.salesforce.bazel.eclipse.model.projectview.ProjectView;
import com.salesforce.bazel.eclipse.model.projectview.ProjectViewConstants;
import com.salesforce.bazel.eclipse.model.projectview.ProjectViewPackageLocation;
import com.salesforce.bazel.eclipse.wizard.BazelProjectImporter;

public class ProjectViewEditor extends AbstractDecoratedTextEditor {
    
    private static final String CONFIRMATION_TEXT = 
        "Update the Bazel Project(s) in your Workspace to match the content of this file?";
    
    private static final LogHelper LOG = LogHelper.log(ProjectViewEditor.class);
    
    private static final String PROJECT_VIEW_RESOURCE = File.separator + ProjectViewConstants.PROJECT_VIEW_FILE_NAME;
    private static final BazelLabel ROOT_LABEL = new BazelLabel("//:*");
    
    private final IProject rootProject;
    private final File rootDirectory;
    private final ProjectViewPackageLocation rootPackage;
    
    public ProjectViewEditor() {
        this.rootProject = getBazelRootProject();
        this.rootDirectory = BazelPluginActivator.getBazelWorkspace().getBazelWorkspaceRootDirectory();
        this.rootPackage = new ProjectViewPackageLocation(this.rootDirectory, "");
        setDocumentProvider(new TextFileDocumentProvider());
        super.setSourceViewerConfiguration(new SourceViewerConfiguration() {             
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
    
    protected void editorSaved() {
        super.editorSaved();        
        String projectViewContent = getSourceViewer().getTextWidget().getText();
        ProjectView projectView = new ProjectView(this.rootDirectory, projectViewContent);
        List<BazelPackageLocation> invalidPackages = projectView.getInvalidPackages();
        Collection<BazelMarkerDetails> problemMarkers = new ArrayList<>();
        for (BazelPackageLocation invalidPackage : invalidPackages) {
            problemMarkers.add(new BazelMarkerDetails(PROJECT_VIEW_RESOURCE, projectView.getLineNumber(invalidPackage), 
                "Bad Bazel Package:" + invalidPackage.getBazelPackageFSRelativePath()));
        }
        // publishProblemMarkers also takes care of clearing old markers
        BazelEclipseProjectSupport.publishProblemMarkers(this.rootProject, getProgressMonitor(), problemMarkers, Collections.singleton(ROOT_LABEL));
        if (problemMarkers.isEmpty()) {
            IJavaProject[] currentlyImportedProjects = getAllJavaBazelProjects();
            
            List<BazelPackageLocation> currentlyImportedPackages = getPackages(currentlyImportedProjects);
            List<BazelPackageLocation> packagesToImport = projectView.getPackages();
            
            if (new HashSet<>(currentlyImportedPackages).equals(new HashSet<>(packagesToImport))) {
                LOG.info("The Bazel Packages in the " + ProjectViewConstants.PROJECT_VIEW_FILE_NAME + " file match the set of Eclipse Projects currently imported");
            } else {
                boolean ok = MessageDialog.openConfirm(this.getSite().getShell(), "Update Imported Projects", CONFIRMATION_TEXT);
                if (ok) {
                    deleteProjects(currentlyImportedProjects);
                    BazelProjectImporter.run(this.rootPackage, packagesToImport);
                }
            }
        }
    }
    
    private List<BazelPackageLocation> getPackages(IJavaProject[] projects) {
        List<BazelPackageLocation> packageLocations = new ArrayList<>(projects.length);
        for (IJavaProject project : projects) {
            // get the target to get at the package path
            String target = BazelEclipseProjectSupport.getBazelTargetsForEclipseProject(project.getProject(), false).get(0);
            BazelLabel label = new BazelLabel(target);
            packageLocations.add(new ProjectViewPackageLocation(this.rootDirectory, label.getPackagePath()));
        }
        return packageLocations;
    }
    
    private void deleteProjects(IJavaProject[] projects) {
        BazelEclipseProjectSupport.runWithProgress(getProgressMonitor(), new WorkspaceModifyOperation() {
            @Override
            protected void execute(IProgressMonitor monitor) throws CoreException {
                for (IJavaProject project : projects) {
                    final boolean deleteContent = true; // delete metadata also, under the Eclipse Workspace directory
                    final boolean force = true;
                    project.getProject().delete(deleteContent, force, monitor);
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
