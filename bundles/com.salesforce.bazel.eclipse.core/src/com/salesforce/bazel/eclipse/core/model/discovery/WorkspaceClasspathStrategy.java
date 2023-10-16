/*-
 *
 */
package com.salesforce.bazel.eclipse.core.model.discovery;

import static com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants.BUILDPATH_PROBLEM_MARKER;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;

import com.salesforce.bazel.eclipse.core.classpath.BazelClasspathScope;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.libs.ExternalLibrariesDiscovery;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.libs.GeneratedLibrariesDiscovery;

/**
 * This strategy implements computation of the {@link BazelWorkspace workspace project's} classpath.
 * <p>
 * In contrast to other projects the workspace project itself does not allow real development. Instead we use it for
 * discovering all imported repositories and making them available to Eclipse for global search and discovery.
 * </p>
 */
public class WorkspaceClasspathStrategy extends BaseProvisioningStrategy {

    /** Build Path related Bazel problem */
    String WORKSPACE_BUILDPATH_PROBLEM_MARKER = BUILDPATH_PROBLEM_MARKER + ".workspace_container";

    /**
     * Computes the classpath for the workspace project.
     * <p>
     * Calling this with a project other then the workspace project ({@link BazelProject#isWorkspaceProject()} must
     * return <code>true</code>) leads to unpredictable result.
     * </p>
     *
     * @param workspaceProject
     *            the workspace project
     * @param workspace
     *            the workspace
     * @param scope
     *            the requested classpath code
     * @param monitor
     *            monitor for checking progress and cancellation
     * @return the computed classpath
     * @throws CoreException
     */
    public Collection<ClasspathEntry> computeClasspath(BazelProject workspaceProject, BazelWorkspace bazelWorkspace,
            BazelClasspathScope scope, IProgressMonitor progress) throws CoreException {
        try {
            var monitor = SubMonitor.convert(progress);
            monitor.beginTask("Scanning for workspace jars", 2);

            // clean-up any markers created previously on the workspace project
            workspaceProject.getProject().deleteMarkers(WORKSPACE_BUILDPATH_PROBLEM_MARKER, false, 0);

            if (!bazelWorkspace.getBazelProjectView().discoverAllExternalAndWorkspaceJars()) {
                // discovery is disabled
                return Collections.emptyList();
            }

            List<ClasspathEntry> result = new ArrayList<>();

            var externalLibrariesDiscovery = new ExternalLibrariesDiscovery(bazelWorkspace);
            result.addAll(externalLibrariesDiscovery.query(monitor.split(1, SubMonitor.SUPPRESS_NONE)));
            if (externalLibrariesDiscovery.isFoundMissingJars()) {
                createMarker(
                    workspaceProject.getProject(),
                    WORKSPACE_BUILDPATH_PROBLEM_MARKER,
                    Status.warning(
                        "Some external jars were ommitted from the classpath because they don't exist locally. Consider runing 'bazel fetch //...' to download any missing library."));
            }

            var generatedLibrariesDiscovery = new GeneratedLibrariesDiscovery(bazelWorkspace);
            result.addAll(generatedLibrariesDiscovery.query(monitor.split(1, SubMonitor.SUPPRESS_NONE)));
            if (generatedLibrariesDiscovery.isFoundMissingJars()) {
                createMarker(
                    workspaceProject.getProject(),
                    WORKSPACE_BUILDPATH_PROBLEM_MARKER,
                    Status.warning(
                        "Some generated jars were ommitted from the classpath because they don't exist locally. Consider runing 'bazel build //...' to build any missing library."));
            } else if (generatedLibrariesDiscovery.isFoundMissingSrcJars()) {
                createMarker(
                    workspaceProject.getProject(),
                    WORKSPACE_BUILDPATH_PROBLEM_MARKER,
                    Status.warning(
                        "Some source jars are missing. Bazel does not build them by default. Consider runing 'bazel build  --output_groups=+_source_jars //...' to build any missing source jar."));
            }

            return result;
        } finally {
            progress.done();
        }
    }

    @Override
    public Map<BazelProject, Collection<ClasspathEntry>> computeClasspaths(Collection<BazelProject> bazelProjects,
            BazelWorkspace workspace, BazelClasspathScope scope, IProgressMonitor monitor) throws CoreException {
        if (bazelProjects.size() != 1) {
            throw new IllegalArgumentException("This strategy must only be used for the BazelWorkspace project!");
        }
        var workspaceProject = bazelProjects.iterator().next();
        return Map.of(workspaceProject, computeClasspath(workspaceProject, workspace, scope, monitor));
    }

    @Override
    protected List<BazelProject> doProvisionProjects(Collection<BazelTarget> targets, SubMonitor monitor)
            throws CoreException {
        throw new IllegalStateException("this method must not be called");
    }

}
