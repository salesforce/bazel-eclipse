package com.salesforce.bazel.sdk.workspace.test;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import com.salesforce.bazel.sdk.path.FSPathHelper;

/**
 * Descriptor for a manufactured bazel package in a test workspace.
 */
public class TestBazelPackageDescriptor {

    public TestBazelWorkspaceDescriptor parentWorkspaceDescriptor;

    // Ex: projects/libs/javalib0  (no leading //, no trailing rule name)
    public String packagePath;

    // Ex: javalib0
    public String packageName;

    // Ex: /tmp/workspaces/test_workspace1/projects/libs/javalib0
    public File diskLocation;

    // Associated targets
    public Map<String, TestBazelTargetDescriptor> targets = new TreeMap<>();

    public TestBazelPackageDescriptor(TestBazelWorkspaceDescriptor parentWorkspace, String packagePath,
            String packageName, File diskLocation, boolean trackState) {

        if (packagePath.contains(FSPathHelper.WINDOWS_BACKSLASH)) {
            // Windows bug, someone passed in a Windows path
            throw new IllegalArgumentException(
                "Windows filesystem path passed to TestBazelPackageDescriptor instead of the Bazel package path: "
                        + packagePath);
        }

        parentWorkspaceDescriptor = parentWorkspace;
        this.packagePath = packagePath;
        this.packageName = packageName;
        this.diskLocation = diskLocation;

        if (trackState) {
            // we normally want to keep track of all the packages we have created, but in some test cases
            // we create Java packages that we don't expect to import (e.g. in a nested workspace that isn't
            // imported) in such cases trackState will be false
            parentWorkspaceDescriptor.createdPackages.put(packagePath, this);
        }
    }
}
