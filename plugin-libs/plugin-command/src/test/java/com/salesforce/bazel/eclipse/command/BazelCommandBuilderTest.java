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
package com.salesforce.bazel.eclipse.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.salesforce.bazel.eclipse.abstractions.BazelAspectLocation;
import com.salesforce.bazel.eclipse.abstractions.BazelCommandArgs;
import com.salesforce.bazel.eclipse.abstractions.CommandConsole;
import com.salesforce.bazel.eclipse.abstractions.CommandConsoleFactory;
import com.salesforce.bazel.eclipse.model.BazelLabel;
import com.salesforce.bazel.eclipse.model.TargetKind;

public class BazelCommandBuilderTest {

    // the code under test wants a real executable (checks whether it exists)
    private static final String BAZEL_EXECUTABLE = System.getProperty("java.home") + "/bin/java";
    private static final int DEBUG_PORT = 1234;

    @Test
    public void testBuildRunCommand() {
        BazelWorkspaceCommandRunner bazelCommandRunner = getBazelCommandRunner();
        BazelLabel label = new BazelLabel("//a/b/c");
        TargetKind targetKind = TargetKind.JAVA_BINARY;
        BazelCommandBuilder cmdBuilder =
                new BazelCommandBuilder(bazelCommandRunner, label, targetKind, Collections.emptyMap());

        List<String> cmdTokens = cmdBuilder.build().getProcessBuilder().command();

        assertEquals(BAZEL_EXECUTABLE, cmdTokens.get(0));
        assertEquals("run", cmdTokens.get(1));
        assertTrue(cmdTokens.contains(label.getLabel()));
        assertFalse(cmdTokens.contains("debug"));
    }

    @Test
    public void testBuildRunCommandWithDebug() {
        BazelWorkspaceCommandRunner bazelCommandRunner = getBazelCommandRunner();
        BazelLabel label = new BazelLabel("//a/b/c");
        TargetKind targetKind = TargetKind.JAVA_BINARY;
        BazelCommandBuilder cmdBuilder =
                new BazelCommandBuilder(bazelCommandRunner, label, targetKind, Collections.emptyMap())
                        .setDebugMode(true, "localhost", DEBUG_PORT);

        List<String> cmdTokens = cmdBuilder.build().getProcessBuilder().command();

        assertEquals(BAZEL_EXECUTABLE, cmdTokens.get(0));
        assertEquals("run", cmdTokens.get(1));
        assertTrue(cmdTokens.contains(label.getLabel()));
        assertTrue(cmdTokens.toString().contains(
            "-agentlib:jdwp=transport=dt_socket,address=localhost:" + DEBUG_PORT + ",server=y,suspend=y"));
    }

    @Test
    public void testBuildTestCommand() {
        BazelWorkspaceCommandRunner bazelCommandRunner = getBazelCommandRunner();
        BazelLabel label = new BazelLabel("//a/b/c");
        TargetKind targetKind = TargetKind.JAVA_TEST;
        BazelCommandBuilder cmdBuilder =
                new BazelCommandBuilder(bazelCommandRunner, label, targetKind, Collections.emptyMap());

        List<String> cmdTokens = cmdBuilder.build().getProcessBuilder().command();

        assertEquals(BAZEL_EXECUTABLE, cmdTokens.get(0));
        assertEquals("test", cmdTokens.get(1));
        assertTrue(cmdTokens.contains(label.getLabel()));
        assertFalse(cmdTokens.toString().contains("debug"));
    }

    @Test
    public void testBuildTestCommandWithFilter() {
        BazelWorkspaceCommandRunner bazelCommandRunner = getBazelCommandRunner();
        BazelLabel label = new BazelLabel("//a/b/c");
        TargetKind targetKind = TargetKind.JAVA_TEST;
        Map<String, String> bazelArgs =
                Collections.singletonMap(BazelCommandArgs.TEST_FILTER.getName(), "someBazelTestFilter");
        BazelCommandBuilder cmdBuilder = new BazelCommandBuilder(bazelCommandRunner, label, targetKind, bazelArgs);

        List<String> cmdTokens = cmdBuilder.build().getProcessBuilder().command();

        assertEquals(BAZEL_EXECUTABLE, cmdTokens.get(0));
        assertEquals("test", cmdTokens.get(1));
        assertTrue(cmdTokens.contains("--test_filter=someBazelTestFilter"));
        assertTrue(cmdTokens.contains(label.getLabel()));
        assertFalse(cmdTokens.toString().contains("debug"));
    }

    @Test
    public void testBuildTestCommandWithDebugEnabled() {
        BazelWorkspaceCommandRunner bazelCommandRunner = getBazelCommandRunner();
        BazelLabel label = new BazelLabel("//a/b/c");
        TargetKind targetKind = TargetKind.JAVA_TEST;
        BazelCommandBuilder cmdBuilder =
                new BazelCommandBuilder(bazelCommandRunner, label, targetKind, Collections.emptyMap())
                        .setDebugMode(true, "localhost", DEBUG_PORT);

        List<String> cmdTokens = cmdBuilder.build().getProcessBuilder().command();

        assertEquals(BAZEL_EXECUTABLE, cmdTokens.get(0));
        assertEquals("test", cmdTokens.get(1));
        assertTrue(cmdTokens.contains(label.getLabel()));
        assertTrue(cmdTokens.toString().contains("debug"));
        assertTrue(cmdTokens.contains("--test_arg=--wrapper_script_flag=--debug=localhost:" + DEBUG_PORT));
    }

    BazelWorkspaceCommandRunner getBazelCommandRunner() {
        BazelAspectLocation bazelAspectLocation = new BazelAspectLocation() {
            @Override
            public String getAspectLabel() {
                return null;
            }

            @Override
            public File getAspectDirectory() {
                return null;
            }
        };
        CommandConsoleFactory commandConsoleFactory = new CommandConsoleFactory() {
            @Override
            public CommandConsole get(String name, String title) throws IOException {
                return null;
            }
        };
        CommandBuilder commandBuilder = new ShellCommandBuilder(commandConsoleFactory);
        BazelCommandFacade bazelCommandFacade = new BazelCommandFacade(bazelAspectLocation, commandBuilder, commandConsoleFactory);
        bazelCommandFacade.setBazelExecutablePath(BAZEL_EXECUTABLE);
        return new BazelWorkspaceCommandRunner(bazelCommandFacade, bazelAspectLocation, commandBuilder, commandConsoleFactory,
                new File(""));
    }

}
