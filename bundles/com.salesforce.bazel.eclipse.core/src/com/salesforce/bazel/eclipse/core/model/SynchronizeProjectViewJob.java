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
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
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
import org.eclipse.core.runtime.jobs.ISchedulingRule;
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

    public SynchronizeProjectViewJob(BazelWorkspace workspace) throws CoreException {
        super(format("Synchronizing project view for workspace: %s", workspace.getName()));
        this.workspace = workspace;

        // trigger loading of the project view
        this.projectView = workspace.getBazelProjectView();

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
        monitor.setWorkRemaining(4);

        var projectDescription = getWorkspace().newProjectDescription(workspaceName);
        projectDescription.setLocation(workspaceRoot);
        projectDescription.setComment(format(
            "Bazel Workspace Project managed by Bazel Eclipse Feature for Bazel workspace at '%s'", workspaceRoot));
        var project = getWorkspaceRoot().getProject(workspaceName);
        project.create(projectDescription, monitor.split(1));

        project.open(monitor.split(1));

        // set natures separately in order to ensure they are configured properly
        projectDescription = project.getDescription();
        projectDescription.setNatureIds(new String[] { JavaCore.NATURE_ID, BAZEL_NATURE_ID });
        project.setDescription(projectDescription, monitor.split(1));

        // set properties
        project.setPersistentProperty(BazelProject.PROJECT_PROPERTY_WORKSPACE_ROOT, workspaceRoot.toString());

        // configure the classpath container
        var javaProject = JavaCore.create(project);
        javaProject.setRawClasspath(
            new IClasspathEntry[] { JavaCore.newContainerEntry(new Path(CLASSPATH_CONTAINER_ID)) }, true,
            monitor.split(1));

        return project;
    }

    /**
     * Checks whether the current rule contains the scheduling rule for running this job directly and returns any
     * missing rule.
     * <p>
     * This method can be used if the caller wants to run the job directly in this thread by calling
     * {@link #runInWorkspace(IProgressMonitor)} but is unsure about the proper rule acquisition. The returned result
     * can directly be passed to
     * {@link IWorkspace#run(org.eclipse.core.runtime.ICoreRunnable, ISchedulingRule, int, IProgressMonitor)}. For
     * example:
     *
     * <pre>
     * IWorkspace workspace = ...
     * var projectViewJob = new SynchronizeProjectViewJob(bazelWorkspace);
     *
     * // don't schedule the job but execute it directly with the required rule
     * var rule = projectViewJob.detectMissingRule();
     * workspace.run(projectViewJob::runInWorkspace, rule, IWorkspace.AVOID_UPDATE, monitor);
     * </pre>
     * </p>
     *
     * @return the required scheduling rule for calling {@link #runInWorkspace(IProgressMonitor)} directly (maybe
     *         <code>null</code> in case none is missing)
     */
    public ISchedulingRule detectMissingRule() {
        var requiredRule = getRule();
        var currentRule = getJobManager().currentRule();
        if ((currentRule != null) && !currentRule.contains(requiredRule)) {
            return requiredRule;
        }
        return null;
    }

    private Set<BazelTarget> detectTargetsToMaterializeInEclipse(IProject workspaceProject, SubMonitor monitor)
            throws CoreException {
        monitor.setWorkRemaining(2);

        Set<BazelTarget> result = new HashSet<>();

        var targetsToExclude = projectView.targetsToExclude().stream().map(BazelLabel::new).collect(toSet());

        if (projectView.deriveTargetsFromDirectories()) {
            // use strategy configured for workspace
            var targetDiscoveryStrategy = getTargetDiscoveryStrategy();

            // we are comparing using project relative paths
            Set<IPath> allowedDirectories = projectView.directoriesToInclude().stream()
                    .map(this::convertProjectViewDirectoryEntryToRelativPathWithoutTrailingSeparator).collect(toSet());
            Set<IPath> explicitelyExcludedDirectories = projectView.directoriesToExclude().stream()
                    .map(this::convertProjectViewDirectoryEntryToRelativPathWithoutTrailingSeparator).collect(toSet());

            // query workspace for all targets
            var bazelPackages = targetDiscoveryStrategy.discoverPackages(workspace, monitor.split(1));

            // if the '.' is listed in the project view it literal means include "everything"
            var includeEverything = allowedDirectories.contains(Path.EMPTY);

            monitor.setWorkRemaining(allowedDirectories.size());
            for (BazelPackage bazelPackage : bazelPackages) {
                // filter packages based in includes
                var directory = bazelPackage.getWorkspaceRelativePath();
                if (!includeEverything && !findPathOrAnyParentInSet(directory, allowedDirectories)) {
                    continue;
                }
                // filter based on excludes
                if (findPathOrAnyParentInSet(directory, explicitelyExcludedDirectories)) {
                    continue;
                }
                // get targets
                monitor.subTask(bazelPackage.getLabel().toString());
                var bazelTargets = targetDiscoveryStrategy.discoverTargets(bazelPackage, monitor.split(1));

                // add only targets not explicitly excluded
                bazelTargets.stream().filter(t -> !targetsToExclude.contains(t.getLabel())).forEach(result::add);
            }
        }

        for (String targetToInclude : projectView.targetsToInclude()) {
            var label = new BazelLabel(targetToInclude);
            if (!targetsToExclude.contains(label)) {
                result.add(workspace.getBazelTarget(label));
            }
        }

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
        monitor.setWorkRemaining(10);

        // we are comparing using project relative paths
        Set<IPath> allowedDirectories = projectView.directoriesToInclude().stream()
                .map(this::convertProjectViewDirectoryEntryToRelativPathWithoutTrailingSeparator).collect(toSet());
        Set<IPath> explicitelyExcludedDirectories = projectView.directoriesToExclude().stream()
                .map(this::convertProjectViewDirectoryEntryToRelativPathWithoutTrailingSeparator).collect(toSet());

        Set<IPath> alwaysAllowedFolders = Set.of(new Path(".settings"), new Path(".eclipse"), new Path(".bazel"));

        IResourceVisitor visitor = resource -> {
            // we only hide folders, i.e. all files contained in the project remain visible
            if (resource.getType() == IResource.FOLDER) {
                // hide the bazel symlinks (performance killer)
                if (resource.getName().startsWith("bazel-")) {
                    var resourceAttributes = resource.getResourceAttributes();
                    if ((resourceAttributes != null) && resourceAttributes.isSymbolicLink()) {
                        resource.setHidden(true);
                        return false;
                    }
                }

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
        };
        workspaceProject.accept(visitor, IResource.DEPTH_INFINITE,
            IContainer.INCLUDE_HIDDEN /* visit hidden ones so we can un-hide if necessary */);

        workspaceProject.refreshLocal(IResource.DEPTH_INFINITE, monitor.split(1));
    }

    private List<BazelProject> provisionProjectsForTarget(Set<BazelTarget> targets, SubMonitor monitor)
            throws CoreException {
        return getTargetProvisioningStrategy().provisionProjectsForSelectedTargets(targets, workspace, monitor);
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
            monitor.setWorkRemaining(obsoleteProjects.size());
            for (IProject project : obsoleteProjects) {
                project.delete(ALWAYS_DELETE_PROJECT_CONTENT | FORCE, monitor.split(1));
            }
        }
    }

    @Override
    public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
        try {

            // ensure workspace project exists
            var workspaceName = workspace.getName();
            var workspaceRoot = workspace.getLocation();

            var progress = SubMonitor.convert(monitor, format("Synchronizing '%s'", workspaceName), 10);

            // we don't care about the actual project name - we look for the path
            var workspaceProject = findProjectForLocation(workspaceRoot);
            if (workspaceProject == null) {
                workspaceProject = createWorkspaceProject(workspaceRoot, workspaceName, progress.split(1));
            } else if (!workspaceProject.isOpen()) {
                workspaceProject.open(progress.split(1));
            }

            // ensure it's latest
            workspaceProject.refreshLocal(IResource.DEPTH_INFINITE, progress.split(1));

            // apply excludes
            hideFoldersNotVisibleAccordingToProjectView(workspaceProject, progress.split(1));

            // detect targets
            var targets = detectTargetsToMaterializeInEclipse(workspaceProject, progress.split(1));

            // ensure project exists
            var targetProjects = provisionProjectsForTarget(targets, progress.split(1));

            // remove no longer needed projects
            removeObsoleteProjects(targetProjects, progress.split(1));

            return Status.OK_STATUS;
        } finally {
            if (monitor != null) {
                monitor.done();
            }
        }
    }

}
