package com.salesforce.bazel.eclipse.command;

import java.io.IOException;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.salesforce.bazel.eclipse.abstractions.CommandConsole;
import com.salesforce.bazel.eclipse.abstractions.CommandConsoleFactory;

public class ShellCommandBuilder extends CommandBuilder {

    public ShellCommandBuilder(final CommandConsoleFactory consoleFactory) {
        super(consoleFactory);
    }

    /**
     * Build a Command object.
     */
    public ShellCommand build() throws IOException {
        Preconditions.checkNotNull(directory);
        ImmutableList<String> args = this.args.build();
        CommandConsole console = consoleName == null ? null : consoleFactory.get(consoleName,
            "Running " + String.join(" ", args) + " from " + directory.toString());
        
        ShellCommand command = new ShellCommand(console, directory, args, stdoutSelector, stderrSelector, stdout, stderr,
            progressMonitor);
        
        // get ready for next command to be built
        this.reset();
        
        return command;
    }

}
