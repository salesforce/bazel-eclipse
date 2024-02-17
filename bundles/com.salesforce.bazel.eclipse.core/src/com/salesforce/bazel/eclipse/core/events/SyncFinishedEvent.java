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
 *      Salesforce - adapted from M2E, JDT or other Eclipse project
 */
package com.salesforce.bazel.eclipse.core.events;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.osgi.service.event.Event;

import com.salesforce.bazel.eclipse.core.util.trace.Trace;

/**
 * A synchronization finished event.
 */
public record SyncFinishedEvent(
        IPath workspaceLocation,
        Instant start,
        Duration duration,
        String status,
        int projectsCount,
        int targetsCount,
        String targetDiscoveryStrategy,
        String targetProvisioningStrategy,
        Trace trace) implements BazelCoreEventConstants {

    /**
     * Convenience constructor for events without additional information (in case of none-ok status)
     */
    public SyncFinishedEvent(IPath workspaceLocation, Instant start, Duration duration, String status) {
        this(workspaceLocation,
                start,
                duration,
                status,
                0,
                0,
                null /* target discover */,
                null /* target provisioning */,
                null /* trace */);
    }

    public Event build() {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put(EVENT_DATA_BAZEL_WORKSPACE_LOCATION, workspaceLocation());
        eventData.put(EVENT_DATA_START_INSTANT, start());
        eventData.put(EVENT_DATA_DURATION, duration());
        eventData.put(EVENT_DATA_STATUS, status());
        eventData.put(EVENT_DATA_COUNT_PROJECT, projectsCount());
        eventData.put(EVENT_DATA_COUNT_TARGETS, targetsCount());
        eventData.put(EVENT_DATA_TARGET_DISCOVERY_STRATEGY, targetDiscoveryStrategy());
        eventData.put(EVENT_DATA_TARGET_PROVISIONING_STRATEGY, targetProvisioningStrategy());
        eventData.put(EVENT_DATA_TRACE, trace());
        return new Event(TOPIC_SYNC_FINISHED, eventData);
    }

    public static SyncFinishedEvent fromEvent(Event event) {
        var workspaceLocation = (IPath) event.getProperty(EVENT_DATA_BAZEL_WORKSPACE_LOCATION);
        var start = (Instant) event.getProperty(EVENT_DATA_START_INSTANT);
        var duration = (Duration) event.getProperty(EVENT_DATA_DURATION);
        var status = (String) event.getProperty(EVENT_DATA_STATUS);
        var projectsCount = (Integer) event.getProperty(EVENT_DATA_COUNT_PROJECT);
        var targetsCount = (Integer) event.getProperty(EVENT_DATA_COUNT_TARGETS);
        var targetDiscoveryStrategy = (String) event.getProperty(EVENT_DATA_TARGET_DISCOVERY_STRATEGY);
        var targetProvisionintStrategy = (String) event.getProperty(EVENT_DATA_TARGET_PROVISIONING_STRATEGY);
        var trace = (Trace) event.getProperty(EVENT_DATA_TRACE);
        return new SyncFinishedEvent(
                workspaceLocation,
                start,
                duration,
                status,
                projectsCount != null ? projectsCount : 0,
                targetsCount != null ? targetsCount : 0,
                targetDiscoveryStrategy,
                targetProvisionintStrategy,
                trace);
    }
}
