package com.salesforce.bazel.eclipse.ui.jdt;

import static java.lang.String.format;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.ui.text.java.ClasspathFixProcessor.ClasspathFixProposal;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import com.google.idea.blaze.base.model.primitives.Label;
import com.salesforce.bazel.eclipse.core.edits.AddDependenciesJob;
import com.salesforce.bazel.eclipse.core.model.BazelBuildFile;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.eclipse.ui.BazelUIPlugin;

/**
 * A factory class used to create resolutions for JDT problem markers which may modify <code>BUILD.bazel</code> files
 */
public class JavaResolutionFactory {

    public static class AddDependencyChange extends BuildFileChange {

        private final Label labelToAdd;
        private final ClasspathEntry newClasspathEntry;

        public AddDependencyChange(BazelBuildFile bazelBuildFile, BazelProject bazelProject, Label labelToAdd,
                ClasspathEntry newClasspathEntry) {
            super(bazelBuildFile, bazelProject);
            this.labelToAdd = labelToAdd;
            this.newClasspathEntry = newClasspathEntry;
        }

        @Override
        public String getDescription() {
            return "Adds a label that provides a needed package or type to the list of dependencies for this project.";
        }

        @Override
        public Image getImage() {
            return BazelUIPlugin.getDefault().getImageRegistry().get(BazelUIPlugin.ICON_BAZEL);
        }

        @Override
        public String getName() {
            return format("Add '%s' to dependencies", labelToAdd);
        }

        @Override
        public Change perform(IProgressMonitor pm) throws CoreException {
            var job = new AddDependenciesJob(bazelProject, List.of(labelToAdd), List.of(newClasspathEntry));
            job.runInWorkspace(pm); // run directly because this should be executed in workspace lock already
            // FIXME: if this blocks the UI we should send it to the background (needs testing)
            return null;
        }
    }

    public static abstract class BuildFileChange extends Change {

        protected final BazelBuildFile bazelBuildFile;
        protected final BazelProject bazelProject;

        public BuildFileChange(BazelBuildFile bazelBuildFile, BazelProject bazelProject) {
            this.bazelBuildFile = bazelBuildFile;
            this.bazelProject = bazelProject;
        }

        /**
         * Provides a description for the Change
         */
        public abstract String getDescription();

        /**
         * Provides an image for the Change
         */
        public abstract Image getImage();

        @Override
        public Object getModifiedElement() {
            return bazelProject.getProject();
        }

        @Override
        public void initializeValidationData(IProgressMonitor pm) {
            // empty
        }

        @Override
        public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException, OperationCanceledException {
            return RefactoringStatus.create(Status.OK_STATUS);
        }

    }

    public enum ProposalType {
        JAVA_COMPLETION, CLASSPATH_FIX
    }

    public static Object createAddDependencyProposal(BazelBuildFile bazelBuildFile, BazelProject bazelProject,
            Label label, ClasspathEntry classpathEntry, ProposalType proposalType, int relevance) {
        return createWrapper(
            new AddDependencyChange(bazelBuildFile, bazelProject, label, classpathEntry),
            proposalType,
            relevance);
    }

    static ClasspathFixProposal createClasspathFixProposal(BuildFileChange change, int relevance) {
        return new ClasspathFixProposal() {

            @Override
            public Change createChange(IProgressMonitor monitor) throws CoreException {
                return change;
            }

            @Override
            public String getAdditionalProposalInfo() {
                return change.getDescription();
            }

            @Override
            public String getDisplayString() {
                return change.getName();
            }

            @Override
            public Image getImage() {
                return change.getImage();
            }

            @Override
            public int getRelevance() {
                return relevance;
            }

        };
    }

    static IJavaCompletionProposal createJavaCompletionProposal(BuildFileChange change, int relevance) {
        return new IJavaCompletionProposal() {

            @Override
            public void apply(IDocument document) {
                try {
                    change.perform(new NullProgressMonitor());
                } catch (CoreException e) {}
            }

            @Override
            public String getAdditionalProposalInfo() {
                return change.getDescription();
            }

            @Override
            public IContextInformation getContextInformation() {
                return null;
            }

            @Override
            public String getDisplayString() {
                return change.getName();
            }

            @Override
            public Image getImage() {
                return change.getImage();
            }

            @Override
            public int getRelevance() {
                return relevance;
            }

            @Override
            public Point getSelection(IDocument document) {
                return null;
            }
        };
    }

    private static final Object createWrapper(BuildFileChange change, ProposalType proposalType, int relevance) {
        return switch (proposalType) {
            case JAVA_COMPLETION -> createJavaCompletionProposal(change, relevance);
            case CLASSPATH_FIX -> createClasspathFixProposal(change, relevance);
            default -> null;
        };
    }

}
