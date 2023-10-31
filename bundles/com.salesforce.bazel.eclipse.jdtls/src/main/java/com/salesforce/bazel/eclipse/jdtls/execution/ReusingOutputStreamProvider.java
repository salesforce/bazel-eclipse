package com.salesforce.bazel.eclipse.jdtls.execution;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import com.salesforce.bazel.sdk.command.BazelCommand;
import com.salesforce.bazel.sdk.command.DefaultBazelCommandExecutor.PreparedCommandLine;
import com.salesforce.bazel.sdk.command.VerboseProcessStreamsProvider;

public class ReusingOutputStreamProvider extends VerboseProcessStreamsProvider {

    private final OutputStream out;
    private final PrintWriter writer;

    public ReusingOutputStreamProvider(OutputStream out, BazelCommand<?> command, PreparedCommandLine commandLine)
            throws IOException {
        super(command, commandLine);
        this.out = out;
        writer = new PrintWriter(out); // system/os encoding
    }

    @Override
    public void close() throws IOException {
        writer.flush(); // flush but don't close
    }

    @Override
    public OutputStream getErrorStream() {
        return out;
    }

    @Override
    public OutputStream getOutStream() {
        return out;
    }

    @Override
    protected void print(String message) {
        writer.print(message);
    }

    @Override
    protected void println() {
        writer.println();
    }

    @Override
    protected void println(String message) {
        writer.println(message);
    }
}
