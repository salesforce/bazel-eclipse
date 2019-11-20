package com.salesforce.bazel.eclipse.command;

import java.io.IOException;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;

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
    ImmutableList<String> getSelectedErrorLines();
    
    /**
     * Returns a ProcessBuilder configured to run this Command instance.
     */
    ProcessBuilder getProcessBuilder();

    /**
     * Returns the list of lines selected from the standard output stream. Lines printed to the standard output stream
     * by the executed command can be filtered to be added to that list.
     *
     * @see {@link CommandBuilder#setStdoutLineSelector(Function)}
     */
    ImmutableList<String> getSelectedOutputLines();

}