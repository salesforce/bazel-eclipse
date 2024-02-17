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
package com.salesforce.bazel.eclipse.jdtls.execution;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.function.Supplier;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import com.salesforce.bazel.eclipse.core.events.BazelCoreEventConstants;
import com.salesforce.bazel.eclipse.core.events.SyncFinishedEvent;

/**
 * A little component which will output sync stats to the console of a workspace.
 */
@Component(property = EventConstants.EVENT_TOPIC + "=" + BazelCoreEventConstants.TOPIC_SYNC_FINISHED, immediate = true)
public class BazelJdtLsSyncStatsOutputComponent implements EventHandler {

    static final class ReusingOutputStreamPrintWriter extends PrintWriter {
        private ReusingOutputStreamPrintWriter(Supplier<OutputStream> outputStreamSupplier) {
            super(outputStreamSupplier.get()); // system/os encoding
        }

        @Override
        public void close() {
            flush(); // only flush, never close the underlying output stream (per contract)
        }
    }

    @Override
    public void handleEvent(Event event) {
        var outputStreamSupplier = StreamingSocketBazelCommandExecutor.getConfiguredOutputStreamSupplier();
        if (outputStreamSupplier == null) {
            // nothing to write to
            return;
        }

        var syncFinishedEvent = SyncFinishedEvent.fromEvent(event);
        try (var writer = new ReusingOutputStreamPrintWriter(outputStreamSupplier);) {
            syncFinishedEvent.printFormatted(writer::println);
        }
    }

}
