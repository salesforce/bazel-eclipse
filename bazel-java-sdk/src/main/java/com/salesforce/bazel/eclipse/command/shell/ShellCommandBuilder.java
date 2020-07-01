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
package com.salesforce.bazel.eclipse.command.shell;

import java.io.IOException;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.salesforce.bazel.eclipse.abstractions.CommandConsole;
import com.salesforce.bazel.eclipse.abstractions.CommandConsoleFactory;
import com.salesforce.bazel.eclipse.command.CommandBuilder;

/**
 * Implementation of CommandBuilder that builds real command line commands (as opposed to
 * mock commands used in testing). This is the implementation used by the plugin when running
 * in Eclipse.
 * <p>
 * It creates instances of type ShellCommand.
 */
public class ShellCommandBuilder extends CommandBuilder {

    public ShellCommandBuilder(final CommandConsoleFactory consoleFactory) {
        super(consoleFactory);
    }

    /**
     * Build a Command object.
     */
    public ShellCommand build_impl() throws IOException {
        Preconditions.checkNotNull(directory);
        ImmutableList<String> iargs = ImmutableList.copyOf(this.args); 
        CommandConsole console = consoleName == null ? null : consoleFactory.get(consoleName,
            "Running " + String.join(" ", args) + " from " + directory.toString());
        
        ShellCommand command = new ShellCommand(console, directory, iargs, stdoutSelector, stderrSelector, stdout, stderr, stdoutObserver, stderrObserver,
            progressMonitor, timeoutMS);
        
        return command;
    }

}
