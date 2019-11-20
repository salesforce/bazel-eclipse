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

package com.salesforce.bazel.eclipse.command;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.salesforce.bazel.eclipse.abstractions.CommandConsoleFactory;

/**
 * Utility class for running a version check on the Bazel executable. The location of the executable is configurable in
 * the Bazel preferences in Eclipse. See BazelPreferenceInitializer.
 * <p>
 * TODO rework BazelVersionCheckCommand to use the generic command runner
 * <p>
 * TODO this is only run when the user updates the bazel path preference. We should do it proactively at other times (at
 * startup, etc)
 */
class BazelVersionCheckCommand {

    /**
     * Minimum bazel version needed to work with this plugin (currently 0.18.0)
     */
    public static final int[] MINIMUM_BAZEL_VERSION = { 0, 24, 1 };

    private static final Pattern VERSION_PATTERN = Pattern.compile("^([0-9]+)\\.([0-9]+)\\.([0-9]+)([^0-9].*)?$");

    private final File bazelWorkspaceRoot;
    private final CommandConsoleFactory consoleFactory;

    public BazelVersionCheckCommand(File bazelWorkspaceRoot, CommandConsoleFactory consoleFactory) {
        this.bazelWorkspaceRoot = bazelWorkspaceRoot;
        this.consoleFactory = consoleFactory;
    }

    /**
     * Check the version of Bazel: throws an exception if the version is incorrect or the path does not point to a Bazel
     * binary.
     *
     * @param bazelExecutablePath
     *            the file system path to the Bazel executable
     */
    public void checkVersion(String bazelExecutablePath) throws BazelCommandLineToolConfigurationException {
        File path = new File(bazelExecutablePath);
        if (!path.exists()) {
            throw new BazelCommandLineToolConfigurationException.BazelNotFoundException(path.getAbsolutePath());
        }
        if (!path.canExecute()) {
            throw new BazelCommandLineToolConfigurationException.BazelNotExecutableException(path.getAbsolutePath());
        }
        try {
            Command command = ShellCommand.builder(consoleFactory).setConsoleName(null).setDirectory(bazelWorkspaceRoot)
                    .addArguments(bazelExecutablePath, "version")
                    .setStdoutLineSelector((s) -> s.startsWith("Build label:") ? s.substring(13) : null).build();
            if (command.run() != 0) {
                throw new BazelCommandLineToolConfigurationException.BazelNotExecutableException(
                        path.getAbsolutePath());
            }
            List<String> result = command.getSelectedOutputLines();
            if (result.size() != 1) {
                throw new BazelCommandLineToolConfigurationException.BazelTooOldException("unknown",
                        path.getAbsolutePath());
            }
            String version = result.get(0);
            Matcher versionMatcher = VERSION_PATTERN.matcher(version);
            if (versionMatcher == null || !versionMatcher.matches()) {
                throw new BazelCommandLineToolConfigurationException.BazelTooOldException(version,
                        path.getAbsolutePath());
            }
            int[] versionNumbers = { Integer.parseInt(versionMatcher.group(1)),
                    Integer.parseInt(versionMatcher.group(2)), Integer.parseInt(versionMatcher.group(3)) };
            if (compareVersion(versionNumbers, MINIMUM_BAZEL_VERSION) < 0) {
                throw new BazelCommandLineToolConfigurationException.BazelTooOldException(version,
                        path.getAbsolutePath());
            }
        } catch (IOException | InterruptedException e) {
            throw new BazelCommandLineToolConfigurationException.BazelNotFoundException(path.getAbsolutePath());
        }
    }

    private static int compareVersion(int[] version1, int[] version2) {
        for (int i = 0; i < Math.min(version1.length, version2.length); i++) {
            if (version1[i] < version2[i]) {
                return -1;
            } else if (version1[i] > version2[i]) {
                return 1;
            }
        }
        return Integer.compare(version1.length, version2.length);
    }

}
