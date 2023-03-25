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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.salesforce.bazel.sdk.path.FSPathHelper;

public class BazelProblemTest {

    @Test
    public void getOwningLabel__matchingLabel() {
        var details = BazelProblem.createError(FSPathHelper.osSeps("a/b/c/d"), 1, "desc"); // $SLASH_OK
        var l1 = new BazelLabel("x/y/z"); // $SLASH_OK bazel path
        var l2 = new BazelLabel("a/b/c"); // $SLASH_OK bazel path

        List<BazelLabel> labels = Arrays.asList(l1, l2);
        var owningLabel = details.getOwningLabel(labels);

        assertSame(l2, owningLabel);
    }

    @Test
    public void getOwningLabel__nestedLabel() {
        var details = BazelProblem.createError(FSPathHelper.osSeps("a/b/c/d/e"), 1, "desc"); // $SLASH_OK
        var l0 = new BazelLabel("b/c/d"); // $SLASH_OK bazel path
        var l1 = new BazelLabel("a/b"); // $SLASH_OK bazel path
        var l2 = new BazelLabel("a/b/c"); // $SLASH_OK bazel path
        var l3 = new BazelLabel("a/b/c/d/e/f"); // $SLASH_OK bazel path
        var l4 = new BazelLabel("a/b/c/d"); // $SLASH_OK bazel path
        var l5 = new BazelLabel("x/y/z"); // $SLASH_OK bazel path

        var owningLabel = details.getOwningLabel(Arrays.asList(l0, l1, l2, l3, l4, l5));

        assertSame(l4, owningLabel);
    }

    @Test
    public void testIsError() {
        assertTrue(BazelProblem.createError("", 1, "").isError());
    }

    @Test(expected = IllegalArgumentException.class)
    public void toErrorWithRelativizedResourcePath__differentBazelPackage() {
        var partialPath = FSPathHelper.osSeps("/src/main/java/com/MyClass.java"); // $SLASH_OK
        var fullPath = FSPathHelper.osSeps("projects/libs/cake/metrics-abstractions" + partialPath); // $SLASH_OK

        var details = BazelProblem.createError(fullPath, 1, "desc");

        details.toErrorWithRelativizedResourcePath(new BazelLabel("projects/libs/cake/abstractions")); // $SLASH_OK bazel path
    }

    @Test
    public void toErrorWithRelativizedResourcePath__matchingBazelPackage() {
        var partialPath = FSPathHelper.osSeps("/src/main/java/com/MyClass.java"); // $SLASH_OK
        var fullPath = FSPathHelper.osSeps("projects/libs/cake/abstractions" + partialPath); // $SLASH_OK
        var details = BazelProblem.createError(fullPath, 1, "desc");

        details = details.toErrorWithRelativizedResourcePath(new BazelLabel("//projects/libs/cake/abstractions")); // $SLASH_OK bazel path

        assertEquals(partialPath, details.getResourcePath());
    }

    @Test(expected = IllegalArgumentException.class)
    public void toErrorWithRelativizedResourcePath__matchingBazelPackagePrefix() {
        var partialPath = FSPathHelper.osSeps("/src/main/java/com/MyClass.java"); // $SLASH_OK
        var fullPath = FSPathHelper.osSeps("projects/libs/cake/abstractions_foo" + partialPath); // $SLASH_OK

        var details = BazelProblem.createError(fullPath, 1, "desc");

        details.toErrorWithRelativizedResourcePath(new BazelLabel("//projects/libs/cake/abstractions")); // $SLASH_OK bazel path
    }

    @Test
    public void toErrorWithRelativizedResourcePath__rootPackage() {
        var details = BazelProblem.createError(FSPathHelper.osSeps("/bazelproject"), 1, "desc"); // $SLASH_OK

        details = details.toErrorWithRelativizedResourcePath(new BazelLabel("//..."));

        assertEquals(FSPathHelper.osSeps("/bazelproject"), details.getResourcePath()); // $SLASH_OK
    }

    @Test
    public void toGenericWorkspaceLevelError() {
        var details = BazelProblem.createError("a/b/c", 13, "desc"); // $SLASH_OK bazel path

        var generic = details.toGenericWorkspaceLevelError("prefix:");

        assertEquals(0, generic.getLineNumber());
        assertEquals(File.separatorChar + "WORKSPACE", generic.getResourcePath());
        assertEquals("prefix:a/b/c desc", generic.getDescription()); // $SLASH_OK bazel path
    }

}
