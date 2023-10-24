package com.salesforce.bazel.eclipse.core.model;

import java.nio.file.Path;

import org.eclipse.core.runtime.CoreException;

import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;

/**
 * Implementation of {@link BlazeInfo} for a {@link BazelWorkspace}.
 * <p>
 * Note, the information captures is static and not dynamic. Objects should be short lived and not hold on for a very
 * long time.
 * </p>
 */
public class BazelWorkspaceBlazeInfo implements BlazeInfo {

    private final ExecutionRootPath bazelBin;
    private final ExecutionRootPath bazelGenfiles;
    private final ExecutionRootPath bazelTestlogs;
    private final Path executionRoot;
    private final Path outputBase;

    public BazelWorkspaceBlazeInfo(BazelWorkspace bazelWorkspace) throws CoreException {
        var info = bazelWorkspace.getInfo();
        bazelBin = new ExecutionRootPath(info.getBazelBin().toPath());
        bazelGenfiles = new ExecutionRootPath(info.getBazelGenfiles().toPath());
        bazelTestlogs = new ExecutionRootPath(info.getBazelTestlogs().toPath());
        executionRoot = info.getExcutionRoot().toPath();
        outputBase = info.getOutputBase().toPath();
    }

    @Override
    public ExecutionRootPath getBlazeBin() {
        return bazelBin;
    }

    @Override
    public ExecutionRootPath getBlazeGenfiles() {
        return bazelGenfiles;
    }

    @Override
    public ExecutionRootPath getBlazeTestlogs() {
        return bazelTestlogs;
    }

    @Override
    public Path getExecutionRoot() {
        return executionRoot;
    }

    @Override
    public Path getOutputBase() {
        return outputBase;
    }

    @Override
    public String toString() {
        return "BazelWorkspaceBlazeInfo [bazelBin=" + bazelBin + ", bazelGenfiles=" + bazelGenfiles + ", bazelTestlogs="
                + bazelTestlogs + ", executionRoot=" + executionRoot + ", outputBase=" + outputBase + "]";
    }
}
