package com.salesforce.bazel.eclipse.core.model.discovery;

import java.util.Collection;

import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.SynchronizeProjectViewJob;

/**
 * The target discovery strategy is responsible for identifying targets to be materialized into Eclipse.
 * <p>
 * Implementors may persist state in instances of this class. However, it's expected that the overall use of the
 * strategy is consistent. The strategy objects are generally short lived, i.e. they will not be re-used across
 * different synchronization/discovery cycles.
 * </p>
 */
public interface TargetDiscoveryStrategy {

    /**
     * Obtains all interesting packages for a given {@link BazelWorkspace}.
     * <p>
     * This method is guaranteed to be called within {@link IWorkspaceRoot a workspace level lock} and allowed to modify
     * workspace resources. Implementors should therefore not schedule any conflicting background jobs/threads which may
     * want to obtain resource level locking. This is likely going to cause deadlocks.
     * </p>
     * <p>
     * This method will be called at most once per {@link TargetDiscoveryStrategy} object instance. Implementors should
     * ensure to release any resources when this method returns to allow proper garbage collection.
     * </p>
     * <p>
     * This method is typically called by {@link SynchronizeProjectViewJob} before
     * {@link #discoverTargets(BazelWorkspace, Collection, IProgressMonitor)} is called. However, the list of packages
     * may be further reduced by the caller. Therefore implementors must not expect the return value to be passed to
     * {@link #discoverTargets(BazelWorkspace, Collection, IProgressMonitor)}.
     * </p>
     *
     * @param bazelWorkspace
     *            the Bazel workspace (never <code>null</code>)
     * @param progress
     *            a monitor for tracking progress and observing cancellations (never <code>null</code>); implementors do
     *            not need to call {@link IProgressMonitor#done()}
     * @return the found packages (never <code>null</code>)
     */
    Collection<WorkspacePath> discoverPackages(BazelWorkspace bazelWorkspace, IProgressMonitor progress)
            throws CoreException;

    /**
     * Obtains all interesting targets for a given collection of {@link BazelPackage packages}.
     * <p>
     * This method is guaranteed to be called within {@link IWorkspaceRoot a workspace level lock} and allowed to modify
     * workspace resources. Implementors should therefore not schedule any conflicting background jobs/threads which may
     * want to obtain resource level locking. This is likely going to cause deadlocks.
     * </p>
     * <p>
     * This method will be called at most once per {@link TargetDiscoveryStrategy} object instance. Implementors should
     * ensure to release any resources when this method returns to allow proper garbage collection.
     * </p>
     *
     * @param bazelWorkspace
     *            the workspace the packages belong to (never <code>null</code>)
     * @param bazelPackages
     *            the collection of Bazel packages to obtain targets for (never <code>null</code>)
     * @param progress
     *            a monitor for tracking progress and observing cancellations (never <code>null</code>); implementors do
     *            not need to call {@link IProgressMonitor#done()}
     *
     * @return the found targets (never <code>null</code>)
     */
    Collection<TargetExpression> discoverTargets(BazelWorkspace bazelWorkspace, Collection<WorkspacePath> bazelPackages,
            IProgressMonitor progress) throws CoreException;
}
