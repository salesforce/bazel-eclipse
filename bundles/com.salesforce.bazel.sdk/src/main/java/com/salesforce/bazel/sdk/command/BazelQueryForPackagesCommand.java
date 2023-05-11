package com.salesforce.bazel.sdk.command;

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.readAllLines;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.salesforce.bazel.sdk.BazelVersion;
import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * <code>bazel query --output package</code>
 */
public class BazelQueryForPackagesCommand extends BazelQueryCommand<Collection<BazelLabel>> {

    public BazelQueryForPackagesCommand(Path workspaceRoot, String query, boolean keepGoing) {
        super(workspaceRoot, query, keepGoing);
        setCommandArgs("--output", "package");
    }

    @Override
    protected Collection<BazelLabel> doGenerateResult() throws IOException {
        List<BazelLabel> result = new ArrayList<>();
        var lines = readAllLines(getStdOutFile());
        for (String line : lines) {
            if (!line.startsWith("@")) {
                line = "//" + line; // the output of 'package' is a relative path, make it absolute to the workspace
            }
            result.add(new BazelLabel(line));
        }
        return result;
    }

    @Override
    public List<String> prepareCommandLine(BazelVersion bazelVersion) throws IOException {
        // redirect output to file for parsing
        var stdoutFile = createTempFile("bazel_query_stdout_", ".bin");
        setRedirectStdOutToFile(stdoutFile);

        // prepare regular query command line
        return super.prepareCommandLine(bazelVersion);
    }
}
