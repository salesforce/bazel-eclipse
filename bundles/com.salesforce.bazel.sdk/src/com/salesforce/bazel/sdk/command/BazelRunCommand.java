/*-
 * Copyright (c) 2023 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - adapted from M2E, JDT or other Eclipse project
 */
package com.salesforce.bazel.sdk.command;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.google.idea.blaze.base.model.primitives.Label;
import com.salesforce.bazel.sdk.BazelVersion;

/**
 * <code>bazel run</code>
 */
public class BazelRunCommand extends BazelCommand<Object> {

    private final List<String> targetArgs;
    private final Label target;

    public BazelRunCommand(Label target, List<String> targetArgs, Path workingDirectory, String purpose) {
        super("run", workingDirectory, purpose);
        this.target = target;
        this.targetArgs = List.copyOf(targetArgs);
    }

    @Override
    protected Object doGenerateResult() throws IOException {
        // ignore
        return new Object();
    }

    /**
     * @return the target
     */
    public Label getTarget() {
        return target;
    }

    /**
     * @return the targetArgs
     */
    public List<String> getTargetArgs() {
        return targetArgs;
    }

    @Override
    public List<String> prepareCommandLine(BazelVersion bazelVersion) throws IOException {
        var commandLine = super.prepareCommandLine(bazelVersion);
        commandLine.add(getTarget().toString());
        var targetArgs = getTargetArgs();
        if (!targetArgs.isEmpty()) {
            commandLine.add("--");
            commandLine.addAll(targetArgs);
        }
        return commandLine;
    }

}
