package com.salesforce.bazel.eclipse.core.classpath;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BAZEL_NATURE_ID;
import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static org.eclipse.core.runtime.SubMonitor.SUPPRESS_NONE;
import static org.eclipse.core.runtime.SubMonitor.convert;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

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
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.JavaCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.model.BazelModelManager;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;

/**
 * A {@link WorkspaceJob} for refreshing the classpath of Bazel projects.
 * <p>
 * This job implementation ensures that only one refresh happens at a time.
 * </p>
 */
public final class InitializeOrRefreshClasspathJob extends WorkspaceJob {

    private static Logger LOG = LoggerFactory.getLogger(InitializeOrRefreshClasspathJob.class);

    static boolean isBazelProject(IProject p) {
        try {
            return p.isAccessible() && p.hasNature(BAZEL_NATURE_ID);
        } catch (CoreException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error accessing project {}", p, e);
            }
            // exclude
            return false;
        }
    }

    private final Map<BazelWorkspace, List<BazelProject>> bazelProjects;
    private final BazelClasspathManager classpathManager;

    private final boolean forceRefresh;

    /**
     * Create a new job instance
     *
     * @param projects
     *            the list of Eclipse projects to refresh
     * @param classpathManager
     *            the classpath manager instance
     * @param forceRefresh
     *            <code>true</code> if a refresh should be forced, <code>false</code> if the classpath should only be
     *            computed when it's missing (no previously persisted state)
     */
    public InitializeOrRefreshClasspathJob(Collection<IProject> projects, BazelClasspathManager classpathManager,
            boolean forceRefresh) {
        this(projects.stream().filter(InitializeOrRefreshClasspathJob::isBazelProject).map(BazelCore::create),
                classpathManager,
                forceRefresh);
    }

    /**
     * Create a new job instance
     *
     * @param projects
     *            the list of Bazel projects to refresh
     * @param classpathManager
     *            the classpath manager instance
     * @param forceRefresh
     *            <code>true</code> if a refresh should be forced, <code>false</code> if the classpath should only be
     *            computed when it's missing (no previously persisted state)
     */
    public InitializeOrRefreshClasspathJob(Stream<BazelProject> projects, BazelClasspathManager classpathManager,
            boolean forceRefresh) {
        super("Computing build path of Bazel projects");
        // store those first for needsRefresh(..)
        this.classpathManager = classpathManager;
        this.forceRefresh = forceRefresh;
        // group by workspace
        this.bazelProjects = projects.filter(this::needsRefresh).collect(groupingBy(p -> {
            try {
                return p.getBazelWorkspace();
            } catch (CoreException e) {
                throw new IllegalStateException(format("Invalid project '%s': %s", p, e.getMessage()), e);
            }
        }));
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

    boolean needsRefresh(BazelProject p) {
        if (forceRefresh) {
            return true;
        }
        var containerEntry = BazelClasspathHelpers.getBazelContainerEntry(JavaCore.create(p.getProject()));
        try {
            return (containerEntry != null) && (getClasspathManager().getSavedContainer(p.getProject()) == null);
        } catch (CoreException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error accessing saved container for project {}", p, e);
            }
            return true;
        }
    }

    @Override
    public IStatus runInWorkspace(IProgressMonitor progress) throws CoreException {
        try {
            var monitor = convert(progress, "Computing Bazel Classpaths", bazelProjects.size());
            var status =
                    new MultiStatus(BazelModelManager.PLUGIN_ID, 0, "Some Bazel build paths could not be initialized.");

            nextProjectSet: for (Entry<BazelWorkspace, List<BazelProject>> projectSet : bazelProjects.entrySet()) {
                try {
                    // we only process projects with a valid workspace
                    if (projectSet.getKey() == null) {
                        continue nextProjectSet;
                    }

                    getClasspathManager().updateClasspath(
                        projectSet.getKey(),
                        projectSet.getValue(),
                        monitor.split(1, SUPPRESS_NONE));
                } catch (CoreException e) {
                    status.add(e.getStatus());

                    // create a marker so user is aware
                    var marker = projectSet.getKey()
                            .getBazelProject()
                            .getProject()
                            .createMarker(IJavaModelMarker.BUILDPATH_PROBLEM_MARKER);
                    marker.setAttributes( // @formatter:off
                        new String[] {
                            IMarker.MESSAGE,
                            IMarker.SEVERITY,
                            IMarker.LOCATION,
                            IMarker.SOURCE_ID,
                        },
                        new Object[] {
                            e.getStatus().getMessage(),
                            IMarker.SEVERITY_ERROR,
                            "Bazel BUILD",
                            BazelModelManager.MARKER_SOURCE_ID,
                        }
                    ); // @formatter:on
                }
                monitor.worked(1);
            }

            // return error if we have one!
            if (status.matches(IStatus.ERROR)) {
                return status;
            }
        } finally {
            if (progress != null) {
                progress.done();
            }
        }
        return Status.OK_STATUS;
    }
}