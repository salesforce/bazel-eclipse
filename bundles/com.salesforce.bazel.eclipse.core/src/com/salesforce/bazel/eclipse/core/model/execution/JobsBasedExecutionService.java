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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.JobGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.model.BazelElement;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.sdk.command.BazelCommand;
import com.salesforce.bazel.sdk.command.BazelCommandExecutor;

/**
 * Implementation of {@link BazelModelCommandExecutionService} using Eclipse Jobs API.
 */
public class JobsBasedExecutionService implements BazelModelCommandExecutionService {

    private static Logger LOG = LoggerFactory.getLogger(JobsBasedExecutionService.class);
    private final BazelCommandExecutor executor;

    private final ConcurrentMap<BazelWorkspace, JobGroup> jobGroupsByWorkspace = new ConcurrentHashMap<>();

    public JobsBasedExecutionService(BazelCommandExecutor executor) {
        this.executor = executor;
    }

    @Override
    public <R> Future<R> executeOutsideWorkspaceLockAsync(BazelCommand<R> command,
            BazelElement<?, ?> executionContext) {
        var future = new WorkspaceLockDetectingFuture<R>();
        new BazelReadOnlyJob<>(executor, command, getJobGroup(executionContext), future).schedule();
        return future;
    }

    @Override
    public <R> R executeWithWorkspaceLock(BazelCommand<R> command, BazelElement<?, ?> executionContext,
            List<IResource> resourcesToRefresh, IProgressMonitor monitor) throws CoreException {
        var result = new AtomicReference<R>();
        ResourcesPlugin.getWorkspace().run(pm -> {
            var subMonitor = SubMonitor.convert(pm);
            try {
                subMonitor.beginTask(command.toString(), IProgressMonitor.UNKNOWN);
                result.set(executor.execute(command, pm::isCanceled));
            } catch (IOException e) {
                throw new CoreException(Status.error("Error executing command: " + e.getMessage(), e));
            } finally {
                try {
                    refreshResources(resourcesToRefresh, subMonitor.newChild(1));
                } finally {
                    pm.done();
                }
            }
        }, monitor);
        return result.get();
    }

    @Override
    public <R> Future<R> executeWithWorkspaceLockAsync(BazelCommand<R> command, BazelElement<?, ?> executionContext,
            ISchedulingRule rule, List<IResource> resourcesToRefresh) {
        var future = new WorkspaceLockDetectingFuture<R>();
        new BazelWorkspaceJob<>(executor, command, getJobGroup(executionContext), rule, resourcesToRefresh, future)
                .schedule();
        return future;
    }

    JobGroup getJobGroup(BazelElement<?, ?> executionContext) {
        var bazelWorkspace = executionContext.getBazelWorkspace();
        if (bazelWorkspace == null) {
            return null; // don't use a job group for the model
        }

        // quick cleanup of empty/no longer needed groups
        jobGroupsByWorkspace.entrySet().removeIf(e -> {
            try {
                return !e.getKey().exists() && e.getValue().getActiveJobs().isEmpty();
            } catch (IOException ioe) {
                // keep in list
                return false;
            }
        });

        return jobGroupsByWorkspace.computeIfAbsent(bazelWorkspace,
            w -> new JobGroup(w.getLocation().toString(), 2, 1));
    }

    void refreshResources(List<IResource> resourcesToRefresh, SubMonitor subMonitor) {
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

}
