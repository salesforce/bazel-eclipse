package com.salesforce.bazel.eclipse.jdtls.execution;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.extensions.EclipseHeadlessBazelCommandExecutor;
import com.salesforce.bazel.sdk.command.BazelCommand;
import com.salesforce.bazel.sdk.command.ProcessStreamsProvider;

public class StreamingSocketBazelCommandExecutor extends EclipseHeadlessBazelCommandExecutor {

    private static Logger LOG = LoggerFactory.getLogger(StreamingSocketBazelCommandExecutor.class);

    private static final Supplier<OutputStream> staticLocalPortHostSupplier = initializeStaticSupplier();

    private static volatile Supplier<OutputStream> localPortHostSupplier;

    private static Supplier<OutputStream> initializeStaticSupplier() {
        var port = Integer.getInteger("java.bazel.staticProcessStreamSocket");
        if ((port != null) && (port > 0) && (port < 65535)) {
            var staticPort = port;
            try {
                LOG.debug("Initializing static socket stream using port {}.", staticPort);
                return new ReconnectingSocket(staticPort);
            } catch (IOException e) {
                LOG.error(
                    "Invalid 'java.bazel.staticProcessStreamSocket' property. Connection to port {} failed.",
                    staticPort,
                    e);
            }
        }

        return null;
    }

    public static void setLocalPortHostSupplier(Supplier<OutputStream> localPortHostSupplier) {
        StreamingSocketBazelCommandExecutor.localPortHostSupplier = localPortHostSupplier;
    }

    @Override
    protected void injectAdditionalOptions(List<String> commandLine, int injectPositionForNoneStartupOptions) {
        // tweak for Eclipse Console
        commandLine.add(injectPositionForNoneStartupOptions, "--progress_in_terminal_title=no");
        commandLine.add(injectPositionForNoneStartupOptions, "--curses=no");
        commandLine.add(injectPositionForNoneStartupOptions, "--color=yes");

        // add tool tag
        super.injectAdditionalOptions(commandLine, injectPositionForNoneStartupOptions);
    }

    @Override
    protected ProcessStreamsProvider newProcessStreamProvider(BazelCommand<?> command, PreparedCommandLine commandLine)
            throws IOException {
        var supplier = localPortHostSupplier;
        if (supplier == null) {
            supplier = staticLocalPortHostSupplier;
        }
        if (supplier != null) {
            var out = supplier.get();
            if (out != null) {
                LOG.info("> {} (>>> {})", command.toString(), out);
                return new ReusingOutputStreamProvider(out, command, commandLine);
            }
        }

        // fallback to default
        return super.newProcessStreamProvider(command, commandLine);
    }
}
