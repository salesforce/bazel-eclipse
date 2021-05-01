/*
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.salesforce.bazel.sdk.util;

/**
 * Abstraction that allows an observer to monitor the progress of work performed during an operation.
 */
public interface WorkProgressMonitor {

    /**
     * Notifies that the main task is beginning. This must only be called once on a given progress monitor instance.
     *
     * @param name
     *            the name (or description) of the main task
     * @param totalWork
     *            the total number of work units into which the main task is been subdivided. If the value is
     *            <code>UNKNOWN</code> the implementation is free to indicate progress in a way which doesn't require
     *            the total number of work units in advance.
     */
    public void beginTask(String name, int totalWork);

    /**
     * Notifies that the work is done; that is, either the main task is completed or the user canceled it. This method
     * may be called more than once (implementations should be prepared to handle this case).
     */
    public void done();

    /**
     * Returns whether cancelation of current operation has been requested. Long-running operations should poll to see
     * if cancelation has been requested.
     *
     * @return <code>true</code> if cancellation has been requested, and <code>false</code> otherwise
     * @see #setCanceled(boolean)
     */
    public boolean isCanceled();

    /**
     * Sets the cancel state to the given value.
     *
     * @param value
     *            <code>true</code> indicates that cancelation has been requested (but not necessarily acknowledged);
     *            <code>false</code> clears this flag
     * @see #isCanceled()
     */
    public void setCanceled(boolean value);

    /**
     * Notifies that a subtask of the main task is beginning. Subtasks are optional; the main task might not have
     * subtasks.
     *
     * @param name
     *            the name (or description) of the subtask
     */
    public void subTask(String name);

    /**
     * Notifies that a given number of work unit of the main task has been completed. Note that this amount represents
     * an installment, as opposed to a cumulative amount of work done to date.
     *
     * @param work
     *            a non-negative number of work units just completed
     */
    public void worked(int work);

    WorkProgressMonitor NOOP = new WorkProgressMonitor() {
        @Override
        public void worked(int work) {}

        @Override
        public void subTask(String name) {}

        @Override
        public void setCanceled(boolean value) {}

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public void done() {}

        @Override
        public void beginTask(String name, int totalWork) {}
    };
}
