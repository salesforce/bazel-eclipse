package com.salesforce.bazel.eclipse.jdtls.execution;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

import com.salesforce.bazel.sdk.command.BazelCommand;
import com.salesforce.bazel.sdk.command.DefaultBazelCommandExecutor.PreparedCommandLine;
import com.salesforce.bazel.sdk.command.VerboseProcessStreamsProvider;

public class SocketStreamProvider extends VerboseProcessStreamsProvider {

    private final OutputStream socketOut;
    private final Socket socket;
    private final PrintWriter socketOutWriter;

    public SocketStreamProvider(Socket socket, BazelCommand<?> command, PreparedCommandLine commandLine)
            throws IOException {
        super(command, commandLine);
        this.socket = socket;
        socketOut = socket.getOutputStream();
        socketOutWriter = new PrintWriter(socketOut);
    }

    @Override
    public void close() throws IOException {
        socketOutWriter.close();
        socketOut.close();
        socket.close();
    }

    @Override
    public OutputStream getErrorStream() {
        return socketOut;
    }

    @Override
    public OutputStream getOutStream() {
        return socketOut;
    }

    @Override
    protected void println() {
        socketOutWriter.println();
    }

    @Override
    protected void println(String message) {
        socketOutWriter.println(message);
    }

}
