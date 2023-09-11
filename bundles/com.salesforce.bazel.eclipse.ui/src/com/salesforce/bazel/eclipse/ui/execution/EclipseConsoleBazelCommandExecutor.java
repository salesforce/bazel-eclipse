package com.salesforce.bazel.eclipse.ui.execution;

import static java.lang.String.format;

import java.io.IOException;
import java.util.List;

import com.salesforce.bazel.eclipse.core.extensions.EclipseHeadlessBazelCommandExecutor;
import com.salesforce.bazel.eclipse.ui.BazelUIPlugin;
import com.salesforce.bazel.sdk.command.BazelCommand;
import com.salesforce.bazel.sdk.command.BazelCommandExecutor;

/**
 * Implementation of a {@link BazelCommandExecutor} which integrates with the Eclipse Console.
 */
public class EclipseConsoleBazelCommandExecutor extends EclipseHeadlessBazelCommandExecutor {

    @Override
    protected String getToolTagArgument() {
        var toolTagArgument = this.cachedToolTagArgument;
        if (toolTagArgument != null) {
            return toolTagArgument;
        }
        return this.cachedToolTagArgument = format("--tool_tag=eclipse:ui:%s", BazelUIPlugin.getBundleVersion());
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
        return new EclipseConsoleStreamsProvider(command, commandLine);
    }

}
