package com.salesforce.bazel.eclipse.mock;

import java.io.File;

import com.salesforce.bazel.eclipse.abstractions.BazelAspectLocation;

public class MockBazelAspectLocation implements BazelAspectLocation {
    private File aspectWorkspaceDir;
    
    public MockBazelAspectLocation(File bazelWorkspaceRoot) throws Exception{
        if (!bazelWorkspaceRoot.exists()) {
            throw new IllegalArgumentException("The Bazel workspace root directory has not been created yet.");
        }
        this.aspectWorkspaceDir = new File(bazelWorkspaceRoot, "tools/aspect");
        aspectWorkspaceDir.mkdirs();
        
        File aspectWorkspaceFile = new File(aspectWorkspaceDir, "WORKSPACE");
        aspectWorkspaceFile.createNewFile();
        File aspectBuildFile = new File(aspectWorkspaceDir, "BUILD");
        aspectBuildFile.createNewFile();
        File aspectStarlarkFile = new File(aspectWorkspaceDir, "bzleclipse_aspect.bzl");
        aspectStarlarkFile.createNewFile();
    }
    
    @Override
    public File getAspectDirectory() {
        return this.aspectWorkspaceDir;
    }

    @Override
    public String getAspectLabel() {
        return "//:bzleclipse_aspect.bzl%bzleclipse_aspect";
    }

}
