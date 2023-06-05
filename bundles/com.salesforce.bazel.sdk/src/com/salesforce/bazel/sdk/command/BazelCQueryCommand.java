package com.salesforce.bazel.sdk.command;

import java.nio.file.Path;

/**
 * <code>bazel cquery</code>
 * <p>
 * Uses <code>--query_file</code> to avoid escaping issues with the query.
 * </p>
 *
 * @param <R>
 *            the query output result
 */
public abstract class BazelCQueryCommand<R> extends BazelQueryCommand<R> {

    public BazelCQueryCommand(Path workspaceRoot, String query, boolean keepGoing) {
        super(QueryCommand.cquery, workspaceRoot, query, keepGoing);
    }

}
