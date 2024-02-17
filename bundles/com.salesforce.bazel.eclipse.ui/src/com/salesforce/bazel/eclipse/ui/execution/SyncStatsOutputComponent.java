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

import static com.salesforce.bazel.eclipse.core.util.trace.TraceGraphDumper.dumpTrace;
import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Attribute.INTENSITY_BOLD;
import static org.fusesource.jansi.Ansi.Attribute.ITALIC;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

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
public class SyncStatsOutputComponent implements EventHandler {

    private static Logger LOG = LoggerFactory.getLogger(SyncStatsOutputComponent.class);

    @Override
    public void handleEvent(Event event) {
        var syncFinishedEvent = SyncFinishedEvent.fromEvent(event);
        var console = new BazelWorkspaceConsole(syncFinishedEvent.workspaceLocation());
        try (var stream = console.newMessageStream()) {
            stream.println();
            stream.println(ansi().a(INTENSITY_BOLD).a("Synchronization Summary").reset().toString());
            stream.println(ansi().a(INTENSITY_BOLD).a("=======================").reset().toString());
            stream.println();

            TimeUnit resultion;
            if (syncFinishedEvent.duration().getSeconds() >= 1200) {
                resultion = TimeUnit.MINUTES;
            } else if (syncFinishedEvent.duration().getSeconds() >= 30) {
                resultion = TimeUnit.SECONDS;
            } else {
                resultion = TimeUnit.MILLISECONDS;
            }

            stream.println(ansi().a(ITALIC).a("Projects: ").reset().a(syncFinishedEvent.projectsCount()).toString());
            stream.println(ansi().a(ITALIC).a(" Targets: ").reset().a(syncFinishedEvent.targetsCount()).toString());
            stream.println(
                ansi().a(ITALIC).a("Strategy: ").reset().a(syncFinishedEvent.targetProvisioningStrategy()).toString());
            stream.println();

            var lines = dumpTrace(syncFinishedEvent.trace(), 100, 0.1F, resultion);
            for (String line : lines) {
                stream.println(line);
            }

        } catch (IOException e) {
            LOG.error("Error printing Sync stats: {}", e.getMessage(), e);
        }
    }

}
