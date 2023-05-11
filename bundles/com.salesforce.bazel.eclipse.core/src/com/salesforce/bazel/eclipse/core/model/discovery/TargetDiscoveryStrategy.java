package com.salesforce.bazel.eclipse.core.model.discovery;

import java.util.Collection;

import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import com.salesforce.bazel.eclipse.core.model.BazelPackage;
import com.salesforce.bazel.eclipse.core.model.BazelTarget;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;

/**
 * The target discovery strategy is responsible for identifying targets to be materialized into Eclipse.
 * <p>
 * Implementors should not persist state in instances of this class. It's expected that any method invocation is
 * hermetic and idempotent. The objects maybe short lived.
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
     *
     * @param bazelWorkspace
     *            the Bazel workspace
     * @param progress
     *            a monitor for tracking progress and observing cancellations (never <code>null</code>)
     * @return the found packages (never <code>null</code>)
     */
    Collection<BazelPackage> discoverPackages(BazelWorkspace bazelWorkspace, IProgressMonitor progress)
            throws CoreException;

    /**
     * Obtains all interesting targets for a given {@link BazelPackage}.
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
     * @param bazelPackage
     *            the Bazel package
     * @param progress
     *            a monitor for tracking progress and observing cancellations (never <code>null</code>)
     * @return the found targets (never <code>null</code>)
     */
    Collection<BazelTarget> discoverTargets(BazelPackage bazelPackage, IProgressMonitor progress) throws CoreException;
}
