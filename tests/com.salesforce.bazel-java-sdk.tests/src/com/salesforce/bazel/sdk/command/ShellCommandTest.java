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
 *
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package com.salesforce.bazel.sdk.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.sdk.command.shell.ShellCommand;
import com.salesforce.bazel.sdk.command.shell.ShellEnvironment;
import com.salesforce.bazel.sdk.console.CommandConsole;
import com.salesforce.bazel.sdk.console.CommandConsoleFactory;

/**
 * @{link Command}Test
 *
 *        TODO add tests also for the higher level Command classes like BazelCommandManager
 */
public class ShellCommandTest {

    private static Function<String, String> NON_EMPTY_LINES_SELECTOR = x -> x.trim().isEmpty() ? null : x;

    private static boolean isWindows = false;
    static {
        String osname = System.getProperty("os.name", "Linux");
        isWindows = osname.contains("Windows");
    }

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static class MockCommandConsole implements CommandConsole {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final String name;
        final String title;

        public MockCommandConsole(String name, String title) {
            this.title = title;
            this.name = name;
        }

        @Override
        public OutputStream createOutputStream() {
            return stdout;
        }

        @Override
        public OutputStream createErrorStream() {
            return stderr;
        }
    }

    private class MockConsoleFactory implements CommandConsoleFactory {
        final List<MockCommandConsole> consoles = new LinkedList<>();

        @Override
        public CommandConsole get(String name, String title) throws IOException {
            MockCommandConsole console = new MockCommandConsole(name, title);
            consoles.add(console);
            return console;
        }
    }

    private static class MockShellEnvironment implements ShellEnvironment {
        @Override
        public boolean launchWithBashEnvironment() {
            return false;
        }
    }

    public MockConsoleFactory mockConsoleFactory;
    public MockShellEnvironment mockShellEnvironment;

    @Before
    public void setup() {
        mockConsoleFactory = new MockConsoleFactory();
    }

    @Test
    public void testBashCommandWithStream() throws IOException, InterruptedException {
        if (isWindows) {
            return; // no bash on Windows
        }
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Function<String, String> stdoutSelector = x -> (x.trim().isEmpty() || x.equals("a")) ? null : x;
        Function<String, String> stderrSelector = x -> (x.trim().isEmpty() || x.equals("b")) ? null : x;

        CommandBuilder builder = ShellCommand.builder(mockConsoleFactory, mockShellEnvironment).setConsoleName("test")
                .setDirectory(tempFolder.getRoot()).setStandardError(stderr).setStandardOutput(stdout)
                .setStderrLineSelector(stderrSelector).setStdoutLineSelector(stdoutSelector);
        builder.addArguments("bash", "-c", "echo a; echo b; echo a >&2; echo b >&2");
        Command cmd = builder.build();
        assertEquals(0, cmd.run());
        String stdoutStr = new String(stdout.toByteArray(), StandardCharsets.UTF_8).trim();
        String stderrStr = new String(stderr.toByteArray(), StandardCharsets.UTF_8).trim();

        assertEquals("a", stdoutStr);
        assertEquals("b", stderrStr);
        assertEquals("a", cmd.getSelectedErrorLines().get(0));
        assertEquals("b", cmd.getSelectedOutputLines().get(0));
        assertEquals(1, mockConsoleFactory.consoles.size());
        MockCommandConsole console = mockConsoleFactory.consoles.get(0);
        assertEquals("test", console.name);
        assertEquals("Running bash -c echo a; echo b; echo a >&2; echo b >&2 " + "from " + tempFolder.getRoot(),
            console.title);
        stdoutStr = new String(console.stdout.toByteArray(), StandardCharsets.UTF_8).trim();
        stderrStr = new String(console.stderr.toByteArray(), StandardCharsets.UTF_8).trim();
        assertTrue(stdoutStr.isEmpty());
        assertTrue(stderrStr.isEmpty());
    }

    @Test
    public void testBashCommandNoStream() throws IOException, InterruptedException {
        if (isWindows) {
            return; // no bash on Windows
        }
        CommandBuilder builder =
                ShellCommand.builder(mockConsoleFactory, mockShellEnvironment).setConsoleName(null)
                        .setDirectory(tempFolder.getRoot());
        builder.addArguments("bash", "-c", "echo a; echo b; echo a >&2; echo b >&2");
        builder.setStderrLineSelector(NON_EMPTY_LINES_SELECTOR).setStdoutLineSelector(NON_EMPTY_LINES_SELECTOR);
        Command cmd = builder.build();
        assertEquals(0, cmd.run());
        assertEquals(2, cmd.getSelectedErrorLines().size());
        assertTrue(cmd.getSelectedErrorLines().contains("a"));
        assertTrue(cmd.getSelectedErrorLines().contains("b"));
        assertEquals(2, cmd.getSelectedOutputLines().size());
        assertTrue(cmd.getSelectedOutputLines().contains("a"));
        assertTrue(cmd.getSelectedOutputLines().contains("b"));
    }

    @Test
    public void testBashCommandStreamAllToConsole() throws IOException, InterruptedException {
        if (isWindows) {
            return; // no bash on Windows
        }
        CommandBuilder builder =
                ShellCommand.builder(mockConsoleFactory, mockShellEnvironment).setConsoleName("test")
                        .setDirectory(tempFolder.getRoot());
        builder.addArguments("bash", "-c", "echo a; echo b; echo a >&2; echo b >&2");
        Command cmd = builder.build();
        assertEquals(0, cmd.run());
        MockCommandConsole console = mockConsoleFactory.consoles.get(0);
        assertEquals("test", console.name);
        assertEquals("Running bash -c echo a; echo b; echo a >&2; echo b >&2 " + "from " + tempFolder.getRoot(),
            console.title);
        String stdoutStr = new String(console.stdout.toByteArray(), StandardCharsets.UTF_8).trim();
        String stderrStr = new String(console.stderr.toByteArray(), StandardCharsets.UTF_8).trim();
        assertEquals("a\nb", stdoutStr);
        assertEquals("a\nb", stderrStr);
    }

    @Test
    public void testBashCommandWorkDir() throws IOException, InterruptedException {
        if (isWindows) {
            return; // no pwd on Windows
        }
        CommandBuilder builder =
                ShellCommand.builder(mockConsoleFactory, mockShellEnvironment).setConsoleName(null)
                        .setDirectory(tempFolder.getRoot());
        builder.setStderrLineSelector(NON_EMPTY_LINES_SELECTOR).setStdoutLineSelector(NON_EMPTY_LINES_SELECTOR);
        builder.addArguments("pwd");
        Command cmd = builder.build();
        assertEquals(0, cmd.run());
        assertTrue(cmd.getSelectedErrorLines().isEmpty());
        assertEquals(1, cmd.getSelectedOutputLines().size());
        assertEquals(tempFolder.getRoot().getCanonicalPath(), cmd.getSelectedOutputLines().get(0));
    }
}
