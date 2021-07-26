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
package com.salesforce.bazel.sdk.project;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.path.SplitSourcePath;

/**
 * SourcePath helps us split paths to source files into the source directory and pkg+file path.
 * src/main/java/com/salesforce/foo/Foo.java => src/main/java + com/salesforce/foo/Foo.java
 * <p>
 * In order for this to work, the caller needs to know the package name (com/salesforce/foo) through some other means
 * (e.g. parse the package line in the java file).
 */
public class SourcePathTest {

    @Test
    public void happyTests() {
        // Maven main
        String filepath = seps("src/main/java/com/salesforce/foo/Foo.java");
        String pkgpath = seps("com/salesforce/foo");
        SplitSourcePath sp = SplitSourcePath.splitNamespacedPath(filepath, pkgpath);
        assertNotNull(sp);
        assertEquals(seps("src/main/java"), sp.sourceDirectoryPath);
        assertEquals(seps("com/salesforce/foo/Foo.java"), sp.filePath);

        // Maven test
        filepath = seps("src/test/java/com/salesforce/foo/Foo.java");
        pkgpath = seps("com/salesforce/foo");
        sp = SplitSourcePath.splitNamespacedPath(filepath, pkgpath);
        assertNotNull(sp);
        assertEquals(seps("src/test/java"), sp.sourceDirectoryPath);
        assertEquals(seps("com/salesforce/foo/Foo.java"), sp.filePath);

        // default package
        filepath = seps("src/main/java/Foo.java");
        pkgpath = seps("");
        sp = SplitSourcePath.splitNamespacedPath(filepath, pkgpath);
        assertNotNull(sp);
        assertEquals(seps("src/main/java"), sp.sourceDirectoryPath);
        assertEquals("Foo.java", sp.filePath);

        // custom
        filepath = seps("sources/com/salesforce/foo/Foo.java");
        pkgpath = seps("com/salesforce/foo");
        sp = SplitSourcePath.splitNamespacedPath(filepath, pkgpath);
        assertNotNull(sp);
        assertEquals("sources", sp.sourceDirectoryPath);
        assertEquals(seps("com/salesforce/foo/Foo.java"), sp.filePath);
    }

    @Test
    public void unhappyTests() {
        // no path for file
        String filepath = seps("Foo.java");
        String pkgpath = seps("com/salesforce/foo");
        SplitSourcePath sp = SplitSourcePath.splitNamespacedPath(filepath, pkgpath);
        assertNull(sp);

        // null path
        sp = SplitSourcePath.splitNamespacedPath(null, "something");
        assertNull(sp);

        // null package
        sp = SplitSourcePath.splitNamespacedPath("Foo.java", null);
        assertNull(sp);

        // missing namespace hierarchy
        boolean allowMissingPackageHierarchy = false;
        filepath = seps("source/java/ui/Foo.java");
        pkgpath = seps("com/salesforce/foo");
        sp = SplitSourcePath.splitNamespacedPath(filepath, pkgpath, allowMissingPackageHierarchy);
        assertNull(sp);
    }

    @Test
    public void edgeCaseTests() {
        // Duped path elements
        String filepath = seps("src/main/java/com/salesforce/foo/com/salesforce/foo/Foo.java");
        String pkgpath = seps("com/salesforce/foo");
        SplitSourcePath sp = SplitSourcePath.splitNamespacedPath(filepath, pkgpath);
        assertNotNull(sp);
        assertEquals(seps("src/main/java/com/salesforce/foo"), sp.sourceDirectoryPath);
        assertEquals(seps("com/salesforce/foo/Foo.java"), sp.filePath);

        boolean allowMissingPackageHierarchy = true;

        // missing hierarchy; in this case we are forced to take the entire path leading up to the file
        // as the sourceDir. This is because it is actually legal (but bad practice) to put a Java file in any old
        // directory, and not use the standard hierarchy based on package.
        filepath = seps("source/java/ui/Foo.java");
        pkgpath = seps("com/salesforce/foo");
        sp = SplitSourcePath.splitNamespacedPath(filepath, pkgpath, allowMissingPackageHierarchy);
        assertNotNull(sp);
        assertEquals(seps("source/java/ui"), sp.sourceDirectoryPath);
        assertEquals("Foo.java", sp.filePath);

        // package mismatch (bar vs foo); this also triggers the missing hierarchy use case, and so the entire
        // directory structure becomes the sourceDir path
        filepath = seps("src/main/java/com/salesforce/foo/Foo.java");
        pkgpath = seps("com/salesforce/bar");
        sp = SplitSourcePath.splitNamespacedPath(filepath, pkgpath, allowMissingPackageHierarchy);
        assertNotNull(sp);
        assertEquals(seps("src/main/java/com/salesforce/foo"), sp.sourceDirectoryPath);
        assertEquals("Foo.java", sp.filePath);
    }

    // convert unix paths to windows paths, if running tests on windows
    private String seps(String path) {
        return FSPathHelper.osSeps(path);
    }
}
