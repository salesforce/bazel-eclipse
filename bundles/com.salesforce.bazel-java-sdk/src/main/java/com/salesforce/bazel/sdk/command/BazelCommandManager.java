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
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import com.salesforce.bazel.sdk.aspect.BazelAspectLocation;
import com.salesforce.bazel.sdk.console.CommandConsoleFactory;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;

/**
 * API for calling bazel commands.
 */
public class BazelCommandManager {
    private static final LogHelper LOG = LogHelper.log(BazelCommandManager.class);

    private final BazelAspectLocation aspectLocation;
    private final CommandBuilder commandBuilder;
    private final CommandConsoleFactory consoleFactory;

    /**
     * BazelWorkspaceCommandRunner instance that is not tied to a workspace. Used to check the Bazel version, and to do
     * a workspace lookup given a random file system directory (since we don't know the workspace in advance, we can't
     * use a workspace specific runner for that).
     * <p>
     * Be careful about adding new operations using this instance. Prefer to use an instance attached to workspace
     * instead, as that can pass in workspace specific options when running the command.
     */
    private final BazelWorkspaceCommandRunner genericCommandRunner;

    /**
     * The set of workspace specific command runners. The key is the File workspaceRoot. We don't yet support multiple
     * Bazel workspaces, but when we do this will be important.
     */
    private final Map<File, BazelWorkspaceCommandRunner> workspaceCommandRunners = new TreeMap<>();

    /**
     * Create a {@link BazelCommandManager} object, providing the implementation for locating aspect and getting console
     * streams.
     */
    public BazelCommandManager(BazelAspectLocation aspectLocation, CommandBuilder commandBuilder,
            CommandConsoleFactory consoleFactory, File bazelExecutablePath) {
        this.aspectLocation = aspectLocation;
        this.commandBuilder = commandBuilder;
        this.consoleFactory = consoleFactory;

        BazelWorkspaceCommandRunner.setBazelExecutablePath(bazelExecutablePath.getAbsolutePath());
        genericCommandRunner = new BazelWorkspaceCommandRunner(bazelExecutablePath, commandBuilder);
    }

    // COMMAND RUNNERS

    /**
     * Provides a generic command runner not associated with any workspace. The set of commands that will work correctly
     * using this runner is very limited. Getting the bazel executable version is one.
     */
    public BazelWorkspaceCommandRunner getGlobalCommandRunner() {
        return genericCommandRunner;
    }

    /**
     * Returns a {@link BazelWorkspaceCommandRunner} for the given Bazel workspace. It looks for the enclosing workspace
     * and returns the instance that corresponds to it. If not in a Bazel workspace, returns null.
     */
    public BazelWorkspaceCommandRunner getWorkspaceCommandRunner(BazelWorkspace bazelWorkspace) {
        if (bazelWorkspace == null) {
            return null;
        }
        File bazelWorkspaceRootDirectory = bazelWorkspace.getBazelWorkspaceRootDirectory();
        BazelWorkspaceCommandRunner workspaceCommandRunner = workspaceCommandRunners.get(bazelWorkspaceRootDirectory);
        if (workspaceCommandRunner == null) {
            File bazelExecutable = null;
            String execPath = "";
            try {
                execPath = getBazelExecutablePath();
                bazelExecutable = new File(execPath);
            } catch (Exception ex) {
                LOG.error("Could not locate the Bazel executable at path [{}]", ex, execPath);
                return null;
            }

            workspaceCommandRunner = new BazelWorkspaceCommandRunner(bazelExecutable, aspectLocation, commandBuilder,
                consoleFactory, bazelWorkspaceRootDirectory);
            workspaceCommandRunners.put(bazelWorkspaceRootDirectory, workspaceCommandRunner);
        }
        return workspaceCommandRunner;
    }

    // BAZEL EXECUTABLE

    /**
     * Set the path to the Bazel binary. Allows the user to override the default via the Preferences ui.
     */
    public synchronized void setBazelExecutablePath(String bazelExectuablePath) {
        BazelWorkspaceCommandRunner.setBazelExecutablePath(bazelExectuablePath);
    }

    /**
     * Get the file system path to the Bazel executable. Might be the hard coded default for the platform (see
     * getDefaultBazelExecutablePath) but this method may apply a user specified override and is thus the preferred
     * method to call.
     *
     * @return the file system path to the Bazel executable
     * @throws BazelCommandLineToolConfigurationException
     */
    public String getBazelExecutablePath() throws BazelCommandLineToolConfigurationException {
        return BazelWorkspaceCommandRunner.getBazelExecutablePath();
    }

    /**
     * Returns the default path for Bazel for the runtime platform. This is a hard coded default, which is not as good
     * as getBazelExecutablePath() which may return a better answer.
     */
    public static String getDefaultBazelExecutablePath() {
        String path = "/usr/local/bin/bazel"; // $SLASH_OK
        if (System.getProperty("os.name").contains("Windows")) {
            path = "C:\\msys64\\usr\\bin";
        }
        return path;
    }

}
