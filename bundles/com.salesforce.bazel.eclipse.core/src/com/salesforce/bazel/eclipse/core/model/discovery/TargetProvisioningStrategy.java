package com.salesforce.bazel.eclipse.core.model.discovery;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.IClasspathEntry;

import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.salesforce.bazel.eclipse.core.classpath.BazelClasspathScope;
import com.salesforce.bazel.eclipse.core.classpath.CompileAndRuntimeClasspath;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.SynchronizeProjectViewJob;

/**
 * The target discovery strategy is responsible for provisioning targets into Eclipse as Eclipse projects.
 * <p>
 * Implementors must not expect state in instances of this class persisted for a longer duration. It's expected that any
 * method invocation is hermetic and idempotent. However, object instances of this class are short lived and never
 * re-used for multiple method invocations. Thus, caching within the boundary of a method call is acceptable.
 * </p>
 */
public interface TargetProvisioningStrategy {

    /**
     * Computes the classpath for a list of projects to be used by the Bazel classpath container entry.
     * <p>
     * In the Bazel Eclipse extension classpath computation is coupled with project provisioning. The implementation
     * will potentially execute Bazel commands to compute the classpath.
     * </p>
     * <p>
     * This operation is called outside of project provisioning, i.e. implementors can assume that the Eclipse workspace
     * is fully provisioned. Therefore, the computed classpath only represents dependencies of the specified projects
     * (either {@link IClasspathEntry#CPE_LIBRARY} or {@link IClasspathEntry#CPE_PROJECT}). Any source folder is
     * expected to be calculated and setup properly as part of a call to
     * {@link #provisionProjectsForSelectedTargets(Collection, BazelWorkspace, IProgressMonitor)} during project
     * provisioning. Implementors are expected to properly connect dependencies to projects provisioned in the Eclipse
     * workspace, i.e. use {@link IClasspathEntry#CPE_PROJECT} when a dependency exists as target in the workspace.
     * </p>
     * <p>
     * This method is guaranteed to be called with a collection of projects belonging to the same
     * {@link BazelWorkspace}. Calling this method with a list of projects belonging to different workspaces is an error
     * and will produce unexpected results. Implementors are allowed to assume all given projects share a single
     * {@link BazelWorkspace}.
     * </p>
     * <p>
     * This method is guaranteed to be called within {@link IWorkspaceRoot a workspace level lock} and allowed to modify
     * workspace resources. Implementors should therefore not schedule any conflicting background jobs/threads which may
     * want to obtain resource level locking. This is likely going to cause deadlocks.
     * </p>
     * <p>
     * If classpath computation fails implementors are required to fail execution with a {@link CoreException}. If
     * {@link CoreException#getStatus() the exception status} is {@link IStatus#isMultiStatus() a multi-status} then the
     * children will be reported as classpath error markers on the workspace project. In general, implementor is
     * expected to create more detailed error markers when applicable.
     * </p>
     * <p>
     * This method will be called at most once per {@link TargetProvisioningStrategy} object instance.
     * </p>
     *
     * @param bazelProjects
     *            the list of project to obtain the classpath for (never <code>null</code>)
     * @param workspace
     *            the workspace all projects belong to.
     * @param scope
     *            the classpath scope (never <code>null</code>)
     * @param workspace
     *            the workspace all targets belong to (never <code>null</code>)
     * @param progress
     *            a monitor for tracking progress and observing cancellations (never <code>null</code>)
     * @return the computed classpath entries by project (never <code>null</code>)
     * @throws CoreException
     *             in case of problems computing the classpath
     */
    Map<BazelProject, CompileAndRuntimeClasspath> computeClasspaths(Collection<BazelProject> bazelProjects,
            BazelWorkspace workspace, BazelClasspathScope scope, IProgressMonitor monitor) throws CoreException;

    /**
     * Provisions projects in Eclipse for a collection of targets to materialize for a workspace.
     * <p>
     * The provisioning operation will convert the given targets into a list of {@link BazelProject Bazel projects}.
     * When this method completes, the underlying resources are guaranteed to exist in the Eclipse workspace and the
     * projects are fully functional.
     * </p>
     * <p>
     * It's up to the discretion of the strategy whether multiple targets are merged into a single projects or one
     * project is created for each target. It might also be realistic that multiple projects are created for a single
     * target if deemed necessary by the strategy.
     * </p>
     * <p>
     * This method is called from the {@link SynchronizeProjectViewJob}. The strategy needs to detect existing projects.
     * Re-use of such projects is recommended. It is also recommended to support project renames in the IDE done by
     * users, i.e. implementations must not rely on a project name scheme to discover existing previously provisioned
     * projects. Obsolete projects don't need to be deleted. The calling {@link SynchronizeProjectViewJob} has logic to
     * remove projects previously created for a workspace, which are no longer needed.
     * </p>
     * <p>
     * This method is guaranteed to be called with a collection of targets belonging to the same {@link BazelWorkspace}.
     * Calling this method with a list of targets belonging to different workspaces is an error and will produce
     * unexpected results. Implementors are allowed to assume all given targets share a single {@link BazelWorkspace}.
     * </p>
     * <p>
     * When this method is called the {@link IProject project} for the {@link BazelWorkspace} has already been
     * provisioned. It must not be included in the returned list of provisioned targets. However, any project (whether
     * it was created or already existed) provisioned by this implementation must be returned. Otherwise it may be
     * removed by the ongoing synchronization.
     * </p>
     * <p>
     * This method is guaranteed to be called within {@link IWorkspaceRoot a workspace level lock} and allowed to modify
     * workspace resources. Implementors should therefore not schedule any conflicting background jobs/threads which may
     * want to obtain resource level locking. This is likely going to cause deadlocks.
     * </p>
     * <p>
     * This method will be called at most once per {@link TargetProvisioningStrategy} object instance.
     * </p>
     *
     * @param targets
     *            the targets to provision (never <code>null</code>)
     * @param workspace
     *            the workspace all targets belong to (never <code>null</code>)
     * @param progress
     *            a monitor for tracking progress and observing cancellations (never <code>null</code>)
     * @return a list of provisioned projects (never <code>null</code>)
     */
    List<BazelProject> provisionProjectsForSelectedTargets(Collection<TargetExpression> targets,
            BazelWorkspace workspace, IProgressMonitor progress) throws CoreException;
}
