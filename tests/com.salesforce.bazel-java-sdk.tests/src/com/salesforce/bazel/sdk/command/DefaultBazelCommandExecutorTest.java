package com.salesforce.bazel.sdk.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

public class DefaultBazelCommandExecutorTest {

    @Test
    void testWrappingShell() throws Exception {
        DefaultBazelCommandExecutor executor = new DefaultBazelCommandExecutor();

        executor.setWrapExecutionIntoShell(false);
        List<String> commandLine = executor.prepareCommandLine(new DummyCommand());
        assertEquals(List.of("bazel", "dummy"), commandLine);
    }

}
