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

    private static volatile Supplier<OutputStream> localPortHostSupplier;

    public static void setLocalPortHostSupplier(Supplier<OutputStream> localPortHostSupplier) {
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
