package com.salesforce.bazel.eclipse.mock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.salesforce.bazel.eclipse.abstractions.CommandConsole;
import com.salesforce.bazel.eclipse.abstractions.CommandConsoleFactory;

public class MockCommandConsole implements CommandConsole, CommandConsoleFactory {
    final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    public MockCommandConsole() {
    }

    @Override
    public OutputStream createOutputStream() {
        return stdout;
    }

    @Override
    public OutputStream createErrorStream() {
        return stderr;
    }

    @Override
    public CommandConsole get(String name, String title) throws IOException {
        return this;
    }
}
