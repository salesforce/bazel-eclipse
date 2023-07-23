package com.salesforce.bazel.sdk.command;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A command to be used by testing an executor.
 */
class TestCommand extends BazelCommand<Integer> {
    public TestCommand() {
        this(Path.of(System.getProperty("user.home")));
    }

    public TestCommand(Path workingDirectory, String... commandArgs) {
        super("dummy", workingDirectory, "testing");
        setCommandArgs(commandArgs);
    }

    @Override
    protected Integer doGenerateResult() throws IOException {
        fail("This should never be called!");
        return null;
    }

    @Override
    public Integer generateResult(int exitCode) throws IOException {
        return exitCode;
    }
}