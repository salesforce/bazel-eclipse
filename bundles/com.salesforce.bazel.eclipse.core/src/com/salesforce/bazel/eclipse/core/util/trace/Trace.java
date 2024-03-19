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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A trace is a simple central class for profiling an operation.
 * <p>
 * It maintains a hierarchy of nested operations (spans) for measuring their execution time.
 * </p>
 */
public final class Trace {

    public static final class Span {

        private final String name;
        private final long startTime;
        private final StopWatch stopWatch;
        private final List<Span> children = new ArrayList<>();
        private boolean done = false;

        Span(String name) {
            this.name = requireNonNull(name, "name must not be null");
            if (name.isBlank()) {
                throw new IllegalArgumentException("A blank name is not allowed!");
            }
            startTime = System.currentTimeMillis();
            stopWatch = new StopWatch();
            stopWatch.start();
        }

        public void done() {
            if (done) {
                return;
            }

            // stop all children
            children.forEach(Span::done);

            done = true;
            stopWatch.stop();
        }

        List<Span> getChildren() {
            return children;
        }

        public long getDuration(TimeUnit timeUnit) {
            return stopWatch.getDuration(timeUnit);
        }

        public long getStartTime() {
            return startTime;
        }

        public String getName() {
            return name;
        }

        boolean isDone() {
            return done;
        }

        private Span newChild(String name) {
            var child = new Span(name);
            children.add(child);
            return child;
        }

        @Override
        public String toString() {
            return name + " " + stopWatch;
        }

    }

    private static final ThreadLocal<Trace> activeTrace = new ThreadLocal<>();

    /**
     * @return Trace of the current thread
     */
    public static Trace getCurrentTrace() {
        return activeTrace.get();
    }

    /**
     * Sets the current threads {@link Trace}.
     *
     * @param trace
     *            the trace to set
     * @return the old trace
     */
    public static Trace setCurrentTrace(Trace trace) {
        var old = activeTrace.get();
        activeTrace.set(trace);
        return old;
    }

    /**
     * Starts and returns a new {@link Span} in the tracing hierarchy if tracing is active.
     *
     * @param name
     *            the span name
     * @return a new span (maybe <code>null</code>)
     */
    public static Span startSpanIfTraceIsActive(String name) {
        var trace = activeTrace.get();
        if ((trace == null) || trace.getRoot().isDone()) {
            return null;
        }
        return trace.newSpan(name);
    }

    /**
     * Checks if there is an active Trace.
     * <p>
     * If there is one, a new {@link Span} in the hierarchy of the trace is created. Otherwise tracing is activated and
     * the root span returned.
     * </p>
     *
     * @param name
     *            name of the span
     * @return a new span in the tracing hierarchy
     */
    public static Span startSpanOrTracing(String name) {
        var trace = activeTrace.get();
        if ((trace != null) && !trace.getRoot().isDone()) {
            // new span from active trace
            return trace.newSpan(name);
        }

        // activate new trace and use root span
        trace = new Trace(name);
        setCurrentTrace(trace);
        return trace.getRoot();
    }

    private final Span root;
    private final Instant created;
    private final Deque<Span> spanStack = new ArrayDeque<>();

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
        root = new Span(name);

        // mark creation time
        created = Instant.now();
    }

    /**
     * Finishes tracing and removes this from the {@link #getCurrentTrace()}.
     *
     * @return Duration from the beginning of this {@link Trace}
     */
    public Duration done() {
        root.done();

        // clear stack
        spanStack.clear();

        // remove from automatically from this thread
        setCurrentTrace(null);

        return Duration.ofNanos(root.getDuration(TimeUnit.NANOSECONDS));
    }

    /**
     * @return the creation time (obtained via <code>Instant.now()</code> at creation time)
     */
    public Instant getCreated() {
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

    public Span newSpan(String name) {
        var lastSpan = spanStack.peek();
        if (lastSpan == null) {
            lastSpan = root;
            spanStack.push(lastSpan);
        }

        // clean up closed spans
        // note: it is illegal to call newSpan on a done Trace
        while (lastSpan.done) {
            // if there is no name, the span is not good
            // we remove it from the stack and discard it
            removeFromSpanStack(lastSpan);
            lastSpan = spanStack.peek();
            if (lastSpan == null) {
                throw new IllegalStateException("Attempted to create a new span on a done trace!");
            }
        }

        var child = lastSpan.newChild(name);
        spanStack.push(child);
        return child;
    }

    private void removeFromSpanStack(Span child) {
        if (spanStack.contains(child)) {
            // start from the last and remove all up to (including) the child
            while (!spanStack.isEmpty()) {
                var removed = spanStack.poll();
                if (removed == child) {
                    return;
                }
            }
        }
    }

    @Override
    public String toString() {
        return root.toString();
    }

}