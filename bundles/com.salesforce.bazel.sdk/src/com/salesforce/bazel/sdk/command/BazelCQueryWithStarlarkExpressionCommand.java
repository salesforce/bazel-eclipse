package com.salesforce.bazel.sdk.command;

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.readString;
import static java.nio.file.Files.writeString;
import static java.util.Objects.requireNonNull;

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

    private final String starlarkExpression;

    public BazelCQueryWithStarlarkExpressionCommand(Path workspaceRoot, String query, String starlarkExpression,
            boolean keepGoing, String purpose) {
        super(workspaceRoot, query, keepGoing, purpose);
        this.starlarkExpression = requireNonNull(starlarkExpression, "missing Starlark expression");
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

        // save expression into file
        var starlarkFile = createTempFile("bazel_cquery_starlark_file_", ".starlark.txt");
        writeString(starlarkFile, starlarkExpression);

        // addjust command args
        setCommandArgs("--output", "starlark", "--starlark:file", starlarkFile.toString());

        // prepare regular query command line
        return super.prepareCommandLine(bazelVersion);
    }
}
