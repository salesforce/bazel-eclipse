package com.salesforce.bazel.sdk.workspace.test;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import com.salesforce.bazel.sdk.path.BazelPathHelper;

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
            String packageName, File diskLocation) {

        if (packagePath.contains(BazelPathHelper.WINDOWS_BACKSLASH)) {
            // Windows bug, someone passed in a Windows path
            throw new IllegalArgumentException(
                    "Windows filesystem path passed to TestBazelPackageDescriptor instead of the Bazel package path: "
                            + packagePath);
        }

        parentWorkspaceDescriptor = parentWorkspace;
        this.packagePath = packagePath;
        this.packageName = packageName;
        this.diskLocation = diskLocation;

        parentWorkspaceDescriptor.createdPackages.put(packagePath, this);
    }
}
