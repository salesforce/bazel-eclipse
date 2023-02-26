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
 *      Salesforce - Partially adapted and heavily inspired from Eclipse JDT, M2E and PDE
 */
package com.salesforce.bazel.eclipse.core.model;

import static java.lang.String.format;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;

import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target;
import com.salesforce.bazel.eclipse.core.model.execution.BazelModelCommandExecutionService;
import com.salesforce.bazel.sdk.command.BazelQueryForTargetProtoCommand;
import com.salesforce.bazel.sdk.model.BazelLabel;

public final class BazelPackageInfo extends BazelElementInfo {

    private final Path buildFile;
    private final Path workspaceRoot;
    private final BazelPackage bazelPackage;

    private Map<String, Target> indexOfTargetInfoByTargetName;

    public BazelPackageInfo(Path buildFile, Path workspaceRoot, BazelPackage bazelPackage) {
        this.buildFile = buildFile;
        this.workspaceRoot = workspaceRoot;
        this.bazelPackage = bazelPackage;
    }

    public BazelPackage getBazelPackage() {
        return bazelPackage;
    }

    public Path getBuildFile() {
        return buildFile;
    }

    Target getTarget(String targetName) {
        return indexOfTargetInfoByTargetName.get(targetName);
    }

    public Set<String> getTargets() {
        return Collections.unmodifiableSet(indexOfTargetInfoByTargetName.keySet());
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void load(BazelModelCommandExecutionService executionService) throws CoreException {
        // bazel query '"//foo:all"'

        try {
            var queryResult = executionService.executeOutsideWorkspaceLockAsync(
                new BazelQueryForTargetProtoCommand(getWorkspaceRoot(),
                        format("\"//%s:all\"", getBazelPackage().getWorkspaceRelativePath()), true /* keep going */),
                getBazelPackage()).get();
            Map<String, Target> indexOfTargetInfoByTargetName = new HashMap<>();
            for (Target target : queryResult) {
                var targetName = new BazelLabel(target.getRuleOrBuilder().getName()).getTargetName();
                indexOfTargetInfoByTargetName.put(targetName, target);
            }
            this.indexOfTargetInfoByTargetName = indexOfTargetInfoByTargetName;
        } catch (InterruptedException e) {
            throw new OperationCanceledException("cancelled");
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause == null) {
                throw new CoreException(Status.error(
                    format("bazel query failed in workspace '%s' for with unknown reason", workspaceRoot), e));
            }
            throw new CoreException(Status.error(
                format("bazel query failed in workspace '%s': %s", workspaceRoot, cause.getMessage()), cause));
        }
    }

}
