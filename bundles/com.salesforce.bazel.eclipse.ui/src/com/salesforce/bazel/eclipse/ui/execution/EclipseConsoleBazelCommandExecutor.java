package com.salesforce.bazel.eclipse.ui.execution;

import static java.lang.String.format;

import java.io.IOException;
import java.util.List;

import com.salesforce.bazel.eclipse.core.extensions.EclipseHeadlessBazelCommandExecutor;
import com.salesforce.bazel.eclipse.ui.BazelUIPlugin;
import com.salesforce.bazel.sdk.command.BazelCommand;
import com.salesforce.bazel.sdk.command.BazelCommandExecutor;
import com.salesforce.bazel.sdk.command.ProcessStreamsProvider;

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
        return new EclipseConsoleStreamsProvider(command, commandLine);
    }

}
