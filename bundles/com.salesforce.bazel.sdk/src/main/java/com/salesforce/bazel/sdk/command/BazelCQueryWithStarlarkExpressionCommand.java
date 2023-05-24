package com.salesforce.bazel.sdk.command;

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.readString;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;

import com.salesforce.bazel.sdk.BazelVersion;

/**
 * <code>bazel cquery --output starlark --starlark:expr ...</code>
 *
 * @see https://bazel.build/query/cquery#cquery-starlark
 */
public class BazelCQueryWithStarlarkExpressionCommand extends BazelCQueryCommand<String> {

    public BazelCQueryWithStarlarkExpressionCommand(Path workspaceRoot, String query, String starlarkExpression,
            boolean keepGoing) {
        super(workspaceRoot, query, keepGoing);
        setCommandArgs("--output", "starlark", "--starlark:expr", starlarkExpression);
    }

    @Override
    protected String doGenerateResult() throws IOException {
        return readString(getStdOutFile(), Charset.defaultCharset());
    }

    @Override
    public List<String> prepareCommandLine(BazelVersion bazelVersion) throws IOException {
        // redirect output to file for parsing
        var stdoutFile = createTempFile("bazel_cquery_stdout_", ".starlark.txt");
        setRedirectStdOutToFile(stdoutFile);

        // prepare regular query command line
        return super.prepareCommandLine(bazelVersion);
    }
}
