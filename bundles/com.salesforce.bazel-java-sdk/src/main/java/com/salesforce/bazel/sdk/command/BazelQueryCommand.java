package com.salesforce.bazel.sdk.command;

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.writeString;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.salesforce.bazel.sdk.BazelVersion;

/**
 * <code>bazel query</code>
 * <p>
 * Uses <code>--query_file</code> to avoid escaping issues with the query.
 * </p>
 *
 * @param <R>
 *            the query output result
 */
public abstract class BazelQueryCommand<R> extends BazelCommand<R> {

    private String query;
    private boolean keepGoing;

    public BazelQueryCommand(Path workspaceRoot, String query, boolean keepGoing) {
        super("query", workspaceRoot);
        this.query = query;
        this.keepGoing = keepGoing;
    }

    public String getQuery() {
        return query;
    }

    @Override
    public List<String> prepareCommandLine(BazelVersion bazelVersion) throws IOException {
        List<String> commandLine = super.prepareCommandLine(bazelVersion);

        if (keepGoing) {
            commandLine.add("--keep_going");
        }

        // write query into file and use that
        var queryFile = createTempFile("bazel_query_", ".txt");
        writeString(queryFile, query);

        commandLine.add("--query_file");
        commandLine.add(queryFile.toString());

        return commandLine;
    }
}
