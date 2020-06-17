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
package com.salesforce.bazel.eclipse.command.mock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;
import com.salesforce.bazel.eclipse.command.BazelProcessBuilder;
import com.salesforce.bazel.eclipse.command.Command;

public class MockCommand implements Command {

    public MockCommand(List<String> commandTokens) {
        this.commandTokens = commandTokens;
    }
    
    public List<String> commandTokens;
    public List<String> outputLines;
    public List<String> errorLines;
    
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

}
