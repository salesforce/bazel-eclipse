package com.salesforce.bazel.eclipse.mock;

import java.io.IOException;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.salesforce.bazel.eclipse.command.Command;

public class MockCommand implements Command {

    public List<String> outputLines;
    public List<String> errorLines;
    
    @Override
    public int run() throws IOException, InterruptedException {
        return 0;
    }

    @Override
    public ImmutableList<String> getSelectedErrorLines() {
        if (errorLines != null) {
            return ImmutableList.copyOf(errorLines);
        }
        return ImmutableList.of();
    }

    @Override
    public ProcessBuilder getProcessBuilder() {
        return null;
    }

    @Override
    public ImmutableList<String> getSelectedOutputLines() {
        if (outputLines != null) {
            return ImmutableList.copyOf(outputLines);
        }
        return ImmutableList.of();
    }

}
