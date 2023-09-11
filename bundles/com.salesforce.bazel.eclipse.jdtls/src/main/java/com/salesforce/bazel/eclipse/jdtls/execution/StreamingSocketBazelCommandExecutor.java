package com.salesforce.bazel.eclipse.jdtls.execution;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.function.Supplier;

import com.salesforce.bazel.eclipse.core.extensions.EclipseHeadlessBazelCommandExecutor;
import com.salesforce.bazel.sdk.command.BazelCommand;
import com.salesforce.bazel.sdk.command.ProcessStreamsProvider;

public class StreamingSocketBazelCommandExecutor extends EclipseHeadlessBazelCommandExecutor {

    private static volatile Supplier<Integer> localPortHostSupplier;

    public static void setLocalPortHostSupplier(Supplier<Integer> localPortHostSupplier) {
        StreamingSocketBazelCommandExecutor.localPortHostSupplier = localPortHostSupplier;
    }

    @Override
    protected void injectAdditionalOptions(List<String> commandLine) {
        super.injectAdditionalOptions(commandLine);

        // tweak for Eclipse Console
        commandLine.add("--color=yes");
        commandLine.add("--curses=no");
        commandLine.add("--progress_in_terminal_title=no");
    }

    @Override
    protected ProcessStreamsProvider newProcessStreamProvider(BazelCommand<?> command, PreparedCommandLine commandLine)
            throws IOException {
        var supplier = localPortHostSupplier;
        if (supplier != null) {
            var port = supplier.get();
            if (port != null) {
                var socket = new Socket("localhost", port);
                return new SocketStreamProvider(socket, command, commandLine);
            }
        }

        // fallback to default
        return super.newProcessStreamProvider(command, commandLine);
    }
}
