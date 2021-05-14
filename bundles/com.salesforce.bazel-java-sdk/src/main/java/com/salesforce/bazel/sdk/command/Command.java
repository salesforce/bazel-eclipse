/**
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
package com.salesforce.bazel.sdk.command;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

/**
 * A built command that can be run via the run() method.
 */
public interface Command {

    /**
     * Executes the command represented by this instance, and return the exit code of the command. This method should
     * not be called twice on the same object.
     *
     * @throws CoreException
     */
    int run() throws IOException, InterruptedException;

    /**
     * Returns the list of lines selected from the standard error stream. Lines printed to the standard error stream by
     * the executed command can be filtered to be added to that list.
     *
     * @see {@link CommandBuilder#setStderrLineSelector(Function)}
     */
    List<String> getSelectedErrorLines();

    /**
     * Returns the list of lines selected from the standard output stream. Lines printed to the standard output stream
     * by the executed command can be filtered to be added to that list.
     *
     * @see {@link CommandBuilder#setStdoutLineSelector(Function)}
     */
    List<String> getSelectedOutputLines();

    /**
     * Returns a BazelProcessBuilder configured to run this Command instance.
     */
    BazelProcessBuilder getProcessBuilder();

}