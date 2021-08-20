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
package com.salesforce.bazel.sdk.workspace;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.sdk.init.JvmRuleInit;
import com.salesforce.bazel.sdk.model.BazelPackageInfo;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceDescriptor;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

/**
 * This test makes sure the workspace scanner correctly identifies packages to import. It does NOT verify the internal
 * details of those scanned project (e.g. the classpath is correctly computed).
 */
public class BazelWorkspaceScannerTest {
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Before
    public void setup() {
        JvmRuleInit.initialize();
    }

    @Test
    public void testHappyPath() throws Exception {
        File tmpWorkspaceDir = tmpFolder.newFolder().getCanonicalFile();
        File tmpOutputBase = tmpFolder.newFolder().getCanonicalFile();

        //        File tmpWorkspaceDir = new File("/tmp/bazeltest/ws"); // $SLASH_OK sample code
        //        File tmpOutputBase = new File("/tmp/bazeltest/bin"); // $SLASH_OK sample code

        TestOptions testOptions = new TestOptions().numberOfJavaPackages(5).numberGenrulePackages(2);

        TestBazelWorkspaceDescriptor descriptor =
                new TestBazelWorkspaceDescriptor(tmpWorkspaceDir, tmpOutputBase).testOptions(testOptions);

        new TestBazelWorkspaceFactory(descriptor).build();

        BazelWorkspaceScanner scanner = new BazelWorkspaceScanner();
        BazelPackageInfo rootWorkspacePackage = scanner.getPackages(tmpWorkspaceDir, null);

        assertEquals(5, scanner.projects.size());
        assertEquals(5, rootWorkspacePackage.getChildPackageInfos().size());
    }


    // UNHAPPY PATHS

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyDirectory() throws Exception {
        File tmpWorkspaceDir = tmpFolder.newFolder().getCanonicalFile();

        BazelWorkspaceScanner scanner = new BazelWorkspaceScanner();
        scanner.getPackages(tmpWorkspaceDir, null);
    }

    @Test
    public void testNoJavaProjects() throws Exception {
        File tmpWorkspaceDir = tmpFolder.newFolder().getCanonicalFile();
        File tmpOutputBase = tmpFolder.newFolder().getCanonicalFile();
        TestOptions testOptions = new TestOptions().numberOfJavaPackages(0).numberGenrulePackages(2);

        TestBazelWorkspaceDescriptor descriptor =
                new TestBazelWorkspaceDescriptor(tmpWorkspaceDir, tmpOutputBase).testOptions(testOptions);
        new TestBazelWorkspaceFactory(descriptor).build();

        BazelWorkspaceScanner scanner = new BazelWorkspaceScanner();
        BazelPackageInfo rootWorkspacePackage = scanner.getPackages(tmpWorkspaceDir, null);

        assertEquals(0, scanner.projects.size());
        assertEquals(0, rootWorkspacePackage.getChildPackageInfos().size());
    }

    @Test
    public void testNoProjects() throws Exception {
        File tmpWorkspaceDir = tmpFolder.newFolder().getCanonicalFile();
        File workspaceFile = new File(tmpWorkspaceDir, "WORKSPACE").getCanonicalFile();
        workspaceFile.createNewFile();

        BazelWorkspaceScanner scanner = new BazelWorkspaceScanner();
        BazelPackageInfo rootWorkspacePackage = scanner.getPackages(tmpWorkspaceDir, null);

        assertEquals(0, scanner.projects.size());
        assertEquals(0, rootWorkspacePackage.getChildPackageInfos().size());
    }
}
