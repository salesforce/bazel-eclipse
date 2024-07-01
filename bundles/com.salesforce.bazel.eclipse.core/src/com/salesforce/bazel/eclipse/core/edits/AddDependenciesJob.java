package com.salesforce.bazel.eclipse.core.edits;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Arrays;
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
import com.salesforce.bazel.eclipse.core.classpath.ClasspathHolder;
import com.salesforce.bazel.eclipse.core.classpath.InitializeOrRefreshClasspathJob;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.sdk.command.BuildozerCommand;
import com.salesforce.bazel.sdk.model.BazelLabel;

public class AddDependenciesJob extends WorkspaceJob {

    protected final BazelProject bazelProject;
    protected final Collection<Label> labelsToAdd;
    protected final Collection<ClasspathEntry> newClasspathEntries;

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

    /**
     * Called by {@link #runInWorkspace(IProgressMonitor)} to update the project's BUILD file.
     * <p>
     * The default implementation calls {@link #updateTargetsUsingBuildozer(List, String, SubMonitor)} for the
     * <code>deps</code> attribute.
     * </p>
     *
     * @param monitor
     *            {@link SubMonitor} for reporting progress
     * @return the number of updated targets
     * @throws CoreException
     */
    protected int addDependenciesToProject(SubMonitor monitor) throws CoreException {
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
                        format("Project '%s' cannot be updated! No targets found to update.", bazelProject.getName())));
        }

        return updateTargetsUsingBuildozer(targetsToUpdate, "deps", monitor.split(1));
    }

    IResourceRuleFactory getRuleFactory() {
        return ResourcesPlugin.getWorkspace().getRuleFactory();
    }

    @Override
    public IStatus runInWorkspace(IProgressMonitor progress) throws CoreException {
        try {
            var monitor = SubMonitor.convert(progress, 2);
            var updated = addDependenciesToProject(monitor);

            // log a message if nothing was updated
            if (updated == 0) {
                return Status.info(
                    format(
                        "No suitable targets found in '%s' to add dependency %s to.",
                        bazelProject.getBazelBuildFile(),
                        labelsToAdd.stream().map(Label::toString).collect(joining(", "))));
            }

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
                    var existing = classpath.stream()
                            .filter(
                                c -> (c.getEntryKind() == newClasspathEntry.getEntryKind())
                                        && c.getPath().equals(newClasspathEntry.getPath()))
                            .findFirst();
                    if (existing.isPresent()) {
                        // remove the test attribute in case a runtime dependency is changed to become a compile dependency
                        if (!existing.get().isTest()) {
                            // skip if there is already an existing entry
                            continue;
                        }
                        existing.get().setTest(true);
                        modified = true;
                    } else {
                        classpath.add(newClasspathEntry);
                        modified = true;
                    }
                }

                if (modified) {
                    var unloaded = Arrays.stream(container.getUnloadedEntries())
                            .map(ClasspathEntry::fromExisting)
                            .collect(toList());
                    classpathManager
                            .patchClasspathContainer(bazelProject, new ClasspathHolder(classpath, unloaded), monitor);
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

    protected int updateTargetsUsingBuildozer(List<String> targetsToUpdate, String depsAttributeName,
            SubMonitor monitor) throws CoreException {

        // filter that targets to update based on top level function calls actually using the attribute name
        var buildFile = bazelProject.getBazelBuildFile();
        var affactedTopLevelCalls = buildFile.getTopLevelCalls()
                .stream()
                .filter(m -> (m.getStringListArgument(depsAttributeName) != null))
                .map(m -> buildFile.getParent().getLabel().toString() + ":" + m.getName())
                .toList();

        // if there is only one top level macro call we use it directly
        // otherwise we try to match based on targetsToUpdate
        List<String> targetsForBuildozerToUpdate;
        if (affactedTopLevelCalls.size() > 1) {
            targetsForBuildozerToUpdate =
                    affactedTopLevelCalls.stream().filter(targetsToUpdate::contains).collect(toList());
        } else {
            targetsForBuildozerToUpdate = affactedTopLevelCalls;
        }

        if (!targetsForBuildozerToUpdate.isEmpty()) {
            var workspaceRoot = bazelProject.getBazelWorkspace().getLocation().toPath();
            var buildozerCommand = new BuildozerCommand(
                    workspaceRoot,
                    labelsToAdd.stream().map(l -> format("add %s %s", depsAttributeName, l)).collect(toList()),
                    targetsForBuildozerToUpdate,
                    format(
                        "Add label(s) '%s' to '%s'",
                        labelsToAdd.stream().map(Label::toString).collect(joining(", ")),
                        buildFile.getLocation()));
            bazelProject.getBazelWorkspace()
                    .getCommandExecutor()
                    .runDirectlyWithinExistingWorkspaceLock(
                        buildozerCommand,
                        List.of(bazelProject.getBuildFile()),
                        monitor);
        }

        return targetsForBuildozerToUpdate.size();
    }
}