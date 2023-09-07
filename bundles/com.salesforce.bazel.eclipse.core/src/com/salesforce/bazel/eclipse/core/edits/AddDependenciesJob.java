package com.salesforce.bazel.eclipse.core.edits;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.resources.IResourceRuleFactory;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;

import com.google.idea.blaze.base.model.primitives.Label;
import com.salesforce.bazel.eclipse.core.classpath.InitializeOrRefreshClasspathJob;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.sdk.command.BuildozerCommand;
import com.salesforce.bazel.sdk.model.BazelLabel;

public final class AddDependenciesJob extends WorkspaceJob {
    private final BazelProject bazelProject;
    private final Collection<Label> labelsToAdd;
    private final Collection<ClasspathEntry> newClasspathEntries;

    public AddDependenciesJob(BazelProject bazelProject, Collection<Label> labelsToAdd,
            Collection<ClasspathEntry> newClasspathEntries) throws CoreException {
        super("Updating: " + bazelProject);
        this.bazelProject = requireNonNull(bazelProject);
        this.labelsToAdd = requireNonNull(labelsToAdd);
        this.newClasspathEntries = requireNonNull(newClasspathEntries);
        setPriority(LONG);
        // lock the workspace
        setRule(getRuleFactory().buildRule());
    }

    IResourceRuleFactory getRuleFactory() {
        return ResourcesPlugin.getWorkspace().getRuleFactory();
    }

    @Override
    public IStatus runInWorkspace(IProgressMonitor progress) throws CoreException {
        try {
            var monitor = SubMonitor.convert(progress, 2);
            List<String> targetsToUpdate;
            if (bazelProject.isTargetProject()) {
                targetsToUpdate = List.of(bazelProject.getBazelTarget().getLabel().toString());
            } else if (bazelProject.isPackageProject()) {
                targetsToUpdate = bazelProject.getBazelTargets()
                        .stream()
                        .map(BazelTarget::getLabel)
                        .map(BazelLabel::toString)
                        .collect(toList());
            } else {
                throw new CoreException(
                        Status.error(
                            format(
                                "Project '%s' cannot be updated! No targets found to update.",
                                bazelProject.getName())));
            }

            // java_library & co
            updateTargetsUsingAttribute(targetsToUpdate, "deps", monitor.split(1));

            // some other macros may use this
            updateTargetsUsingAttribute(targetsToUpdate, "dependencies", monitor.split(1));

        } catch (CoreException e) {
            return Status.error(format("Error updating '%s'. %s", bazelProject, e.getMessage()), e);
        } finally {
            progress.done();
        }

        if (!newClasspathEntries.isEmpty()) {
            scheduleClasspathContainerPatch();
        }

        return Status.OK_STATUS;
    }

    void scheduleClasspathContainerPatch() {
        final WorkspaceJob workspaceJob = new WorkspaceJob("Patch .classpath of " + bazelProject.getName()) {

            @Override
            public IStatus runInWorkspace(final IProgressMonitor monitor) throws CoreException {
                var classpathManager =
                        bazelProject.getBazelWorkspace().getParent().getModelManager().getClasspathManager();

                var container = classpathManager.getSavedContainer(bazelProject.getProject());
                if (container == null) {
                    // cannot patch, need to refresh the container
                    new InitializeOrRefreshClasspathJob(Stream.of(bazelProject), classpathManager, false).schedule();
                    return Status.OK_STATUS;
                }

                var classpath = new ArrayList<ClasspathEntry>();
                var modified = false;
                Stream.of(container.getClasspathEntries()).map(ClasspathEntry::fromExisting).forEach(classpath::add);
                for (ClasspathEntry newClasspathEntry : newClasspathEntries) {
                    if (classpath.stream()
                            .anyMatch(
                                c -> (c.getEntryKind() == newClasspathEntry.getEntryKind())
                                        && c.getPath().equals(newClasspathEntry.getPath()))) {
                        // skip if there is already an existing entry
                        continue;
                    }
                    classpath.add(newClasspathEntry);
                    modified = true;
                }

                if (modified) {
                    classpathManager.patchClasspathContainer(bazelProject, classpath, monitor);
                }
                return Status.OK_STATUS;
            }
        };
        workspaceJob.setPriority(Job.LONG);
        workspaceJob.setUser(isUser());
        workspaceJob.setSystem(isSystem());
        workspaceJob.setRule(getRuleFactory().buildRule());
        workspaceJob.schedule();
    }

    private void updateTargetsUsingAttribute(List<String> targetsToUpdate, String depsAttributeName, SubMonitor monitor)
            throws CoreException {

        // filter that targets to update based on top level macro calls actually using the attribute name
        var buildFile = bazelProject.getBazelBuildFile();
        List<String> targetsUsingDependencies = buildFile.getTopLevelMacroCalls()
                .stream()
                .filter(m -> (m.getStringListArgument(depsAttributeName) != null))
                .map(m -> buildFile.getParent().getLabel().toString() + ":" + m.getName())
                .filter(targetsToUpdate::contains)
                .collect(toList());

        if (!targetsUsingDependencies.isEmpty()) {
            var workspaceRoot = bazelProject.getBazelWorkspace().getLocation().toPath();
            var buildozerCommand = new BuildozerCommand(
                    workspaceRoot,
                    labelsToAdd.stream().map(l -> format("add %s %s", depsAttributeName, l)).collect(toList()),
                    targetsUsingDependencies,
                    "Add dependency '%s'");
            bazelProject.getBazelWorkspace()
                    .getCommandExecutor()
                    .runDirectlyWithinExistingWorkspaceLock(
                        buildozerCommand,
                        List.of(bazelProject.getBuildFile()),
                        monitor);
        }
    }
}