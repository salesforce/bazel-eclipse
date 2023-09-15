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

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobGroup;

import com.salesforce.bazel.sdk.command.BazelCommand;
import com.salesforce.bazel.sdk.command.BazelCommandExecutor;

class BazelReadOnlyJob<R> extends Job {

    private final BazelCommandExecutor executor;
    private final BazelCommand<R> command;
    private final CompletableFuture<R> resultFuture;

    public BazelReadOnlyJob(BazelCommandExecutor executor, BazelCommand<R> command, JobGroup jobGroup,
            CompletableFuture<R> resultFuture) {
        super(command.toString());
        this.executor = executor;
        this.command = command;
        this.resultFuture = resultFuture;
        setPriority(LONG);
        setUser(true);
        setJobGroup(jobGroup);
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        try {
            monitor.beginTask(getTaskName(command), IProgressMonitor.UNKNOWN);
            var result = executor.execute(command, monitor::isCanceled);
            resultFuture.complete(result);
        } catch (RuntimeException | IOException e) {
            resultFuture.completeExceptionally(e);
            return Status.CANCEL_STATUS;
        } finally {
            monitor.done();
        }
        return Status.OK_STATUS;
    }

}