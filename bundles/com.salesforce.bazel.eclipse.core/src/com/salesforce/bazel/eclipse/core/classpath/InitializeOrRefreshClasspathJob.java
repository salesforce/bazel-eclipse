package com.salesforce.bazel.eclipse.core.classpath;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BAZEL_NATURE_ID;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceRuleFactory;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.JavaCore;

import com.salesforce.bazel.eclipse.core.model.BazelModelManager;

/**
 * A {@link WorkspaceJob} for refreshing the classpath of Bazel projects.
 * <p>
 * This job implementation ensures that only one refresh happens at a time.
 * </p>
 */
public final class InitializeOrRefreshClasspathJob extends WorkspaceJob {
    private final IProject[] projects;
    private final BazelClasspathManager classpathManager;
    private final boolean forceRefresh;

    /**
     * Create a new job instance
     *
     * @param projects
     *            the list of projects to refresh
     * @param classpathManager
     *            the classpath manager instance
     * @param forceRefresh
     *            <code>true</code> if a refresh should be forced, <code>false</code> if the classpath should only be
     *            computed when it's missing (no previously persisted state)
     */
    public InitializeOrRefreshClasspathJob(IProject[] projects, BazelClasspathManager classpathManager,
            boolean forceRefresh) {
        super("Computing build path of Bazel projects");
        this.projects = projects;
        this.classpathManager = classpathManager;
        this.forceRefresh = forceRefresh;
        setPriority(Job.BUILD); // process after others
        setRule(getRuleFactory().buildRule()); // ensure not build is running in parallel
    }

    @Override
    public boolean belongsTo(Object family) {
        return BazelModelManager.PLUGIN_ID.equals(family);
    }

    BazelClasspathManager getClasspathManager() {
        return classpathManager;
    }

    IResourceRuleFactory getRuleFactory() {
        return ResourcesPlugin.getWorkspace().getRuleFactory();
    }

    @Override
    public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
        try {
            var subMonitor = SubMonitor.convert(monitor, projects.length);
            var status =
                    new MultiStatus(BazelModelManager.PLUGIN_ID, 0, "Some Bazel build paths could not be initialized.");

            nextProject: for (IProject project : projects) {
                try {
                    // we only process Java projects
                    if (project.isOpen() && project.hasNature(JavaCore.NATURE_ID)
                            && project.hasNature(BAZEL_NATURE_ID)) {
                        var javaProject = JavaCore.create(project);
                        // ensure the project has a Bazel container
                        var containerEntry = BazelClasspathHelpers.getBazelContainerEntry(javaProject);
                        if (containerEntry != null) {
                            var savedContainer = forceRefresh ? null : getClasspathManager().getSavedContainer(project);
                            if (forceRefresh || (savedContainer == null)) {
                                // there is no saved container; i.e. this is a project setup/imported before the classpath container rework
                                // initialize the classpath
                                subMonitor.setTaskName(project.getName());
                                getClasspathManager().updateClasspath(javaProject, subMonitor.newChild(1));
                                continue nextProject;
                            }
                        }
                    }
                } catch (CoreException e) {
                    status.add(e.getStatus());

                    // create a marker to user is aware
                    var marker = project.createMarker(IJavaModelMarker.BUILDPATH_PROBLEM_MARKER);
                    marker.setAttributes( // @formatter:off
                        new String[] {
                            IMarker.MESSAGE,
                            IMarker.SEVERITY,
                            IMarker.LOCATION,
                            IMarker.SOURCE_ID,
                        },
                        new Object[] {
                            status.getMessage(),
                            IMarker.SEVERITY_ERROR,
                            "Bazel BUILD",
                            BazelModelManager.MARKER_SOURCE_ID,
                        }
                    ); // @formatter:on
                }
                subMonitor.worked(1);
            }

            // return error if we have one!
            if (status.matches(IStatus.ERROR)) {
                return status;
            }
        } finally {
            if (monitor != null) {
                monitor.done();
            }
        }
        return Status.OK_STATUS;
    }
}