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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.salesforce.bazel.eclipse.model.BazelLabel;
import com.salesforce.bazel.eclipse.model.TargetKind;

/**
 * Manufactures Bazel Command instances, used for launching Bazel (ie through Launch Configs).
 * 
 * @author stoens
 * @since the great late rainy season of 2019
 */
public class BazelCommandBuilder {

    private final BazelWorkspaceCommandRunner bazelRunner;
    private final BazelLabel bazelLabel;
    private final TargetKind targetKind;
    private final Map<String, String> bazelArgs;

    private boolean isDebugMode;
    private String debugHost;
    private int debugPort;

    public BazelCommandBuilder(BazelWorkspaceCommandRunner bazelRunner, BazelLabel bazelLabel, TargetKind targetKind,
            Map<String, String> bazelArgs) {
        this.bazelRunner = Objects.requireNonNull(bazelRunner);
        this.bazelLabel = Objects.requireNonNull(bazelLabel);
        this.targetKind = Objects.requireNonNull(targetKind);
        this.bazelArgs = Objects.requireNonNull(bazelArgs);
    }

    public BazelCommandBuilder setDebugMode(boolean isDebugMode, String debugHost, int debugPort) {
        this.isDebugMode = isDebugMode;
        this.debugHost = debugHost;
        this.debugPort = debugPort;
        return this;
    }

    public Command build() {
        List<String> args = new ArrayList<>();
        if (isDebugMode) {
            if (targetKind.isTestable()) {
                args.add("--test_arg=--wrapper_script_flag=--debug=" + debugHost + ":" + debugPort);
            } else {
                args.add(String.format("--jvmopt='-agentlib:jdwp=transport=dt_socket,address=%s:%s,server=y,suspend=y'",
                    debugHost, debugPort));
            }
        }

        for (Map.Entry<String, String> arg : bazelArgs.entrySet()) {
            args.add(arg.getKey() + "=" + arg.getValue());
        }

        try {
            return targetKind.isTestable()
                    ? bazelRunner.getBazelTestCommand(Collections.singletonList(bazelLabel.toString()), args)
                    : bazelRunner.getBazelRunCommand(Collections.singletonList(bazelLabel.toString()), args);
        } catch (IOException | BazelCommandLineToolConfigurationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
