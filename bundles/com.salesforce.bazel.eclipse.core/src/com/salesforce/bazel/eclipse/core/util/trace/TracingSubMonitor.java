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
package com.salesforce.bazel.eclipse.core.util.trace;

import static com.salesforce.bazel.eclipse.core.util.trace.Trace.startSpanIfTraceIsActive;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;

import com.salesforce.bazel.eclipse.core.util.trace.Trace.Span;

/**
 * A wrapper around {@link SubMonitor} which adds a little tracing.
 * <p>
 * Note, for proper tracing names are required as well as the use of {@link #done()} on the first (outer)
 * {@link TracingSubMonitor}. Children created via {@link #split(int, String)} don't need {@link #done()} to be called.
 * </p>
 */
public class TracingSubMonitor implements IProgressMonitor {

    /**
     * Converts an unknown (possibly null) IProgressMonitor into a {@link TracingSubMonitor} allocated with the given
     * number of ticks.
     * <p>
     * For proper tracing it is required to call {@link #done()} on the result. {@link #beginTask(String, int)} will be
     * called by this method.
     * </p>
     * <p>
     * This method should generally be called at the beginning of a method that accepts an IProgressMonitor in order to
     * convert the IProgressMonitor into a {@link TracingSubMonitor}. A {@link Trace} is expected to be
     * {@link Trace#getActiveTrace() active in the current thread} otherwise tracing is a no-op.
     * </p>
     * <p>
     * Since it is illegal to call beginTask on the same IProgressMonitor more than once, the same instance of
     * IProgressMonitor must not be passed to convert more than once.
     * </p>
     *
     * @param monitor
     *            to convert into a SubMonitor instance or null. If given a null argument, the resulting SubMonitor will
     *            not report its progress anywhere.
     * @param taskName
     *            user readable name to pass to monitor.beginTask. Never <code>null</code>.
     * @param work
     *            initial number of ticks to allocate for children of the SubMonitor
     * @return a new {@link TracingSubMonitor} instance that is a child of the given monitor
     */
    public static TracingSubMonitor convert(IProgressMonitor monitor, String taskName, int work) {
        if (monitor instanceof TracingSubMonitor tracingSubMonitor) {
            // When someone wants to convert an existing TracingSubMonitor into a TracingSubMonitor then
            // we do not create a new child; the reason is that this scenario is likely in the event of
            // API boundaries, i.e. an outer process calls to some API which may or may not be tracing aware.
            // In this case the API is likely be called with a child already and never with an outer
            // "in-progress" monitor.
            // Therefore we only set the remaining work assuming beginTask was already called
            return tracingSubMonitor.setWorkRemaining(work);
        }

        // create the SubMonitor
        var subMonitor = SubMonitor.convert(monitor, taskName, work);

        // start the span
        var span = startSpanIfTraceIsActive(taskName);

        return new TracingSubMonitor(subMonitor, span);
    }

    final SubMonitor subMonitor;
    final Span span; // optional

    /**
     * Children created by split will be completed automatically the next time the parent progress monitor is touched.
     * This points to the last incomplete child created with split.
     */
    private TracingSubMonitor lastTracingMonitor;

    /**
     * Create a new instance wrapping the given {@link SubMonitor}.
     *
     * @param subMonitor
     * @param span
     *            optional span for reporting (maybe <code>null</code> if tracing is inactive)
     */
    private TracingSubMonitor(SubMonitor subMonitor, Span span) {
        this.subMonitor = subMonitor;
        this.span = span;
    }

    @Override
    public void beginTask(String name, int totalWork) {
        subMonitor.beginTask(name, totalWork);
    }

    public SubMonitor checkCanceled() throws OperationCanceledException {
        return subMonitor.checkCanceled();
    }

    private void cleanupActiveChild() {
        IProgressMonitor child = lastTracingMonitor;
        if (child == null) {
            return;
        }

        lastTracingMonitor = null;
        child.done();
    }

    @Override
    public void clearBlocked() {
        subMonitor.clearBlocked();
    }

    /**
     * Notifies that the work is done.
     */
    @Override
    public void done() {
        cleanupActiveChild();

        if (span != null) {
            span.done();
        }

        subMonitor.done();
    }

    @Override
    public void internalWorked(double work) {
        subMonitor.internalWorked(work);
    }

    @Override
    public boolean isCanceled() {
        return subMonitor.isCanceled();
    }

    /**
     * Create a new active span as child of current and returns a new tracing monitor.
     *
     * @param name
     *            the name of the span to create
     * @param subMonitor
     *            the submonitor to wrap
     * @return a new tracing sub monitor wrapping the given subMonitor
     */
    private TracingSubMonitor newChild(String name, SubMonitor subMonitor) {
        cleanupActiveChild();

        return lastTracingMonitor = new TracingSubMonitor(subMonitor, startSpanIfTraceIsActive(name));
    }

    @Override
    public void setBlocked(IStatus reason) {
        subMonitor.setBlocked(reason);
    }

    @Override
    public void setCanceled(boolean b) {
        subMonitor.setCanceled(b);
    }

    @Override
    public void setTaskName(String name) {
        subMonitor.setTaskName(name);
    }

    /**
     * @see SubMonitor#setWorkRemaining(int)
     */
    public TracingSubMonitor setWorkRemaining(int workRemaining) {
        subMonitor.setWorkRemaining(workRemaining);
        return this;
    }

    /**
     * Creates a new active span as child of the current one and a new {@link SubMonitor} using
     * {@link SubMonitor#SUPPRESS_NONE}.
     * <p>
     * The implementation calls {@link #beginTask(String, int)} on the {@link SubMonitor}. {@link #done()} is not
     * required to be called on the child. It will be called implicitly when {@link #done()} on the parent is called.
     * </p>
     *
     * @param totalWork
     *            the work the new child is allowed to consume from this parent
     * @param name
     *            the name of the span
     */
    public TracingSubMonitor split(int totalWork, String name) throws OperationCanceledException {
        var monitor = subMonitor.split(totalWork, SubMonitor.SUPPRESS_NONE);
        monitor.beginTask(name, totalWork);
        return newChild(name, monitor);
    }

    @Override
    public void subTask(String name) {
        subMonitor.subTask(name);
    }

    @Override
    public String toString() {
        return subMonitor.toString();
    }

    @Override
    public void worked(int work) {
        subMonitor.worked(work);
    }

}
