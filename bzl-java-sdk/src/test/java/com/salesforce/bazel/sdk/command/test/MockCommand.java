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
import java.util.List;
import java.util.Map;

import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;
import com.salesforce.bazel.sdk.command.BazelProcessBuilder;
import com.salesforce.bazel.sdk.command.Command;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

public class MockCommand implements Command {

    // almost all tests should fail if an unknown target (not found in the underlying test workspace) is passed to a command
    // if you are testing failure cases, this can be set to "false" so that a Bazel error is simulated instead
    public static final String TESTOPTION_FAILTESTFORUNKNOWNTARGET = "FAILTESTFORUNKNOWNTARGET";
    static {
        TestOptions.advertise(TESTOPTION_FAILTESTFORUNKNOWNTARGET);
    }

    // INPUTS
    public List<String> commandTokens;
    public Map<String, String> testOptions;
    public TestBazelWorkspaceFactory testWorkspaceFactory;

    // OUTPUTS
    public List<String> outputLines;
    public List<String> errorLines;

    public MockCommand(List<String> commandTokens, Map<String, String> testOptions,
            TestBazelWorkspaceFactory testWorkspaceFactory) {
        this.commandTokens = commandTokens;
        this.testOptions = testOptions;
        this.testWorkspaceFactory = testWorkspaceFactory;
    }

    public void addSimulatedOutputToCommandStdOut(String... someStrings) {
        this.outputLines = new ArrayList<>();
        for (String someString : someStrings) {
            this.outputLines.add(someString);
        }
        this.errorLines = new ArrayList<>();
    }

    public void addSimulatedOutputToCommandStdErr(String... someStrings) {
        this.errorLines = new ArrayList<>();
        for (String someString : someStrings) {
            this.errorLines.add(someString);
        }
        this.outputLines = new ArrayList<>();
    }

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
    public BazelProcessBuilder getProcessBuilder() {
        BazelProcessBuilder pb = Mockito.mock(BazelProcessBuilder.class);

        // you may need to add more mocking behaviors
        Mockito.when(pb.command()).thenReturn(commandTokens);

        return pb;
    }

    @Override
    public ImmutableList<String> getSelectedOutputLines() {
        if (outputLines != null) {
            return ImmutableList.copyOf(outputLines);
        }
        return ImmutableList.of();
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

        if (target.startsWith("//")) {
            target = target.substring(2);
        }
        String packageLabel = target;
        String ruleName = "*";
        int colonIndex = target.indexOf(":");
        if (colonIndex >= 0) {
            packageLabel = target.substring(0, colonIndex);
            ruleName = target.substring(colonIndex + 1);
        }

        if (testWorkspaceFactory.workspaceDescriptor.getCreatedPackageByName(packageLabel) == null) {
            returnFalseOrThrow(target);
        }
        if (!ruleName.equals("*")) {
            // * ruleName is always valid, but if there is a specific rule we need to check
            if (testWorkspaceFactory.workspaceDescriptor.createdTargets.get(target) == null) {
                returnFalseOrThrow(target);
            }
        }

        return true;
    }

    /**
     * Will normally throw an exception if the test causes an unknown Bazel target to be used in a Bazel command.
     * Usually this means that the command was malformed, or there is a bug in the mocking layer.
     */
    private boolean returnFalseOrThrow(String target) {
        String failOnMissingStr = testOptions.get(TESTOPTION_FAILTESTFORUNKNOWNTARGET);
        if ("false".equals(failOnMissingStr)) {
            return false;
        }
        throw new IllegalArgumentException("Bazel command attempted to process an unknown target [" + target + "]");
    }
}
