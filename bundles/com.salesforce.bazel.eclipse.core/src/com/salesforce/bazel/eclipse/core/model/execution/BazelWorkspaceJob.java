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

import static com.salesforce.bazel.eclipse.core.model.execution.TaskNameHelper.getTaskName;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.JobGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.command.BazelCommand;
import com.salesforce.bazel.sdk.command.BazelCommandExecutor;

class BazelWorkspaceJob<R> extends WorkspaceJob {

    private static Logger LOG = LoggerFactory.getLogger(BazelWorkspaceJob.class);

    private final BazelCommandExecutor executor;
    private final BazelCommand<R> command;
    private final CompletableFuture<R> resultFuture;

    private final List<IResource> resourcesToRefresh;

    public BazelWorkspaceJob(BazelCommandExecutor executor, BazelCommand<R> command, JobGroup jobGroup,
            ISchedulingRule rule, List<IResource> resourcesToRefresh, CompletableFuture<R> resultFuture) {
        super(getTaskName(command));
        this.executor = executor;
        this.command = command;
        this.resourcesToRefresh = resourcesToRefresh;
        this.resultFuture = resultFuture;
        setPriority(LONG);
        setUser(true);
        setJobGroup(jobGroup);
        setRule(requireNonNull(rule, "This job needs a scheduling rule. It shold probably be the workspace root!"));
    }

    private void refreshResources(SubMonitor subMonitor) {
        subMonitor.beginTask("Refreshing resources", resourcesToRefresh.size());
        for (IResource resource : resourcesToRefresh) {
            try {
                resource.refreshLocal(IResource.DEPTH_INFINITE, subMonitor.newChild(1));
            } catch (CoreException e) {
                // ignore (might have been deleted?)
                LOG.debug("Ignoring error during refresh of {}", resource, e);
            }
        }
    }

    @Override
    public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
        var subMonitor = SubMonitor.convert(monitor, getTaskName(command), IProgressMonitor.UNKNOWN);
        try {
            if (command.getPurpose() != null) {
                subMonitor.subTask(command.getPurpose());
            }
            var result = executor.execute(command, monitor::isCanceled);
            refreshResources(subMonitor.newChild(1));
            resultFuture.complete(result);
        } catch (RuntimeException | IOException e) {
            try {
                refreshResources(subMonitor.newChild(1));
            } finally {
                resultFuture.completeExceptionally(e);
            }
            return Status.CANCEL_STATUS;
        } finally {
            monitor.done();
        }
        return Status.OK_STATUS;
    }
}