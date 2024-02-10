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

import org.osgi.service.event.Event;

/**
 * A synchronization finished event.
 */
public record SyncFinishedEvent(Instant start, Duration duration, String status) implements BazelCoreEventConstants {

    public static SyncFinishedEvent fromMap(Map<String, ?> eventData) {
        var start = (Instant) eventData.get(EVENT_DATA_START_INSTANT);
        var duration = (Duration) eventData.get(EVENT_DATA_DURATION);
        var status = (String) eventData.get(EVENT_DATA_STATUS);
        return new SyncFinishedEvent(start, duration, status);
    }

    public Event build() {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put(EVENT_DATA_START_INSTANT, start);
        eventData.put(EVENT_DATA_DURATION, duration);
        eventData.put(EVENT_DATA_STATUS, status);
        return new Event(TOPIC_SYNC_FINISHED, eventData);
    }
}
