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
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.salesforce.bazel.sdk.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.BeforeClass;
import org.junit.Test;

import com.salesforce.bazel.sdk.model.BazelPackageInfo;

public class BazelProjectInfoTest {

    private static File WSDIR;
    private static File EMPTYDIR;
    private static File WSFILE;

    /**
     * Relative file system path for the Apple test Bazel package.
     * <ul>
     * <li>projects/libs/apple when test runs on *nix
     * <li>projects\libs\apple when tests run on Windows
     * </ul>
     */
    private static final String APPLE_PROJECT_FS_PATH = "projects" + File.separator + "libs" + File.separator + "apple";

    /**
     * Relative file system path for the Banana test Bazel package.
     * <ul>
     * <li>projects/libs/banana when test runs on *nix
     * <li>projects\libs\banana when tests run on Windows
     * </ul>
     */
    private static final String BANANA_PROJECT_FS_PATH =
            "projects" + File.separator + "libs" + File.separator + "banana";

    @BeforeClass
    public static void setup() throws Exception {
        String tmpdirpath = System.getProperty("java.io.tmpdir", "/tmp");
        WSDIR = new File(tmpdirpath, "BazelProjectInfoTest");
        WSDIR.mkdir();
        WSDIR.deleteOnExit();

        // make an empty directory for a few of the negative tests
        EMPTYDIR = new File(tmpdirpath, "BazelProjectInfoTest_Empty");
        EMPTYDIR.mkdir();
        EMPTYDIR.deleteOnExit();

        WSFILE = new File(WSDIR, "WORKSPACE");
        if (!WSFILE.exists()) {
            WSFILE.createNewFile();
        }
    }

    // ROOT NODE CTOR

    @Test
    public void testCtorValidation_Root_Happy() throws Exception {
        BazelPackageInfo rootNode = new BazelPackageInfo(WSDIR);

        assertTrue(rootNode.isWorkspaceRoot());
        assertNull(rootNode.getParentPackageInfo());
        assertEquals(WSDIR.getCanonicalPath(), rootNode.getWorkspaceRootDirectory().getCanonicalPath());
        assertTrue(rootNode.getWorkspaceFile().exists());

        assertEquals("", rootNode.getBazelPackageFSRelativePath());
        assertEquals("WORKSPACE", rootNode.getBazelPackageFSRelativePathForUI());
        assertEquals("//...", rootNode.getBazelPackageName());
        assertEquals("", rootNode.getBazelPackageNameLastSegment());

        assertTrue(rootNode.getChildPackageInfos().isEmpty());

    }

    @Test(expected = IllegalArgumentException.class)
    public void testCtorValidation_Root_NotNull() throws Exception {
        new BazelPackageInfo(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCtorValidation_Root_NotExists() throws Exception {
        new BazelPackageInfo(new File("/tmp/somebogusdirectory"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCtorValidation_Root_WORKSPACE_NotExists() throws Exception {
        // Workspace dir exists, but is empty
        new BazelPackageInfo(EMPTYDIR);
    }

    // SUBPACKAGE NODE CTOR

    @Test
    public void testCtor_Sub_Happy() throws Exception {
        create_subdir(APPLE_PROJECT_FS_PATH);

        BazelPackageInfo rootNode = new BazelPackageInfo(WSDIR);
        BazelPackageInfo subNode = new BazelPackageInfo(rootNode, APPLE_PROJECT_FS_PATH);

        assertFalse(subNode.isWorkspaceRoot());
        assertEquals(rootNode, subNode.getParentPackageInfo());
        assertTrue(subNode.getWorkspaceFile().exists());

        assertEquals(APPLE_PROJECT_FS_PATH, subNode.getBazelPackageFSRelativePath());
        assertEquals(APPLE_PROJECT_FS_PATH, subNode.getBazelPackageFSRelativePathForUI());
        assertEquals("//projects/libs/apple", subNode.getBazelPackageName());
        assertEquals("apple", subNode.getBazelPackageNameLastSegment());

        assertFalse(rootNode.getChildPackageInfos().isEmpty());
        assertEquals(subNode, rootNode.getChildPackageInfos().iterator().next());
    }

    @Test
    /**
     * Same test as above, but the path is passed with a trailing slash, which should be ignored
     * 
     * @throws Exception
     */
    public void testCtor_Sub_Happy_trailingslash() throws Exception {
        create_subdir(APPLE_PROJECT_FS_PATH);

        BazelPackageInfo rootNode = new BazelPackageInfo(WSDIR);
        BazelPackageInfo subNode = new BazelPackageInfo(rootNode, APPLE_PROJECT_FS_PATH + File.separator);

        assertFalse(subNode.isWorkspaceRoot());
        assertEquals(rootNode, subNode.getParentPackageInfo());
        //assertEquals(subdir.getCanonicalPath(), rootNode.getWorkspaceRootDirectory().getCanonicalPath());
        assertTrue(subNode.getWorkspaceFile().exists());

        assertEquals(APPLE_PROJECT_FS_PATH, subNode.getBazelPackageFSRelativePath());
        assertEquals(APPLE_PROJECT_FS_PATH, subNode.getBazelPackageFSRelativePathForUI());
        assertEquals("//projects/libs/apple", subNode.getBazelPackageName());
        assertEquals("apple", subNode.getBazelPackageNameLastSegment());

        assertFalse(rootNode.getChildPackageInfos().isEmpty());
        assertEquals(subNode, rootNode.getChildPackageInfos().iterator().next());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCtorValidation_Sub_Null() throws Exception {
        new BazelPackageInfo(null, APPLE_PROJECT_FS_PATH);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCtorValidation_Sub_LeadingSlash() throws Exception {
        BazelPackageInfo rootNode = new BazelPackageInfo(WSDIR);

        new BazelPackageInfo(rootNode, File.separator + APPLE_PROJECT_FS_PATH);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCtorValidation_Sub_NotExists() throws Exception {
        BazelPackageInfo rootNode = new BazelPackageInfo(WSDIR);

        // calling subdir() here would create the sub-project dir, but intentionally do not do that

        new BazelPackageInfo(rootNode, "projects" + File.separator + "does_not_exist");
    }

    // EQUALITY

    @Test
    public void testEquality_Root() {
        assertEquals(new BazelPackageInfo(WSDIR), new BazelPackageInfo(WSDIR));
    }

    @Test
    public void testEquality_Sub() throws Exception {
        create_subdir(APPLE_PROJECT_FS_PATH);

        BazelPackageInfo rootNode = new BazelPackageInfo(WSDIR);
        BazelPackageInfo apple = new BazelPackageInfo(rootNode, APPLE_PROJECT_FS_PATH);

        assertEquals(apple, apple);
        assertNotEquals(apple, rootNode);
    }

    // TREE BEHAVIORS

    @Test
    public void testComputeBestParent() throws Exception {
        create_subdir(APPLE_PROJECT_FS_PATH);
        create_subdir(APPLE_PROJECT_FS_PATH + File.separator + "web");
        create_subdir(BANANA_PROJECT_FS_PATH);

        BazelPackageInfo rootNode = new BazelPackageInfo(WSDIR);
        BazelPackageInfo apple = new BazelPackageInfo(rootNode, APPLE_PROJECT_FS_PATH);
        BazelPackageInfo apple_web = new BazelPackageInfo(apple, APPLE_PROJECT_FS_PATH + File.separator + "web");
        BazelPackageInfo banana = new BazelPackageInfo(apple_web, BANANA_PROJECT_FS_PATH);

        assertEquals(rootNode, apple.getParentPackageInfo());
        assertEquals(apple, apple_web.getParentPackageInfo());
        assertEquals(rootNode, banana.getParentPackageInfo());
    }

    @Test
    public void testFind() throws Exception {
        create_subdir(APPLE_PROJECT_FS_PATH);
        create_subdir(APPLE_PROJECT_FS_PATH + File.separator + "web");
        create_subdir(BANANA_PROJECT_FS_PATH);

        BazelPackageInfo rootNode = new BazelPackageInfo(WSDIR);
        BazelPackageInfo apple = new BazelPackageInfo(rootNode, APPLE_PROJECT_FS_PATH);
        BazelPackageInfo apple_web = new BazelPackageInfo(apple, APPLE_PROJECT_FS_PATH + File.separator + "web");
        BazelPackageInfo banana = new BazelPackageInfo(apple_web, BANANA_PROJECT_FS_PATH);

        assertEquals(apple_web, banana.findByPackage("//projects/libs/apple/web"));
        assertEquals(rootNode, apple.findByPackage("//..."));
    }

    // HELPERS

    private static File create_subdir(String fsPath) throws Exception {
        File subdir = new File(WSDIR, fsPath);

        if (!subdir.exists()) {
            subdir.mkdirs();
        }

        return subdir;
    }
}
