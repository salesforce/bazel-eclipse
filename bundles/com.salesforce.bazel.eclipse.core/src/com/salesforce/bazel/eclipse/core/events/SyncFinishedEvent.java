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

import static com.salesforce.bazel.eclipse.core.util.trace.TraceGraphDumper.dumpTrace;
import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Attribute.INTENSITY_BOLD;
import static org.fusesource.jansi.Ansi.Attribute.ITALIC;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.eclipse.core.runtime.IPath;
import org.osgi.service.event.Event;

import com.google.gson.JsonObject;
import com.salesforce.bazel.eclipse.core.util.trace.Trace;
import com.salesforce.bazel.eclipse.core.util.trace.TraceTree;

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

    public Event toEvent() {
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

    public JsonObject toJson() {
        var eventData = new JsonObject();
        eventData.addProperty(EVENT_DATA_BAZEL_WORKSPACE_LOCATION, workspaceLocation().toString());
        eventData.addProperty("startEpochMilliseconds", start().toEpochMilli());
        eventData.addProperty("durationMilliseconds", duration().toMillis());
        eventData.addProperty(EVENT_DATA_STATUS, status());
        if (projectsCount() > 0) {
            eventData.addProperty(EVENT_DATA_COUNT_PROJECT, projectsCount());
        }
        if (targetsCount() > 0) {
            eventData.addProperty(EVENT_DATA_COUNT_TARGETS, targetsCount());
        }
        if (targetDiscoveryStrategy() != null) {
            eventData.addProperty(EVENT_DATA_TARGET_DISCOVERY_STRATEGY, targetDiscoveryStrategy());
        }
        if (targetProvisioningStrategy() != null) {
            eventData.addProperty(EVENT_DATA_TARGET_PROVISIONING_STRATEGY, targetProvisioningStrategy());
        }
        var trace = trace();
        if (trace != null) {
            var traceTree = TraceTree.create(trace);
            eventData.add("trace", traceTree.getRootNode().toJson());
        }
        return eventData;
    }

    /**
     * Prints an ANSI formatted summary of the event to the given <code>println</code> consumer.
     * <p>
     * The output will be colorful and is suitable for printing to terminals/console.
     * </p>
     *
     * @param println
     *            the <code>println</code> consumer
     */
    public void printFormatted(Consumer<String> println) {
        println.accept("");
        println.accept(ansi().a(INTENSITY_BOLD).a("Synchronization Summary").reset().toString());
        println.accept(ansi().a(INTENSITY_BOLD).a("=======================").reset().toString());
        println.accept("");

        if (trace() == null) {
            println.accept(status());
            return;
        }

        TimeUnit resultion;
        if (duration().getSeconds() >= 1200) {
            resultion = TimeUnit.MINUTES;
        } else if (duration().getSeconds() >= 30) {
            resultion = TimeUnit.SECONDS;
        } else {
            resultion = TimeUnit.MILLISECONDS;
        }

        println.accept(ansi().a(ITALIC).a("  Status: ").reset().a(status()).toString());
        println.accept(ansi().a(ITALIC).a("Projects: ").reset().a(projectsCount()).toString());
        println.accept(ansi().a(ITALIC).a(" Targets: ").reset().a(targetsCount()).toString());
        println.accept(ansi().a(ITALIC).a("Strategy: ").reset().a(targetProvisioningStrategy()).toString());
        println.accept("");

        var lines = dumpTrace(trace(), 100, 0.1F, resultion);
        for (String line : lines) {
            println.accept(line);
        }
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
