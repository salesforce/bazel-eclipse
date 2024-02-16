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
 *      Salesforce - Imported from https://github.com/eclipseguru/gyrex/blob/master/platform/bundles/org.eclipse.gyrex.monitoring/src/org/eclipse/gyrex/monitoring/profiling/Transaction.java
 */
package com.salesforce.bazel.eclipse.core.util.trace;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A trace is a simple central class for profiling an operation.
 * <p>
 * It maintains a hierarchy of nested operations (spans) for measuring their execution time.
 * </p>
 */
public final class Trace implements AutoCloseable {

    public static final class Span implements AutoCloseable {

        private String name;
        private final StopWatch stopWatch;
        private final List<Span> children = new ArrayList<>();

        Span() {
            stopWatch = new StopWatch();
            stopWatch.start();
        }

        @Override
        public void close() {
            stopWatch.stop();
        }

        List<Span> getChildren() {
            return children;
        }

        public long getDuration(TimeUnit timeUnit) {
            return stopWatch.getDuration(timeUnit);
        }

        public String getName() {
            return name;
        }

        public Span newChild() {
            var child = new Span();
            children.add(child);
            return child;
        }

        public void setName(String name) {
            this.name = requireNonNull(name, "name must not be null");
        }

        @Override
        public String toString() {
            return name + " " + stopWatch;
        }

    }

    private final Span root;
    private final long created;

    /**
     * Creates a new trace.
     * <p>
     * Note, when a new transaction is created it will be initialized immediately, i.e. it begins measuring.
     * </p>
     *
     * @param name
     *            the trace name
     */
    public Trace(final String name) {
        root = new Span();
        root.setName(name);
        // mark creation time
        created = System.currentTimeMillis();
    }

    @Override
    public void close() {
        root.close();
    }

    /**
     * @return the creation time (obtained via <code>System.currentTimeMillis()</code>)
     */
    public long getCreatedTimestamp() {
        return created;
    }

    public long getDuration(TimeUnit timeUnit) {
        return root.getDuration(timeUnit);
    }

    public String getName() {
        return root.getName();
    }

    Span getRoot() {
        return root;
    }

    public Span newSpan() {
        return root.newChild();
    }

    @Override
    public String toString() {
        return root.toString();
    }
}