/*-
 *
 */
package com.salesforce.bazel.eclipse.core.model;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BAZEL_NATURE_ID;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.CLASSPATH_CONTAINER_ID;
import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;
import static org.eclipse.core.resources.IResource.ALWAYS_DELETE_PROJECT_CONTENT;
import static org.eclipse.core.resources.IResource.FORCE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;

import com.salesforce.bazel.eclipse.core.model.discovery.TargetDiscoveryAndProvisioningExtensionLookup;
import com.salesforce.bazel.eclipse.core.model.discovery.TargetDiscoveryStrategy;
import com.salesforce.bazel.eclipse.core.model.discovery.TargetProvisioningStrategy;
import com.salesforce.bazel.eclipse.core.projectview.BazelProjectView;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * This job is responsible for synchronizing the Eclipse workspace with a Bazel workspace's project view.
 * <p>
 * The project view is the driving force for defining projects and visibility in Eclipse.
 * </p>
 */
public class SynchronizeProjectViewJob extends WorkspaceJob {

    private final BazelWorkspace workspace;
    private final BazelProjectView projectView;

    public SynchronizeProjectViewJob(BazelWorkspace workspace, BazelProjectView projectView) {
        super(format("Synchronizing project view for workspace: %s", workspace.getName()));
        this.workspace = workspace;
        this.projectView = projectView;

        // lock the full workspace (to prevent concurrent build activity)
        setRule(getWorkspaceRoot());
    }

    private IPath convertProjectViewDirectoryEntryToRelativPathWithoutTrailingSeparator(String path) {
        // special handling for '.'
        if (".".equals(path)) {
            return Path.EMPTY;
        }

        return new Path(path).makeRelative().removeTrailingSeparator();
    }

    private IProject createWorkspaceProject(IPath workspaceRoot, String workspaceName, SubMonitor monitor)
            throws CoreException {
        monitor.beginTask(format("Creating %s", workspaceName), 4);

        monitor.subTask("Creating project");
        var projectDescription = getWorkspace().newProjectDescription(workspaceName);
        projectDescription.setLocation(workspaceRoot);
        projectDescription.setComment(format(
            "Bazel Workspace Project managed by Bazel Eclipse Feature for Bazel workspace at '%s'", workspaceRoot));
        var project = getWorkspaceRoot().getProject(workspaceName);
        project.create(projectDescription, monitor.newChild(1));

        monitor.subTask("Opening project");
        project.open(monitor.newChild(1));

        // set natures separately in order to ensure they are configured properly
        monitor.subTask("Configuring natures");
        projectDescription = project.getDescription();
        projectDescription.setNatureIds(new String[] { JavaCore.NATURE_ID, BAZEL_NATURE_ID });
        project.setDescription(projectDescription, monitor.newChild(1));

        // set properties
        project.setPersistentProperty(BazelProject.PROJECT_PROPERTY_WORKSPACE_ROOT, workspaceRoot.toString());

        // configure the classpath container
        var javaProject = JavaCore.create(project);
        javaProject.setRawClasspath(
            new IClasspathEntry[] { JavaCore.newContainerEntry(new Path(CLASSPATH_CONTAINER_ID)) }, true,
            monitor.newChild(1));

        return project;
    }

    private Set<BazelTarget> detectTargetsToMaterializeInEclipse(IProject workspaceProject, SubMonitor monitor)
            throws CoreException {
        Set<BazelTarget> result = new HashSet<>();

        var targetsToExclude = projectView.targetsToExclude().stream().map(BazelLabel::new).collect(toSet());

        if (projectView.deriveTargetsFromDirectories()) {
            // use strategy configured for workspace
            var targetDiscoveryStrategy = getTargetDiscoveryStrategy();

            // we are comparing using project relative paths
            Set<IPath> allowedDirectories = projectView.directoriesToInclude().stream()
                    .map(this::convertProjectViewDirectoryEntryToRelativPathWithoutTrailingSeparator).collect(toSet());
            Set<IPath> explicitelyExcludedDirectories = projectView.directoriesToInclude().stream()
                    .map(this::convertProjectViewDirectoryEntryToRelativPathWithoutTrailingSeparator).collect(toSet());

            monitor.beginTask("Discovering targets", allowedDirectories.size());
            for (IPath directory : allowedDirectories) {
                if (findPathOrAnyParentInSet(directory, explicitelyExcludedDirectories)) {
                    continue;
                }

                var bazelPackage = workspace.getBazelPackage(directory);
                monitor.subTask(bazelPackage.getLabel().toString());
                var bazelTargets = targetDiscoveryStrategy.discoverTargets(bazelPackage, monitor.newChild(1));

                bazelTargets.stream().filter(t -> !targetsToExclude.contains(t.getLabel())).forEach(result::add);
            }
        }

        for (String targetToInclude : projectView.targetsToInclude()) {
            var label = new BazelLabel(targetToInclude);
            if (!targetsToExclude.contains(label)) {
                result.add(workspace.getBazelTarget(label));
            }
        }

        monitor.done();
        return result;
    }

    /**
     * Checks a given set if it contains an entry for a given path or any of its parents
     *
     * @param pathToFind
     *            the path to find
     * @param paths
     *            the set to check
     *
     * @return <code>true</code> if a hit was found, <code>false</code> otherwise
     */
    private boolean findPathOrAnyParentInSet(IPath pathToFind, Set<IPath> paths) {
        while (!pathToFind.isEmpty()) {
            if (paths.contains(pathToFind)) {
                return true; // found
            }
            pathToFind = pathToFind.removeLastSegments(1);
        }
        // check for empty path entry
        return paths.contains(pathToFind);
    }

    private IProject findProjectForLocation(IPath location) {
        var potentialProjects = getWorkspaceRoot().findContainersForLocationURI(URIUtil.toURI(location));
        if (potentialProjects.length != 1) {
            return null;
        }

        var potentialProject = potentialProjects[0];
        if (potentialProject.getType() != IResource.PROJECT) {
            return null;
        }

        return (IProject) potentialProject;
    }

    BazelProject getBazelProject(IProject project) {
        return workspace.getModelManager().getBazelProject(project);
    }

    TargetDiscoveryStrategy getTargetDiscoveryStrategy() throws CoreException {
        return new TargetDiscoveryAndProvisioningExtensionLookup().createTargetDiscoveryStrategy(projectView);
    }

    TargetProvisioningStrategy getTargetProvisioningStrategy() throws CoreException {
        return new TargetDiscoveryAndProvisioningExtensionLookup().createTargetProvisioningStrategy(projectView);
    }

    IWorkspace getWorkspace() {
        return ResourcesPlugin.getWorkspace();
    }

    IWorkspaceRoot getWorkspaceRoot() {
        return getWorkspace().getRoot();
    }

    private void hideFoldersNotVisibleAccordingToProjectView(IProject workspaceProject, SubMonitor monitor)
            throws CoreException {
        monitor.beginTask("Hiding non visible folders", 1);

        // we are comparing using project relative paths
        Set<IPath> allowedDirectories = projectView.directoriesToInclude().stream()
                .map(this::convertProjectViewDirectoryEntryToRelativPathWithoutTrailingSeparator).collect(toSet());
        Set<IPath> explicitelyExcludedDirectories = projectView.directoriesToInclude().stream()
                .map(this::convertProjectViewDirectoryEntryToRelativPathWithoutTrailingSeparator).collect(toSet());

        Set<IPath> alwaysAllowedFolders = Set.of(new Path(".settings"), new Path(".eclipse"), new Path(".bazel"));

        workspaceProject.accept(resource -> {
            // we only hide folders, i.e. all files contained in the project remain visible
            if (resource.getType() == IResource.FOLDER) {
                var path = resource.getProjectRelativePath();
                if (findPathOrAnyParentInSet(path, alwaysAllowedFolders)) {
                    // never hide those
                    resource.setHidden(false);
                    return false;
                }

                var isIncluded = findPathOrAnyParentInSet(path, allowedDirectories);
                if (isIncluded) {
                    // hide when explicitly excluded (otherwise don't hide)
                    var isExcluded = findPathOrAnyParentInSet(path, explicitelyExcludedDirectories);
                    resource.setHidden(isExcluded);

                    // continue checking the hierarchy for additional excludes
                    // (this is only needed when the folder is visible and not excluded so far)
                    return !isExcluded;
                }

                // resource is not included, hide it
                resource.setHidden(true);

                // no need to continue searching
                return false;
            }

            // we cannot make a decision, continue searching
            return true;
        });

        monitor.done();
    }

    private List<BazelProject> provisionProjectsForTarget(Set<BazelTarget> targets, SubMonitor monitor)
            throws CoreException {
        return getTargetProvisioningStrategy().provisionProjectsForTarget(targets, monitor);
    }

    private void removeObsoleteProjects(List<BazelProject> provisionedProjects, SubMonitor monitor)
            throws CoreException {
        var obsoleteProjects = new ArrayList<IProject>();
        for (IProject project : getWorkspaceRoot().getProjects()) {
            if (!project.isOpen() || !project.hasNature(BAZEL_NATURE_ID)) {
                continue;
            }

            var bazelProject = getBazelProject(project);
            if (bazelProject.isWorkspaceProject()) {
                continue;
            }

            try {
                if (workspace.equals(bazelProject.getBazelWorkspace()) && !provisionedProjects.contains(bazelProject)) {
                    obsoleteProjects.add(project);
                }
            } catch (CoreException e) {
                // no workspace found, consider the project obsolete
                obsoleteProjects.add(project);
            }
        }

        if (obsoleteProjects.size() > 0) {
            monitor.beginTask("Removing obsolete projects", obsoleteProjects.size());
            for (IProject project : obsoleteProjects) {
                project.delete(ALWAYS_DELETE_PROJECT_CONTENT | FORCE, monitor.newChild(1));
            }
        }

        monitor.done();
    }

    @Override
    public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
        try {
            var progress = SubMonitor.convert(monitor, "Synchronizing", 10);

            // ensure workspace project exists
            var workspaceName = workspace.getName();
            var workspaceRoot = workspace.getLocation();

            // we don't care about the actual project name - we look for the path
            var workspaceProject = findProjectForLocation(workspaceRoot);
            if (workspaceProject == null) {
                workspaceProject = createWorkspaceProject(workspaceRoot, workspaceName, progress.newChild(1));
            } else if (!workspaceProject.isOpen()) {
                workspaceProject.open(progress.newChild(1));
            }

            // ensure it's latest
            workspaceProject.refreshLocal(IResource.DEPTH_INFINITE, progress.newChild(1));

            // apply excludes
            hideFoldersNotVisibleAccordingToProjectView(workspaceProject, progress.newChild(1));

            // detect targets
            var targets = detectTargetsToMaterializeInEclipse(workspaceProject, progress.newChild(1));

            // ensure project exists
            var targetProjects = provisionProjectsForTarget(targets, progress.newChild(1));

            // remove no longer needed projects
            removeObsoleteProjects(targetProjects, progress.newChild(1));

            return Status.OK_STATUS;
        } finally {
            if (monitor != null) {
                monitor.done();
            }
        }
    }

}
