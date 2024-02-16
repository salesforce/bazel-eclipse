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

import static java.util.Objects.requireNonNull;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SlicedProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import com.salesforce.bazel.eclipse.core.util.trace.Trace.Span;

/**
 * A wrapper around {@link SubMonitor} which adds a little tracing.
 */
public class TracingSubMonitor implements IProgressMonitor {

    static final class SlicedProgressMonitorWithSpan extends SlicedProgressMonitor {

        private final Span span;

        private SlicedProgressMonitorWithSpan(IProgressMonitor monitor, int totalWork, Span span) {
            super(monitor, totalWork);
            this.span = span;
        }

        @Override
        public void beginTask(String name, int totalWork) {
            super.beginTask(name, totalWork);
            span.setName(name);
        }

        @Override
        public void done() {
            span.close();
            super.done();
        }

        @Override
        public IProgressMonitor slice(int work) {
            return new SlicedProgressMonitorWithSpan(this, work, span.newChild());
        }
    }

    /**
     * <p>
     * Converts an unknown (possibly null) IProgressMonitor into a SubMonitor allocated with the given number of ticks.
     * It is not necessary to call done() on the result, but the caller is responsible for calling done() on the
     * argument. Calls beginTask on the argument.
     * </p>
     *
     * <p>
     * This method should generally be called at the beginning of a method that accepts an IProgressMonitor in order to
     * convert the IProgressMonitor into a SubMonitor.
     * </p>
     *
     * <p>
     * Since it is illegal to call beginTask on the same IProgressMonitor more than once, the same instance of
     * IProgressMonitor must not be passed to convert more than once.
     * </p>
     *
     * @param monitor
     *            to convert into a SubMonitor instance or null. If given a null argument, the resulting SubMonitor will
     *            not report its progress anywhere.
     * @param taskName
     *            user readable name to pass to monitor.beginTask. Never null.
     * @param work
     *            initial number of ticks to allocate for children of the SubMonitor
     * @return a new SubMonitor instance that is a child of the given monitor
     */
    public static TracingSubMonitor convert(IProgressMonitor monitor, String taskName, int work) {
        if (monitor instanceof TracingSubMonitor subMonitor) {
            return new TracingSubMonitor(SubMonitor.convert(subMonitor, taskName, work), subMonitor.trace);
        }

        return new TracingSubMonitor(SubMonitor.convert(monitor), taskName);
    }

    final Trace trace;
    final SubMonitor subMonitor;
    final Span span; // optional

    private TracingSubMonitor(SubMonitor subMonitor, String taskName) {
        this(subMonitor, new Trace(taskName));
    }

    private TracingSubMonitor(SubMonitor subMonitor, Trace trace) {
        this(subMonitor, trace, null);
    }

    private TracingSubMonitor(SubMonitor subMonitor, Trace trace, Span span) {
        this.subMonitor = subMonitor;
        this.trace = trace;
        this.span = span;
    }

    @Override
    public void beginTask(String name, int totalWork) {
        subMonitor.beginTask(name, totalWork);
        if (span != null) {
            span.setName(name);
        }
    }

    public SubMonitor checkCanceled() throws OperationCanceledException {
        return subMonitor.checkCanceled();
    }

    @Override
    public void clearBlocked() {
        subMonitor.clearBlocked();
    }

    @Override
    public void done() {
        if (span != null) {
            span.close();
        } else {
            trace.close();
        }
        subMonitor.done();
    }

    @Override
    public boolean equals(Object obj) {
        return subMonitor.equals(obj);
    }

    /**
     * @return the trace
     */
    public Trace getTrace() {
        return requireNonNull(trace);
    }

    @Override
    public int hashCode() {
        return subMonitor.hashCode();
    }

    @Override
    public void internalWorked(double work) {
        subMonitor.internalWorked(work);
    }

    @Override
    public boolean isCanceled() {
        return subMonitor.isCanceled();
    }

    public TracingSubMonitor newChild(int totalWork) {
        var childSubMonitor = subMonitor.newChild(totalWork);
        return new TracingSubMonitor(childSubMonitor, trace, newSpan());
    }

    public TracingSubMonitor newChild(int totalWork, int suppressFlags) {
        var childSubMonitor = subMonitor.newChild(totalWork, suppressFlags);
        return new TracingSubMonitor(childSubMonitor, trace, newSpan());
    }

    private Span newSpan() {
        return span != null ? span.newChild() : trace.newSpan();
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

    @Override
    public IProgressMonitor slice(int work) {
        return new SlicedProgressMonitorWithSpan(subMonitor, work, newSpan());
    }

    /**
     * @see SubMonitor#split(int)
     */
    public TracingSubMonitor split(int totalWork) throws OperationCanceledException {
        var childSubMonitor = subMonitor.split(totalWork);
        return new TracingSubMonitor(childSubMonitor, trace, newSpan());
    }

    /**
     * @see SubMonitor#split(int, int)
     */
    public TracingSubMonitor split(int totalWork, int suppressFlags) throws OperationCanceledException {
        var childSubMonitor = subMonitor.split(totalWork, suppressFlags);
        return new TracingSubMonitor(childSubMonitor, trace, newSpan());
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
