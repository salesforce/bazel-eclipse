package com.salesforce.bazel.eclipse.core.model;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.model.execution.BazelModelCommandExecutionService;
import com.salesforce.bazel.sdk.command.BazelBinary;
import com.salesforce.bazel.sdk.command.BazelCommand;
import com.salesforce.bazel.sdk.command.BazelQueryCommand;

/**
 * A helper for executing commands with the {@link BazelModelCommandExecutionService} in the context of a
 * {@link BazelElement}.
 * <p>
 * The helper does not implement any command execution but provides convenience API for the Bazel model. It's important
 * that callers read and understand the locking and execution semantics of {@link BazelModelCommandExecutionService}.
 * </p>
 *
 * @see BazelModelCommandExecutionService
 */
public class BazelElementCommandExecutor {

    private static Logger LOG = LoggerFactory.getLogger(BazelElementCommandExecutor.class);

    private final BazelElement<?, ?> executionContext;

    public BazelElementCommandExecutor(BazelElement<?, ?> executionContext) {
        this.executionContext = requireNonNull(executionContext, "execution context is missing");
    }

    /**
     * Applies any Bazel workspace specific configuration to the command binary.
     * <p>
     * By default we have an Eclipse wide preference setting providing the Eclipse wide Bazel binary to use. However, a
     * Bazel workspace may use a different Bazel version (eg., via <code>.bazelversion</code> file or
     * <code>.bazelproject</code> project view).
     * </p>
     * <p>
     * We rely on a single, system wide Bazel binary (eg., Bazelisk or Bazel shell wrapper script) to resolve the
     * version. Therefore we only tweak the detected version if necessary.
     * </p>
     *
     * @param command
     *            the Bazel command
     * @param bazelWorkspace
     *            the workspace to use
     * @throws CoreException
     */
    private void configureCommand(BazelCommand<?> command, BazelWorkspace bazelWorkspace) throws CoreException {
        if (command.getBazelBinary() != null) {
            LOG.trace("Command '{}' already has a binary: {}", command, command.getBazelBinary());
            return;
        }

        var bazelBinary = bazelWorkspace.getBazelBinary();
        if (bazelBinary == null) {
            bazelBinary = getExecutionService().getBazelBinary();
        } else {
            LOG.trace("Using binary from workspace: {}", bazelBinary);
        }
        var workspaceBazelVersion = bazelWorkspace.getBazelVersion();
        if (!bazelBinary.bazelVersion().equals(workspaceBazelVersion)) {
            LOG.trace(
                "Forcing (overriding) workspace Bazel version '{}' for command: {}",
                workspaceBazelVersion,
                command);
            command.setBazelBinary(new BazelBinary(bazelBinary.executable(), workspaceBazelVersion));
        } else {
            command.setBazelBinary(bazelBinary);
        }

        var buildFlags = bazelWorkspace.getBazelProjectView().buildFlags();
        if (!buildFlags.isEmpty()) {
            command.addCommandArgs(buildFlags);
        }
    }

    final BazelModelCommandExecutionService getExecutionService() {
        return executionContext.getModel().getModelManager().getExecutionService();
    }

    /**
     * Execute a Bazel build/test using
     * {@link BazelModelCommandExecutionService#executeWithinExistingWorkspaceLock(BazelCommand, BazelElement, java.util.List, org.eclipse.core.runtime.IProgressMonitor)}.
     * <p>
     * The method will execute the command directly in the same thread. Progress reporting will happen to the provided
     * progress monitor. The workspace will be locked.
     * </p>
     * <p>
     * Be careful with <code>bazel run</code>. If this is very long running (eg., an application/server) no other
     * modifications can be done in Eclipse while <code>bazel run</code> did not finish.
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
     * @return the command result (never <code>null</code>)
     * @see BazelModelCommandExecutionService#executeWithinExistingWorkspaceLock(BazelCommand, BazelElement,
     *      java.util.List, org.eclipse.core.runtime.IProgressMonitor) <code>executeWithWorkspaceLock</code> for
     *      execution and locking semantics
     * @see IWorkspace#run(org.eclipse.core.runtime.ICoreRunnable, ISchedulingRule, int, IProgressMonitor)
     * @throws CoreException
     */
    public <R> R runDirectlyWithinExistingWorkspaceLock(BazelCommand<R> command, List<IResource> resourcesToRefresh,
            IProgressMonitor monitor) throws CoreException {
        configureCommand(command, executionContext.getBazelWorkspace());
        return getExecutionService()
                .executeWithinExistingWorkspaceLock(command, executionContext, resourcesToRefresh, monitor);
    }

    /**
     * Execute a Bazel query command using
     * {@link BazelModelCommandExecutionService#executeOutsideWorkspaceLockAsync(BazelCommand, BazelElement)}.
     * <p>
     * The method will block the current thread and wait for the result. However, the command execution will happen in a
     * different {@link Job thread} in the background for proper progress handling/reporting.
     * </p>
     * <p>
     * Note, the command must not modify any resources in the workspace (eg., performing a build or something).
     * </p>
     *
     * @param <R>
     *            the command result type
     * @param command
     *            the command to execute
     * @return the command result (never <code>null</code>)
     * @see BazelModelCommandExecutionService#executeOutsideWorkspaceLockAsync(BazelCommand, BazelElement)
     *      <code>executeOutsideWorkspaceLockAsync</code> for execution and locking semantics
     * @throws CoreException
     */
    public <R> R runQueryWithoutLock(BazelQueryCommand<R> command) throws CoreException {
        configureCommand(command, executionContext.getBazelWorkspace());
        Future<R> future = getExecutionService().executeOutsideWorkspaceLockAsync(command, executionContext);
        try {
            return future.get();
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause != null) {
                throw new CoreException(toStatus(cause));
            }

            throw new CoreException(toStatus(e));
        } catch (InterruptedException e) {
            throw new OperationCanceledException("Interrupted while waiting for bazel cquery output to complete.");
        }
    }

    /**
     * Execute a Bazel command using
     * {@link BazelModelCommandExecutionService#executeOutsideWorkspaceLockAsync(BazelCommand, BazelElement)}.
     * <p>
     * The method will block the current thread and wait for the result. However, the command execution will happen in a
     * different {@link Job thread} in the background for proper progress handling/reporting.
     * </p>
     *
     * @param <R>
     *            the command result type
     * @param command
     *            the command to execute
     * @param rule
     *            the scheduling rule to apply to {@link WorkspaceJob#setRule(ISchedulingRule)}
     * @param resourcesToRefresh
     *            list of resources to refresh recursively when the command execution is complete
     * @return the command result (never <code>null</code>)
     * @see BazelModelCommandExecutionService#executeWithWorkspaceLockAsync(BazelCommand, BazelElement, ISchedulingRule,
     *      List) <code>executeWithWorkspaceLockAsync</code> for execution and locking semantics
     * @throws CoreException
     */
    public <R> R runWithWorkspaceLock(BazelCommand<R> command, ISchedulingRule rule, List<IResource> resourcesToRefresh)
            throws CoreException {
        configureCommand(command, executionContext.getBazelWorkspace());
        Future<R> future = getExecutionService()
                .executeWithWorkspaceLockAsync(command, executionContext, rule, resourcesToRefresh);
        try {
            return future.get();
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause != null) {
                throw new CoreException(toStatus(cause));
            }

            throw new CoreException(toStatus(e));
        } catch (InterruptedException e) {
            throw new OperationCanceledException("Interrupted while waiting for bazel output to complete.");
        }
    }

    private IStatus toStatus(Throwable e) {
        return Status.error(format("%s: %s", executionContext.getName(), e.getMessage()), e);
    }
}
