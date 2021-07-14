/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.eclipse.projectimport.flow;

import org.eclipse.core.runtime.SubMonitor;

/**
 * A single project import step.
 */
public interface ImportFlow {

    /**
     * Run the import step.
     *
     * @param ctx
     *            the import state passed between ImportFlow implementations
     * @param progressMonitor
     *            optional - may be used for more detailed progress reporting. For progress reporting to work, the
     *            {@link #getTotalWorkTicks(ImportContext)} method MUST be implemented.
     */
    void run(ImportContext ctx, SubMonitor progressMonitor) throws Exception;

    /**
     * The text shown on the progress dialogue when this ImportFlow runs.
     *
     * The text should be in present tense and should not include punctuation.
     *
     * Examples:
     *
     * Creating projects Loading type information Analyzing widgets
     */
    String getProgressText();

    /**
     * Asserts invariants about the state of the specified ctx.
     */
    default void assertContextState(ImportContext ctx) {

    }

    /**
     * Called after all ProjectImportFlow instances have run.
     */
    default void finish(ImportContext ctx) {

    }

    /**
     * Long running ImportFlow implementations may opt into additional progress reporting by implementing this method.
     * This method must return the number of "work units" that will be reported in total to the SubMonitor instance
     * passed to the {@link #run(ImportContext, SubMonitor)} method.
     */
    default int getTotalWorkTicks(ImportContext ctx) {
        return 0;
    }
}
