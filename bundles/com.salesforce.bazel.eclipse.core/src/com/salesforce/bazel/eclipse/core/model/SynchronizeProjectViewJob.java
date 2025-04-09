/*-
 *
 */
package com.salesforce.bazel.eclipse.core.model;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BAZEL_NATURE_ID;
import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.RESOURCE_FILTER_BAZEL_OUTPUT_SYMLINKS_ID;
import static com.salesforce.bazel.eclipse.core.util.trace.Trace.getCurrentTrace;
import static com.salesforce.bazel.eclipse.core.util.trace.Trace.setCurrentTrace;
import static com.salesforce.bazel.sdk.util.DurationUtil.humanReadableFormat;
import static java.lang.String.format;
import static java.nio.file.Files.isReadable;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static org.eclipse.core.resources.IContainer.INCLUDE_HIDDEN;
import static org.eclipse.core.resources.IResource.DEPTH_ONE;
import static org.eclipse.core.resources.IResource.FORCE;
import static org.eclipse.core.resources.IResource.NEVER_DELETE_PROJECT_CONTENT;
import static org.eclipse.core.runtime.IPath.forPosix;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.FileInfoMatcherDescription;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceFilterDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IPreferenceFilter;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.core.runtime.preferences.PreferenceFilterEntry;
import org.eclipse.jdt.core.JavaCore;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.salesforce.bazel.eclipse.core.BazelCorePlugin;
import com.salesforce.bazel.eclipse.core.classpath.InitializeOrRefreshClasspathJob;
import com.salesforce.bazel.eclipse.core.events.SyncFinishedEvent;
import com.salesforce.bazel.eclipse.core.model.discovery.TargetDiscoveryAndProvisioningExtensionLookup;
import com.salesforce.bazel.eclipse.core.model.discovery.TargetDiscoveryStrategy;
import com.salesforce.bazel.eclipse.core.model.discovery.TargetProvisioningStrategy;
import com.salesforce.bazel.eclipse.core.projectview.BazelProjectView;
import com.salesforce.bazel.eclipse.core.util.trace.Trace;
import com.salesforce.bazel.eclipse.core.util.trace.TraceGraphDumper;
import com.salesforce.bazel.eclipse.core.util.trace.TracingSubMonitor;
import com.salesforce.bazel.sdk.command.BazelQueryForLabelsCommand;
import com.salesforce.bazel.sdk.projectview.ImportRoots;

/**
 * This job is responsible for synchronizing the Eclipse workspace with a Bazel workspace's project view.
 * <p>
 * The project view is the driving force for defining projects and visibility in Eclipse.
 * </p>
 */
public class SynchronizeProjectViewJob extends WorkspaceJob {

    private static Logger LOG = LoggerFactory.getLogger(SynchronizeProjectViewJob.class);

    static ImportRoots createImportRoots(BazelWorkspace workspace) throws CoreException {
        var builder = ImportRoots.builder(new WorkspaceRoot(workspace.workspacePath()));
        var projectView = workspace.getBazelProjectView();
        builder.setDeriveTargetsFromDirectories(projectView.deriveTargetsFromDirectories());
        projectView.directoriesToImport().forEach(builder::addRootDirectory);
        projectView.directoriesToExclude().forEach(builder::addExcludeDirectory);
        projectView.targets().forEach(builder::addTarget);
        return builder.build();
    }

    /**
     * Creates a query to run target lists through Bazel.
     * <p>
     * Copied from WildcardTargetExpander IJ plug-in.
     * </p>
     *
     * @param targets
     * @return
     */
    private static String queryForTargets(Collection<TargetExpression> targets) {
        var builder = new StringBuilder();
        for (TargetExpression target : targets) {
            var excluded = target.isExcluded();
            if (builder.length() == 0) {
                if (excluded) {
                    continue; // an excluded target at the start of the list has no effect
                }
                builder.append("'").append(target).append("'");
            } else if (excluded) {
                builder.append(" - ");
                // trim leading '-'
                var excludedTarget = target.toString();
                builder.append("'").append(excludedTarget, 1, excludedTarget.length()).append("'");
            } else {
                builder.append(" + ");
                builder.append("'").append(target).append("'");
            }
        }
        var targetList = builder.toString();
        if (targetList.isEmpty()) {
            return targetList;
        }

        // do not exclude the manual tags here but make sure to filter out "no-ide" targets
        return String.format("attr('tags', '^((?!no-ide).)*$', %s)", targetList);
    }

    private final BazelWorkspace workspace;
    private BazelProjectView projectView;
    private ImportRoots importRoots;

    public SynchronizeProjectViewJob(BazelWorkspace workspace) throws CoreException {
        super("Synchronizing Bazel projects");
        this.workspace = workspace;

        // don't perform any expensive work in the constructor
        // it may be called in the UI thread

        // lock the full workspace (to prevent concurrent build activity)
        setRule(getWorkspaceRoot());
    }

    private void callSynParticipants(List<BazelProject> targetProjects, TracingSubMonitor monitor, int work)
            throws CoreException {
        var synchronizationParticipants = getSynchronizationParticipants();
        if (synchronizationParticipants.isEmpty()) {
            monitor.worked(work);
            return;
        }

        monitor = monitor.split(work, "Calling synchronization participants")
                .setWorkRemaining(synchronizationParticipants.size());
        for (SynchronizationParticipant synchronizationParticipant : synchronizationParticipants) {
            try {
                synchronizationParticipant.afterSynchronizationCompleted(workspace, targetProjects, monitor.slice(1));
            } catch (CoreException | RuntimeException | LinkageError | AssertionError e) {
                LOG.error(
                    "Synchronization participant '{}' failed to execute. Please report this to the extension authors.",
                    synchronizationParticipant.getClass().toString(),
                    e);
            }
        }
    }

    private void configureFilters(IProject workspaceProject, TracingSubMonitor monitor, int work) throws CoreException {
        var filterExists = Stream.of(workspaceProject.getFilters()).anyMatch(f -> {
            var matcher = f.getFileInfoMatcherDescription();
            return RESOURCE_FILTER_BAZEL_OUTPUT_SYMLINKS_ID.equals(matcher.getId());
        });

        if (!filterExists) {
            // create filter will trigger a refresh - we perform it in this thread to ensure everything is good to continue
            workspaceProject.createFilter(
                IResourceFilterDescription.EXCLUDE_ALL | IResourceFilterDescription.FILES
                        | IResourceFilterDescription.FOLDERS,
                new FileInfoMatcherDescription(RESOURCE_FILTER_BAZEL_OUTPUT_SYMLINKS_ID, null),
                NONE,
                monitor.slice(work));
        } else {
            monitor.worked(work);
        }
    }

    private IPath convertProjectViewDirectoryEntryToRelativPathWithoutTrailingSeparator(WorkspacePath path) {
        // special handling for '.'
        if (path.isWorkspaceRoot()) {
            return Path.EMPTY;
        }

        return new Path(path.relativePath()).makeRelative().removeTrailingSeparator();
    }

    private IProject createWorkspaceProject(IPath workspaceRoot, String workspaceName, TracingSubMonitor monitor,
            int work) throws CoreException {
        monitor = monitor.split(work, "Creating workspace project").setWorkRemaining(4);

        var projectDescription = getWorkspace().newProjectDescription(workspaceName);
        projectDescription.setLocation(workspaceRoot);
        projectDescription.setComment(getWorkspaceProjectComment(workspaceRoot));
        var project = getWorkspaceRoot().getProject(workspaceName);
        if (!project.exists()) {
            project.create(projectDescription, monitor.slice(1));
        } else if (!workspaceRoot.equals(project.getLocation())) { // project.getLocation() should work regardless of project being open or closed (according to API spec)
            throw new CoreException(
                    Status.error(
                        format(
                            "Unable to create project for workspace '%s'. A project with name '%s' already exist in the workspace but points to a different location. Please delete it.",
                            workspaceRoot,
                            workspaceName)));
        }

        // open project but refresh in the background (there is another one coming later)
        project.open(IResource.BACKGROUND_REFRESH, monitor.slice(1));

        // set natures separately in order to ensure they are configured properly
        projectDescription = project.getDescription();
        projectDescription.setNatureIds(
            new String[] {
                    JavaCore.NATURE_ID,
                    BAZEL_NATURE_ID });
        project.setDescription(projectDescription, monitor.slice(1));

        // set properties
        project.setPersistentProperty(BazelProject.PROJECT_PROPERTY_WORKSPACE_ROOT, workspaceRoot.toString());
        project.setDefaultCharset(StandardCharsets.UTF_8.name(), monitor.slice(1));

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

    private Set<TargetExpression> detectTargetsToMaterializeInEclipse(IProject workspaceProject,
            TracingSubMonitor monitor, int work) throws CoreException {
        monitor = monitor.split(work, "Detecting targets");

        Set<TargetExpression> result = new HashSet<>();

        if (projectView.deriveTargetsFromDirectories()) {
            // use strategy configured for workspace
            var targetDiscoveryStrategy = getTargetDiscoveryStrategy();

            // we are comparing using project relative paths
            Set<IPath> allowedDirectories = projectView.directoriesToImport()
                    .stream()
                    .map(this::convertProjectViewDirectoryEntryToRelativPathWithoutTrailingSeparator)
                    .collect(toSet());
            Set<IPath> explicitelyExcludedDirectories = projectView.directoriesToExclude()
                    .stream()
                    .map(this::convertProjectViewDirectoryEntryToRelativPathWithoutTrailingSeparator)
                    .collect(toSet());

            // query workspace for all targets
            var bazelPackages = targetDiscoveryStrategy.discoverPackages(workspace, monitor.slice(1));

            // if the '.' is listed in the project view it literal means include "everything"
            var includeEverything = allowedDirectories.contains(Path.EMPTY);

            // filter packages to remove excludes
            bazelPackages = bazelPackages.stream().filter(bazelPackage -> {
                // filter packages based in includes
                var directory = forPosix(bazelPackage.relativePath());
                if (!includeEverything && !findPathOrAnyParentInSet(directory, allowedDirectories)) {
                    return false;
                }
                // filter based on excludes
                if (findPathOrAnyParentInSet(directory, explicitelyExcludedDirectories)) {
                    return false;
                }

                return true;
            }).collect(toList());

            // get targets
            var bazelTargets = targetDiscoveryStrategy.discoverTargets(workspace, bazelPackages, monitor.slice(1));

            // add only targets not explicitly excluded
            for (TargetExpression t : bazelTargets) {
                if (t instanceof Label l && !importRoots.targetInProject(l)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Excluding target '{}' per project view exclusion", t);
                    }
                    continue;
                }
                result.add(t);
            }
        }

        // add any explicitly configured target
        var manualTargetsQuery = queryForTargets(projectView.targets());
        if (!manualTargetsQuery.isBlank()) {
            var queryCommand = new BazelQueryForLabelsCommand(
                    workspace.workspacePath(),
                    manualTargetsQuery,
                    true,
                    "Identifying manual specified targets to synchronize");
            Collection<String> labels = workspace.getCommandExecutor().runQueryWithoutLock(queryCommand);
            for (String label : labels) {
                var targetExpression = Label.validate(label) == null ? TargetExpression.fromStringSafe(label)
                        : TargetExpression.fromStringSafe(label);
                if (targetExpression != null) {
                    result.add(targetExpression);
                }
                //var bazelLabel = new BazelLabel(label.toString());
                //result.add(workspace.getBazelTarget(bazelLabel));
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
        // use the first matching project
        for (IContainer container : potentialProjects) {
            if (container.getType() == IResource.PROJECT) {
                return (IProject) container;
            }
        }

        // nothing available
        return null;
    }

    BazelProject getBazelProject(IProject project) {
        return workspace.getModelManager().getBazelProject(project);
    }

    EventAdmin getEventAdmin() {
        return BazelCorePlugin.getInstance().getServiceTracker().getEventAdmin();
    }

    List<SynchronizationParticipant> getSynchronizationParticipants() throws CoreException {
        return new SynchronizationParticipantExtensionLookup().createSynchronizationParticipants();
    }

    TargetDiscoveryStrategy getTargetDiscoveryStrategy() throws CoreException {
        return new TargetDiscoveryAndProvisioningExtensionLookup().createTargetDiscoveryStrategy(projectView);
    }

    String getTargetDiscoveryStrategyName() throws CoreException {
        return new TargetDiscoveryAndProvisioningExtensionLookup().getTargetDiscoveryStrategyName(projectView);
    }

    TargetProvisioningStrategy getTargetProvisioningStrategy() throws CoreException {
        return new TargetDiscoveryAndProvisioningExtensionLookup().createTargetProvisioningStrategy(projectView);
    }

    String getTargetProvisioningStrategyName() throws CoreException {
        return new TargetDiscoveryAndProvisioningExtensionLookup().getTargetProvisioningStrategyName(projectView);
    }

    IWorkspace getWorkspace() {
        return ResourcesPlugin.getWorkspace();
    }

    private String getWorkspaceProjectComment(IPath workspaceRoot) {
        return format(
            "Bazel Workspace Project managed by Bazel Eclipse Feature for Bazel workspace at '%s'",
            workspaceRoot);
    }

    IWorkspaceRoot getWorkspaceRoot() {
        return getWorkspace().getRoot();
    }

    private void hideFoldersNotVisibleAccordingToProjectViewAndSmartRefresh(IProject workspaceProject,
            TracingSubMonitor monitor, int work) throws CoreException {
        monitor = monitor.split(work, "Configuring visible folders").setWorkRemaining(10);

        // we are comparing using project relative paths
        Set<IPath> alwaysAllowedFolders = Set.of(new Path(".settings"), new Path(".eclipse"));

        // build a tree of visible paths
        var foundWorkspaceRoot = importRoots.rootDirectories().stream().anyMatch(WorkspacePath::isWorkspaceRoot);

        // collect path and all its parents
        Set<IPath> visiblePaths = new HashSet<>();
        for (WorkspacePath dir : importRoots.rootDirectories()) {
            var directoryPath = IPath.fromPath(dir.asPath());
            while (!directoryPath.isEmpty()) {
                visiblePaths.add(directoryPath);
                directoryPath = directoryPath.removeLastSegments(1);
            }
        }

        refreshFolderAndHideMembersIfNecessary(
            monitor,
            alwaysAllowedFolders,
            foundWorkspaceRoot,
            visiblePaths,
            3 /* max deepness */,
            workspaceProject);
    }

    private void importPreferences(Collection<WorkspacePath> importPreferences, TracingSubMonitor monitor, int work)
            throws CoreException {
        if (importPreferences.isEmpty()) {
            monitor.worked(work);
            return;
        }

        var workspaceRoot = workspace.getLocation().toPath();

        monitor = monitor.split(work, "Importing preferences...").setWorkRemaining(importPreferences.size());
        for (WorkspacePath epfFile : importPreferences) {
            monitor.subTask(epfFile.toString());

            var epfFilePath = workspaceRoot.resolve(epfFile.asPath());
            if (!isReadable(epfFilePath)) {
                throw new CoreException(
                        Status.error(
                            format(
                                "Eclipse preference file '%s' is not readable. Please check .bazelproject file!",
                                epfFile)));
            }

            try (var fis = new FileInputStream(epfFilePath.toFile())) {
                final var service = Platform.getPreferencesService();
                final var prefs = service.readPreferences(fis);

                // create a filter that matches *all*
                final var filters = new IPreferenceFilter[1];
                filters[0] = new IPreferenceFilter() {

                    @Override
                    public Map<String, PreferenceFilterEntry[]> getMapping(final String scope) {
                        // note, here we could limit the values that should be imported
                        // we don't do this because we expect the .epf file to be correctly stripped
                        return null;
                    }

                    @Override
                    public String[] getScopes() {
                        return new String[] {
                                InstanceScope.SCOPE,
                                ConfigurationScope.SCOPE };
                    }
                };

                service.applyPreferences(prefs, filters);

                monitor.worked(1);
            } catch (IOException | CoreException e) {
                throw new CoreException(
                        Status.error(format("Error importing preference file '%s': %s", epfFile, e.getMessage()), e));
            }
        }
    }

    private void initializeClasspaths(List<BazelProject> projects, BazelWorkspace workspace, TracingSubMonitor monitor,
            int work) throws CoreException {
        monitor = monitor.split(work, format("Initializing Classpaths for %d projects", projects.size()));

        // use the job to properly trigger the classpath manager
        var status = new InitializeOrRefreshClasspathJob(
                projects.contains(workspace.getBazelProject()) ? projects.stream()
                        : concat(Stream.of(workspace.getBazelProject()), projects.stream()),
                workspace.getParent().getModelManager().getClasspathManager(),
                true).runInWorkspace(monitor);

        // re-throw any error so we get proper reporting in the UI
        if (status.matches(IStatus.ERROR)) {
            throw new CoreException(status);
        }
    }

    private void logSyncStats(String workspaceName, Duration duration, int projectsCount, int targetsCount,
            Trace trace) {
        var lines = TraceGraphDumper.dumpTrace(trace, 100, 0F, TimeUnit.MILLISECONDS);
        LOG.info(
            "Synchronization of workspace '{}' finished successfully (duration {}, {} targets, {} projects){}{}",
            workspaceName,
            humanReadableFormat(duration),
            targetsCount,
            projectsCount,
            System.lineSeparator(),
            lines.stream().collect(joining(System.lineSeparator())));
    }

    private List<BazelProject> provisionProjectsForTarget(Set<TargetExpression> targets, TracingSubMonitor monitor,
            int work) throws CoreException {
        return getTargetProvisioningStrategy()
                .provisionProjectsForSelectedTargets(targets, workspace, monitor.split(work, "Provisioning Projects"));
    }

    private void refreshFolderAndHideMembersIfNecessary(IProgressMonitor monitor, Set<IPath> alwaysAllowedFolders,
            boolean foundWorkspaceRoot, Set<IPath> visiblePaths, int maxDepth, IContainer container)
            throws CoreException {
        monitor.subTask(container.getFullPath().toString());

        // ensure the folder is up to date
        container.refreshLocal(DEPTH_ONE, monitor.slice(1));

        // check all its children
        var members = container.members(INCLUDE_HIDDEN);
        for (IResource resource : members) {
            // we only hide folders, i.e. all files contained in the project remain visible
            if (resource.getType() != IResource.FOLDER) {
                continue;
            }

            var path = resource.getProjectRelativePath();
            if (findPathOrAnyParentInSet(path, alwaysAllowedFolders)) {
                // never hide those in the always allowed folders
                resource.setHidden(false);

                // continue with next member, there is no need to go any deeper here
                continue;
            }

            // we need to check three things
            // 1. if workspace root '.' is listed, everything should be visible by default
            // 2. if a parent is in visiblePaths it should be visible
            // 3. if a sub-sub-sub directory is in importRoots then it should be visible as well
            var visible = foundWorkspaceRoot || visiblePaths.contains(resource.getProjectRelativePath())
                    || importRoots.containsWorkspacePath(new WorkspacePath(path.toString()));
            // but an explicit exclude dominates
            var excluded = importRoots.isExcluded(new WorkspacePath(path.toString()));

            // summarize hidden state
            var isHidden = excluded || !visible;

            // toggle the resource hidden state
            resource.setHidden(isHidden);

            // there was no update to the resource
            // in for efficiency of process don't go deeper if a folder is hidden
            if (isHidden) {
                continue;
            }

            // folder is visible then check its children
            if ((maxDepth - 1) > 0) {
                refreshFolderAndHideMembersIfNecessary(
                    monitor.slice(1),
                    alwaysAllowedFolders,
                    foundWorkspaceRoot,
                    visiblePaths,
                    maxDepth - 1,
                    (IContainer) resource);
            }
        }
    }

    private void removeObsoleteProjects(List<BazelProject> provisionedProjects, TracingSubMonitor monitor, int work)
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
            var pm = monitor.split(work, "Cleaning up").setWorkRemaining(obsoleteProjects.size());
            for (IProject project : obsoleteProjects) {
                project.delete(FORCE | NEVER_DELETE_PROJECT_CONTENT, pm.slice(1));
            }
        } else {
            monitor.worked(work);
        }
    }

    @Override
    public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
        // track the start
        var progress = TracingSubMonitor
                .convert(monitor, format("Synchronizing %s", workspace.getLocation().lastSegment()), 60);
        var trace = requireNonNull(getCurrentTrace(), "Tracing is supposed to be active at this point!");
        try {
            // invalidate the entire cache because we want to ensure we sync fresh
            // FIXME: this should not be required but currently is because our ResourceChangeProcessor is very light
            // ideally we would monitor resource change events and invalidate individual targets/packages only when necessary
            workspace.getModel().getInfoCache().invalidateAll();

            // during synchronization resource changes may occur; however, they are triggered by the synchronization activities
            // therefore we suspend cache invalidation of the model due to resource changes
            workspace.getModelManager().getResourceChangeProcessor().suspendInvalidationFor(workspace);

            // trigger loading of the project view
            projectView = workspace.getBazelProjectView();
            importRoots = createImportRoots(workspace);

            // ensure workspace project exists
            var workspaceName = workspace.getName();
            var workspaceRoot = workspace.getLocation();

            // log an event so we can verify a few things later
            if (LOG.isInfoEnabled()) {
                var syncInfo = new StringBuilder();
                syncInfo.append("Synchronizing workspace '")
                        .append(workspaceName)
                        .append("'")
                        .append(System.lineSeparator());
                syncInfo.append("  bazel binary: ")
                        .append(null != workspace.getBazelBinary() ? workspace.getBazelBinary().toString() : "default");
                LOG.info(syncInfo.toString());
            }

            // import preferences
            importPreferences(projectView.importPreferences(), progress, 1);

            // we don't care about the actual project name - we look for the path
            var workspaceProject = findProjectForLocation(workspaceRoot);
            if (workspaceProject == null) {
                // create new project
                workspaceProject = createWorkspaceProject(workspaceRoot, workspaceName, progress, 1);
            } else {
                // open existing
                if (!workspaceProject.isOpen()) {
                    workspaceProject.open(progress.slice(1));
                }
                // fix name
                if (!workspaceName.equals(workspaceProject.getName())) {
                    var projectDescription = workspaceProject.getDescription();
                    projectDescription.setName(workspaceName);
                    projectDescription.setComment(getWorkspaceProjectComment(workspaceRoot));
                    workspaceProject.move(projectDescription, true, progress.slice(1));
                }
            }

            // sanity check
            if (!workspaceProject.isAccessible()) {
                throw new CoreException(
                        Status.error(
                            format(
                                "Unable to sync workspace '%s' because the project is not accessible!",
                                workspaceRoot)));
            }

            // ensure Bazel symlinks are filtered
            configureFilters(workspaceProject, progress, 1);

            // apply excludes
            hideFoldersNotVisibleAccordingToProjectViewAndSmartRefresh(workspaceProject, progress, 10);

            // detect targets
            var targets = detectTargetsToMaterializeInEclipse(workspaceProject, progress, 2);

            // ensure project exists
            var targetProjects = provisionProjectsForTarget(targets, progress, 20);

            // remove no longer needed projects
            removeObsoleteProjects(targetProjects, progress, 1);

            // after provisioning and cleanup we go over the projects a second time to initialize the classpaths
            initializeClasspaths(targetProjects, workspace, progress, 40);

            // last but not least we call any sync participants
            callSynParticipants(targetProjects, progress, 1);

            // required per spec to finish the span properly
            progress.done();

            // broadcast & log sync metrics
            var duration = trace.done();
            var start = trace.getCreated();
            var projectsCount = targetProjects.size();
            var targetsCount = targets.size();
            var targetDiscoveryStrategyName = getTargetDiscoveryStrategyName();
            var targetProvisioningStrategyName = getTargetProvisioningStrategyName();
            safePostEvent(
                new SyncFinishedEvent(
                        workspaceRoot,
                        start,
                        duration,
                        "ok",
                        projectsCount,
                        targetsCount,
                        targetDiscoveryStrategyName,
                        targetProvisioningStrategyName,
                        trace));
            logSyncStats(workspaceName, duration, projectsCount, targetsCount, trace);

            return Status.OK_STATUS;
        } catch (OperationCanceledException e) {
            // broadcast sync metrics
            var start = trace.getCreated();
            var duration = Duration.between(start, Instant.now());
            safePostEvent(new SyncFinishedEvent(workspace.getLocation(), start, duration, "cancelled"));
            LOG.warn("Workspace synchronization cancelled: {}", workspace.getLocation(), e);
            return Status.CANCEL_STATUS;
        } catch (Exception e) {
            // broadcast sync metrics
            var start = trace.getCreated();
            var duration = Duration.between(start, Instant.now());
            safePostEvent(new SyncFinishedEvent(workspace.getLocation(), start, duration, "Failed: " + e.getMessage()));
            LOG.error("Error synchronizing workspace '{}': {}", workspace.getLocation(), e.getMessage(), e);
            return e instanceof CoreException ce ? ce.getStatus()
                    : Status.error(format("Error synchronizing workspace '%s'", workspace.getLocation()), e);
        } finally {
            // resume cache invalidation
            workspace.getModelManager().getResourceChangeProcessor().resumeInvalidationFor(workspace);

            // stop tracing
            trace.done();
            setCurrentTrace(null);

            // finish outer monitor
            IProgressMonitor.done(monitor);
        }
    }

    private void safePostEvent(SyncFinishedEvent syncFinishedEvent) {
        try {
            getEventAdmin().postEvent(syncFinishedEvent.toEvent());
        } catch (RuntimeException | AssertionError | LinkageError e) {
            LOG.error(
                "Unable to post event. OSGi Event Admin does not seem to be available. Please check the deployment.",
                e);
        }
    }
}
