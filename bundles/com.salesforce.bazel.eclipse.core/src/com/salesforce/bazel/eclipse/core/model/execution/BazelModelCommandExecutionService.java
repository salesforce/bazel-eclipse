/*-
 * Copyright (c) 2023 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - Partially adapted and heavily inspired from Eclipse JDT, M2E and PDE
 */
package com.salesforce.bazel.eclipse.core.model.execution;

import java.util.List;
import java.util.concurrent.Future;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceRuleFactory;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;

import com.salesforce.bazel.eclipse.core.model.BazelElement;
import com.salesforce.bazel.eclipse.core.model.BazelElementCommandExecutor;
import com.salesforce.bazel.eclipse.core.model.BazelModel;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.sdk.command.BazelBinary;
import com.salesforce.bazel.sdk.command.BazelCommand;

/**
 * A service for executing Bazel commands in an Eclipse intended way within the context of the Bazel model.
 * <p>
 * Interactions with Bazel require spawning processes for calling Bazel. This service ensures all those interactions
 * happen in a consistent way, which adheres to best practices in the Eclipse IDE. As such, no execution shall happen
 * directly in the calling thread. Instead a {@link Job} will be used to perform any execution in the background. The
 * jobs will ensure they never compete with each other and especially never conflict with ongoing build activity inside
 * Eclipse.
 * </p>
 * <p>
 * Note, callers should use this service very seldom. Instead the {@link BazelElementCommandExecutor} should be used via
 * {@link BazelElement#getCommandExecutor()}.
 * </p>
 *
 * @see BazelElementCommandExecutor
 */
public interface BazelModelCommandExecutionService {

    /**
     * Execute a read-only Bazel command asynchronously.
     * <p>
     * The command is expected to be read-only from an Eclipse resource perspective, no resources will be modified when
     * executing this command. This distinction is important from an Eclipse perspective to still allow scheduling
     * command executions properly in the background without potentially causing deadlocks because of required resource
     * locking.
     * </p>
     * <p>
     * The executor service will use the given Bazel element as its execution context. The execution context will either
     * be a {@link BazelWorkspace} or a {@link BazelModel} (global). This is only interesting for visualization of
     * command activity inside Eclipse. From an implementation perspective we are relying on Bazel's client lock in the
     * Bazel server process to prevent concurrent activity for a Bazel workspace.
     * </p>
     *
     * @param <R>
     *            the command result type
     * @param command
     *            the command to execute
     * @param executionContext
     *            the execution context for visualization purposes (may be any {@link BazelElement} or
     *            <code>null</code>)
     * @return a {@link Future} for obtaining the command result (never <code>null</code>)
     */
    <R> Future<R> executeOutsideWorkspaceLockAsync(BazelCommand<R> command, BazelElement<?, ?> executionContext)
            throws CoreException;

    /**
     * Execute a potentially resource modifying Bazel command directly, i.e. in this very same thread.
     * <p>
     * The command is allowed to modify Eclipse resources. As such a resource level lock is expected to be set already
     * by the caller.
     * </p>
     * <p>
     * The executor service will use the given Bazel element as its execution context. The execution context will either
     * be a {@link BazelWorkspace} or a {@link BazelModel} (global). This is only interesting for visualization of
     * command activity inside Eclipse. From an implementation perspective we are relying on Bazel's client lock in the
     * Bazel server process to prevent concurrent activity for a Bazel workspace.
     * </p>
     *
     * @param <R>
     *            the command result type
     * @param command
     *            the command to execute
     * @param executionContext
     *            the execution context for visualization purposes (may be any {@link BazelElement} or
     *            <code>null</code>)
     * @param resourcesToRefresh
     *            list of resources to refresh recursively when the command execution is complete
     * @param monitor
     *            the monitor to check for cancellation and to report progress (must not be <code>null</code>)
     * @param
     * @return the command result
     * @see IWorkspace#run(org.eclipse.core.runtime.ICoreRunnable, ISchedulingRule, int, IProgressMonitor)
     */
    <R> R executeWithWorkspaceLock(BazelCommand<R> command, BazelElement<?, ?> executionContext,
            List<IResource> resourcesToRefresh, IProgressMonitor monitor) throws CoreException;

    /**
     * Execute a potentially resource modifying Bazel command asynchronously.
     * <p>
     * The command is allowed to modify Eclipse resources. As such a resource level lock will be obtained by the
     * execution service before executing the command. The lock ensures that the workspace is locked for modification
     * and prevents concurring build activity.
     * </p>
     * <p>
     * This method should be used with care. Specifically it must not be used when the caller is already in possession
     * of a resource lock and intends to wait for the result of this execution. This will likely cause a deadlock.
     * </p>
     * <p>
     * The executor service will use the given Bazel element as its execution context. The execution context will either
     * be a {@link BazelWorkspace} or a {@link BazelModel} (global). This is only interesting for visualization of
     * command activity inside Eclipse. From an implementation perspective we are relying on Bazel's client lock in the
     * Bazel server process to prevent concurrent activity for a Bazel workspace.
     * </p>
     *
     * @param <R>
     *            the command result type
     * @param command
     *            the command to execute
     * @param executionContext
     *            the execution context for visualization purposes (may be any {@link BazelElement} or
     *            <code>null</code>)
     * @param rule
     *            the scheduling rule to apply to {@link WorkspaceJob#setRule(ISchedulingRule)}
     * @param resourcesToRefresh
     *            list of resources to refresh recursively when the command execution is complete
     * @return a {@link Future} for obtaining the command result
     * @see IResourceRuleFactory
     * @see ISchedulingRule
     * @see WorkspaceJob
     */
    <R> Future<R> executeWithWorkspaceLockAsync(BazelCommand<R> command, BazelElement<?, ?> executionContext,
            ISchedulingRule rule, List<IResource> resourcesToRefresh) throws CoreException;

    /**
     * {@return the current Bazel binary used by the command executor}
     *
     * @throws NullPointerException
     *             if the command executor has no Bazel binary
     */
    BazelBinary getBazelBinary();
}
