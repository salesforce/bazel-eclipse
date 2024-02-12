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

/**
 * Shared constants to be used with the OSGi EventAdmin specification.
 */
public interface BazelCoreEventConstants {

    String TOPIC_BASE = "com/salesforce/bazel/eclipse/core/events/";
    String TOPIC_EVERYTHING = TOPIC_BASE + "*";

    String TOPIC_SYNC_FINISHED = TOPIC_BASE + "sync-finished";

    String EVENT_DATA_START_INSTANT = "start";
    String EVENT_DATA_DURATION = "duration";
    String EVENT_DATA_STATUS = "status";

    String EVENT_DATA_COUNT_PROJECT = "count.projects";
    String EVENT_DATA_COUNT_TARGETS = "count.targets";
}
