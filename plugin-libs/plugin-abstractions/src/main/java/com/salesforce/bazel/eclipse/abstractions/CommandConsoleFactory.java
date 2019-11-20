package com.salesforce.bazel.eclipse.abstractions;

import java.io.IOException;

/** A factory that returns a command console by name */
public interface CommandConsoleFactory {
    /**
     * Returns a {@link CommandConsole} that has the name {@code name}. {@code title} will be written at the
     * beginning of the console.
     */
    CommandConsole get(String name, String title) throws IOException;
}
