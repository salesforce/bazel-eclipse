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
package com.salesforce.bazel.eclipse.ui.execution;

import java.io.IOException;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.events.BazelCoreEventConstants;
import com.salesforce.bazel.eclipse.core.events.SyncFinishedEvent;

/**
 * A little component which will output sync stats to the console of a workspace.
 */
@Component(property = EventConstants.EVENT_TOPIC + "=" + BazelCoreEventConstants.TOPIC_SYNC_FINISHED, immediate = true)
public class BazelUiSyncStatsOutputComponent implements EventHandler {

    private static Logger LOG = LoggerFactory.getLogger(BazelUiSyncStatsOutputComponent.class);

    @Override
    public void handleEvent(Event event) {
        var syncFinishedEvent = SyncFinishedEvent.fromEvent(event);
        var console = new BazelWorkspaceConsole(syncFinishedEvent.workspaceLocation());
        try (var stream = console.newMessageStream()) {
            syncFinishedEvent.printFormatted(stream::println);
        } catch (IOException e) {
            LOG.error("Error printing Sync stats: {}", e.getMessage(), e);
        }
    }

}
