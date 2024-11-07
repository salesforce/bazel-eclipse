/*-
 * Copyright (c) 2024 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - adapted from ParentProcessWatcher from JDTLS
 */
package com.salesforce.bazel.scipls.app;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches a process PID and notifies a callback when the process is it is no longer available.
 * <p>
 * When process is gone, the callback is triggered. The callback is expected to complete within one minute. After that
 * time the JVM shutdown is forced.
 * </p>
 */
public final class ProcessWatcher {

    public static final int FORCED_EXIT_CODE = 1;

    private static final Logger LOG = LoggerFactory.getLogger(ProcessWatcher.class);

    private static final int POLL_DELAY_SECS = 10;

    static ProcessWatcher forCurrentParentProcess(Runnable processNoLongerAliveCallback) {
        var parent = ProcessHandle.current().parent();
        if (!parent.isPresent()) {
            throw new IllegalStateException("No parent process available to monitor!");
        }

        return new ProcessWatcher(parent.get(), processNoLongerAliveCallback);
    }

    private final ScheduledFuture<?> task;
    private final ScheduledExecutorService service;
    private final ProcessHandle processToMonitor;
    private final Runnable processNoLongerAliveCallback;

    ProcessWatcher(ProcessHandle processToMonitor, Runnable processNoLongerAliveCallback) {
        this.processToMonitor = requireNonNull(processToMonitor);
        this.processNoLongerAliveCallback = requireNonNull(processNoLongerAliveCallback);

        LOG.debug("Watching parent process with PID {}", processToMonitor.pid());
        service = Executors.newScheduledThreadPool(1, r -> {
            var t = new Thread(r, "Process Watcher");
            t.setDaemon(true);
            return t;
        });
        task = service.scheduleWithFixedDelay(this::checkProcess, POLL_DELAY_SECS, POLL_DELAY_SECS, TimeUnit.SECONDS);
    }

    /**
     * Called regularly to check the parent process for liveness
     */
    void checkProcess() {
        if (!processToMonitor.isAlive()) {
            LOG.debug("Parent process stopped running, notifying callback");
            task.cancel(true);

            // hard time limit for the callback
            service.schedule(() -> {
                LOG.warn("Forcing exit after 1 min because orderly shutdown took too long.");
                System.exit(FORCED_EXIT_CODE);
            }, 1, TimeUnit.MINUTES);

            processNoLongerAliveCallback.run();
        }
    }

    public void stop() {
        LOG.debug("Stopping process watcher");
        task.cancel(true);
    }
}
