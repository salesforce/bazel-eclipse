package com.salesforce.bazel.sdk.command;

import java.io.IOException;
import java.nio.file.Path;

class DummyCommand extends BazelCommand<Void> {
    public DummyCommand() {
        super("dummy", Path.of(""));
    }

    @Override
    public Void processResult(int exitCode, String stdout, String stderr) throws IOException {
        return null;
    }

}