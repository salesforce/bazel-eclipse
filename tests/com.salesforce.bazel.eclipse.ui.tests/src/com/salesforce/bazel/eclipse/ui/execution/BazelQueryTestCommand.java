package com.salesforce.bazel.eclipse.ui.execution;

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.readAllLines;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.command.BazelQueryCommand;

/**
 * <code>bazel query --output streamed_proto --order_output=no</code>
 */
public class BazelQueryTestCommand extends BazelQueryCommand<Collection<String>> {

    public BazelQueryTestCommand(Path workspaceRoot, String query, boolean keepGoing) {
        super(workspaceRoot, query, keepGoing);
    }

    @Override
    protected Collection<String> doGenerateResult() throws IOException {
        return readAllLines(getStdOutFile(), Charset.defaultCharset());
    }

    @Override
    public List<String> prepareCommandLine(BazelVersion bazelVersion) throws IOException {
        // redirect output to file for parsing
        var stdoutFile = createTempFile("bazel_test_query_stdout_", ".bin");
        setRedirectStdOutToFile(stdoutFile);

        // prepare regular query command line
        return super.prepareCommandLine(bazelVersion);
    }
}
