/**
 * Copyright (c) 2021, Salesforce.com, Inc. All rights reserved.
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
