/**
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.bazel.sdk.command.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.mockito.Mockito;

import com.salesforce.bazel.sdk.command.BazelProcessBuilder;
import com.salesforce.bazel.sdk.command.Command;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

public class MockCommand implements Command {

    // INPUTS
    public List<String> commandTokens;
    public TestOptions testOptions;
    public TestBazelWorkspaceFactory testWorkspaceFactory;

    // OUTPUTS
    public List<String> outputLines = new ArrayList<>();
    public List<String> errorLines = new ArrayList<>();

    public MockCommand(List<String> commandTokens, TestOptions testOptions,
            TestBazelWorkspaceFactory testWorkspaceFactory) {
        this.commandTokens = commandTokens;
        this.testOptions = testOptions;
        this.testWorkspaceFactory = testWorkspaceFactory;
    }

    public void addSimulatedOutputToCommandStdOut(String... someStrings) {
        outputLines = new ArrayList<>();
        Collections.addAll(outputLines, someStrings);
    }

    public void addSimulatedOutputToCommandStdOut(List<String> someStrings) {
        outputLines = someStrings;
    }

    public void addSimulatedOutputToCommandStdErr(String... someStrings) {
        errorLines = new ArrayList<>();
        Collections.addAll(errorLines, someStrings);
    }

    public void addSimulatedOutputToCommandStdErr(List<String> someStrings) {
        errorLines = someStrings;
    }

    @Override
    public int run() throws IOException, InterruptedException {
        return 0;
    }

    @Override
    public List<String> getSelectedErrorLines() {
        if (errorLines != null) {
            return errorLines;
        }
        return new ArrayList<>();
    }

    @Override
    public BazelProcessBuilder getProcessBuilder() {
        BazelProcessBuilder pb = Mockito.mock(BazelProcessBuilder.class);

        // you may need to add more mocking behaviors
        Mockito.when(pb.command()).thenReturn(commandTokens);

        return pb;
    }

    @Override
    public List<String> getSelectedOutputLines() {
        if (outputLines != null) {
            return outputLines;
        }
        return new ArrayList<>();
    }

    // HELPERS

    protected String findBazelTargetInArgs() {
        // we expect the target to be the last arg, but that is not always the case
        // sometimes there are args after, prefixed by --
        int numArgs = commandTokens.size();
        String target = commandTokens.get(numArgs - 1);
        for (int i = numArgs - 1; i >= 0; i--) {
            if (commandTokens.get(i).startsWith("--")) {
                continue;
            }
            // we could also add a check that the target is // but in the real world that is not required
            return commandTokens.get(i);
        }
        return target;
    }

    /**
     * Checks if a passed target to a command is valid. This is useful for commands such as build/test that operate on
     * targets in that they can simulate failed build output
     */
    protected boolean isValidBazelTarget(String target) {

        if (target == null) {
            returnFalseOrThrow(target);
        }
        if (target.endsWith(":")) {
            // bug in a mock or the test itself
            throw new IllegalArgumentException("Target [" + target + "] is invalid.");
        }
        if (testWorkspaceFactory.workspaceDescriptor == null) {
            // there is no workspace to validate against, just return true
            return true;
        }

        if (target.startsWith(BazelLabel.BAZEL_ROOT_SLASHES)) {
            target = target.substring(2);
        }
        String packageLabel = target;
        // TODO also need to check for ... and :all
        String ruleName = BazelLabel.BAZEL_WILDCARD_ALLTARGETS_STAR;
        int colonIndex = target.indexOf(BazelLabel.BAZEL_COLON);
        if (colonIndex >= 0) {
            packageLabel = target.substring(0, colonIndex);
            ruleName = target.substring(colonIndex + 1);
        }

        if (testWorkspaceFactory.workspaceDescriptor.getCreatedPackageByName(packageLabel) == null) {
            returnFalseOrThrow(target);
        }
        // * ruleName is always valid, but if there is a specific rule we need to check
        if (!ruleName.equals(BazelLabel.BAZEL_WILDCARD_ALLTARGETS_STAR)
                && (testWorkspaceFactory.workspaceDescriptor.createdTargets.get(target) == null)) {
            returnFalseOrThrow(target);
        }

        return true;
    }

    /**
     * Will normally throw an exception if the test causes an unknown Bazel target to be used in a Bazel command.
     * Usually this means that the command was malformed, or there is a bug in the mocking layer.
     */
    private boolean returnFalseOrThrow(String target) {
        if (!testOptions.failTestForUnknownTarget) {
            return false;
        }
        throw new IllegalArgumentException("Bazel command attempted to process an unknown target [" + target + "]");
    }
}
