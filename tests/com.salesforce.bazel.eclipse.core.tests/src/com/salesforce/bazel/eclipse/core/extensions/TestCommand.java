package com.salesforce.bazel.eclipse.core.extensions;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;

import com.salesforce.bazel.sdk.command.BazelCommand;

/**
 * A command to be used by testing an executor.
 */
class TestCommand extends BazelCommand<Integer> {
    public TestCommand() {
        this(Path.of(System.getProperty("user.home")));
    }

    public TestCommand(Path workingDirectory, String... commandArgs) {
        super("dummy", workingDirectory, "test command");
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

    @Override
    public void setRedirectStdOutToFile(Path stdOutFile) {
        super.setRedirectStdOutToFile(stdOutFile);
    }
}