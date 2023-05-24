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

import static java.lang.String.format;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;

/**
 * This is an attempt to prevent programming mistakes.
 *
 * @param <R>
 */
@SuppressWarnings("restriction")
final class WorkspaceLockDetectingFuture<R> extends CompletableFuture<R> {

    public void ensureCurrentThreadDoesNotOwnWorkspaceLock() {
        var workspace = ResourcesPlugin.getWorkspace();
        try {
            if (((Workspace) workspace).getWorkManager().isLockAlreadyAcquired()) {
                throw new IllegalStateException(format(
                    "The current thread (%s) already owns the workspace lock. Calling get() will almost certainly cause a deadlock!",
                    Thread.currentThread()));
            }
        } catch (CoreException e) {
            throw new IllegalStateException("The workspace is shutdown!", e);
        }
    }

    @Override
    public R get() throws InterruptedException, ExecutionException {
        // check lock
        ensureCurrentThreadDoesNotOwnWorkspaceLock();

        // proceed
        return super.get();
    }

    @Override
    public R get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        // check lock
        ensureCurrentThreadDoesNotOwnWorkspaceLock();

        // proceed
        return super.get(timeout, unit);
    }

}