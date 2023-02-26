package com.salesforce.bazel.eclipse.core.model.discovery;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.SynchronizeProjectViewJob;

/**
 * The target discovery strategy is responsible for provisioning targets into Eclipse as Eclipse projects.
 * <p>
 * Implementors should not persist state in instances of this class. It's expected that any method invocation is
 * hermetic and idempotent. The objects maybe short lived.
 * </p>
 */
public interface TargetProvisioningStrategy {

    /**
     * Provisions project for a collection of targets to materialize for a workspace.
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
     * This method is guaranteed to be called within {@link IWorkspaceRoot a workspace level lock} and allowed to modify
     * workspace resources. Implementors should therefore not schedule any conflicting background jobs/threads which may
     * want to obtain resource level locking. This is likely going to cause deadlocks.
     * </p>
     * <p>
     * This method will be called at most once per {@link TargetProvisioningStrategy} object instance. Implementors
     * should ensure to release any resources when this method returns to allow proper garbage collection.
     * </p>
     *
     * @param targets
     *            the targets to provision (never <code>null</code>)
     * @param progress
     *            a monitor for tracking progress and observing cancellations (never <code>null</code>)
     * @return a list of provisioned projects or <code>null</code> if not project should be provisioned for the given
     *         target
     */
    List<BazelProject> provisionProjectsForTarget(Collection<BazelTarget> targets, IProgressMonitor progress)
            throws CoreException;

}
