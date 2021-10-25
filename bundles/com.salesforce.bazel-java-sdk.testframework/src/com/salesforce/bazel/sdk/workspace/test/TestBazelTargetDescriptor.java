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

import com.salesforce.bazel.sdk.model.BazelLabel;

/**
 * Descriptor for a manufactured target (java_library, java_test, etc) in a manufactured bazel package in a test
 * workspace.
 */
public class TestBazelTargetDescriptor {

    public TestBazelPackageDescriptor parentPackage;

    // Ex: projects/libs/javalib0:javalib0
    public String targetPath;

    // Ex: javalib0
    public String targetName;

    // Ex: java_library
    public String targetType;

    public TestBazelTargetDescriptor(TestBazelPackageDescriptor parentPackage, String targetName, String targetType) {
        this.parentPackage = parentPackage;
        targetPath = parentPackage.packagePath + BazelLabel.BAZEL_COLON + targetName;
        this.targetName = targetName;
        this.targetType = targetType;

        this.parentPackage.parentWorkspaceDescriptor.createdTargets.put(targetPath, this);
        this.parentPackage.targets.put(targetPath, this);
    }

}
