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
package com.salesforce.bazel.sdk.aspect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Test;

import com.salesforce.bazel.sdk.init.JvmRuleInit;
import com.salesforce.bazel.sdk.model.BazelTargetKind;
import com.salesforce.bazel.sdk.path.FSPathHelper;

public class AspectTargetInfosTest {

    private final String libPath = FSPathHelper.osSeps("a/b/c/d/Foo.java"); // $SLASH_OK
    private final String testPath = FSPathHelper.osSeps("a/b/c/d/Foo.java"); // $SLASH_OK
    private final String binPath = FSPathHelper.osSeps("a/b/c/d/Foo.java"); // $SLASH_OK
    private final String selPath = FSPathHelper.osSeps("a/b/c/d/Foo.java"); // $SLASH_OK

    @Test
    public void testLookupByLabel() {
        AspectTargetInfo lib = getAspectTargetInfo("foo1", JvmRuleInit.KIND_JAVA_LIBRARY, libPath);
        AspectTargetInfo test = getAspectTargetInfo("foo2", JvmRuleInit.KIND_JAVA_TEST, testPath);
        AspectTargetInfo bin = getAspectTargetInfo("foo3", JvmRuleInit.KIND_JAVA_BINARY, binPath);
        AspectTargetInfo seleniumTest = getAspectTargetInfo("foo4", JvmRuleInit.KIND_SELENIUM_TEST, selPath);

        AspectTargetInfos apis = new AspectTargetInfos(lib, test, bin, seleniumTest);

        assertSame(lib, apis.lookupByLabel("foo1"));
        assertSame(test, apis.lookupByLabel("foo2"));
        assertSame(bin, apis.lookupByLabel("foo3"));
        assertSame(seleniumTest, apis.lookupByLabel("foo4"));
        assertNull(apis.lookupByLabel("blah"));
    }

    @Test
    public void testLookupByTargetKind__singleTargetKind() {
        AspectTargetInfo lib = getAspectTargetInfo("foo1", JvmRuleInit.KIND_JAVA_LIBRARY, libPath);
        AspectTargetInfo test = getAspectTargetInfo("foo2", JvmRuleInit.KIND_JAVA_TEST, testPath);
        AspectTargetInfo bin = getAspectTargetInfo("foo3", JvmRuleInit.KIND_JAVA_BINARY, binPath);
        AspectTargetInfo seleniumTest = getAspectTargetInfo("foo4", JvmRuleInit.KIND_SELENIUM_TEST, selPath);

        AspectTargetInfos apis = new AspectTargetInfos(lib, test, bin, seleniumTest);

        Collection<AspectTargetInfo> infos = apis.lookupByTargetKind(JvmRuleInit.KIND_JAVA_TEST);
        assertEquals(1, infos.size());
        assertSame(test, infos.iterator().next());

        infos = apis.lookupByTargetKind(JvmRuleInit.KIND_SELENIUM_TEST);
        assertEquals(1, infos.size());
        assertSame(seleniumTest, infos.iterator().next());
    }

    @Test
    public void testLookupByTargetKind__multipleTargetKinds() {
        AspectTargetInfo lib = getAspectTargetInfo("foo1", JvmRuleInit.KIND_JAVA_LIBRARY, libPath);
        AspectTargetInfo test = getAspectTargetInfo("foo2", JvmRuleInit.KIND_JAVA_TEST, testPath);
        AspectTargetInfo bin = getAspectTargetInfo("foo3", JvmRuleInit.KIND_JAVA_BINARY, binPath);
        AspectTargetInfo seleniumTest = getAspectTargetInfo("foo4", JvmRuleInit.KIND_SELENIUM_TEST, selPath);

        AspectTargetInfos apis = new AspectTargetInfos(lib, test, bin, seleniumTest);

        Collection<AspectTargetInfo> infos =
                apis.lookupByTargetKind(JvmRuleInit.KIND_JAVA_TEST, JvmRuleInit.KIND_JAVA_BINARY);

        assertEquals(2, infos.size());
        assertTrue(infos.contains(test));
        assertTrue(infos.contains(bin));
    }

    @Test
    public void testLookupByRootSourcePath() {
        AspectTargetInfo api = getAspectTargetInfo("foo", libPath);

        AspectTargetInfos infos = new AspectTargetInfos(api);

        assertSame(api, infos.lookupByRootSourcePath(FSPathHelper.osSeps("a/b/c/d/Foo.java")).iterator().next());
        assertSame(api, infos.lookupByRootSourcePath(FSPathHelper.osSeps("a/b/c/d")).iterator().next());
        assertSame(api, infos.lookupByRootSourcePath(FSPathHelper.osSeps("a/b/c/")).iterator().next());
        assertSame(api, infos.lookupByRootSourcePath(FSPathHelper.osSeps("a/b")).iterator().next());
        assertSame(api, infos.lookupByRootSourcePath(FSPathHelper.osSeps("a/")).iterator().next());
        assertSame(api, infos.lookupByRootSourcePath("a").iterator().next());
        assertEquals(0, infos.lookupByRootSourcePath("f").size());
    }

    @Test
    public void testLookupByRootSourcePath__noSubstringMatch() {
        AspectTargetInfo api =
                getAspectTargetInfo("myclass", FSPathHelper.osSeps("projects/services/foo/MyClass.java")); // $SLASH_OK

        AspectTargetInfos apis = new AspectTargetInfos(api);
        Collection<AspectTargetInfo> infos =
                apis.lookupByRootSourcePath(FSPathHelper.osSeps("projects/services/foo")); // $SLASH_OK

        assertEquals(1, infos.size());
        assertSame(api, infos.iterator().next());
        assertEquals(0, apis.lookupByRootSourcePath(FSPathHelper.osSeps("projects/services/fo")).size()); // $SLASH_OK
    }

    @Test
    public void testLookupByRootSourcePath__multipleMatching() {
        AspectTargetInfo foo = getAspectTargetInfo("foo", FSPathHelper.osSeps("a/b/c/aaa/Foo.java")); // $SLASH_OK
        AspectTargetInfo blah = getAspectTargetInfo("blah", FSPathHelper.osSeps("a/b/c/zzz/Blah.java")); // $SLASH_OK

        AspectTargetInfos apis = new AspectTargetInfos(foo, blah);

        Collection<AspectTargetInfo> infos = apis.lookupByRootSourcePath(FSPathHelper.osSeps("a/b/c")); // $SLASH_OK
        assertEquals(2, infos.size());
        assertTrue(infos.contains(foo));
        assertTrue(infos.contains(blah));
        infos = apis.lookupByRootSourcePath(FSPathHelper.osSeps("a/b/c/aaa")); // $SLASH_OK
        assertEquals(1, infos.size());
        assertTrue(infos.contains(foo));
    }

    @Test
    public void testLookupByRootSourcePath__sourcesWithCommonRootPathValidation() {
        AspectTargetInfo foo = getAspectTargetInfo("foo", FSPathHelper.osSeps("a/b/c/aaa/ccc/Foo.java"), // $SLASH_OK
            FSPathHelper.osSeps("a/b/c/aaa/ddd/Blah.java")); // $SLASH_OK

        AspectTargetInfos apis = new AspectTargetInfos(foo);

        Collection<AspectTargetInfo> infos = apis.lookupByRootSourcePath(FSPathHelper.osSeps("a/b/c")); // $SLASH_OK
        assertEquals(1, infos.size());

        infos = apis.lookupByRootSourcePath(FSPathHelper.osSeps("a/b/c/aaa")); // $SLASH_OK
        assertEquals(1, infos.size());
    }

    @Test(expected = IllegalStateException.class)
    public void testLookupByRootSourcePath__sourcesWithoutCommonRootPathValidation_partialPath() {
        AspectTargetInfo foo = getAspectTargetInfo("foo", FSPathHelper.osSeps("a/b/c/aaa/Foo.java"), // $SLASH_OK
            FSPathHelper.osSeps("a/b/c/zzz/Blah.java")); // $SLASH_OK

        AspectTargetInfos apis = new AspectTargetInfos(foo);

        apis.lookupByRootSourcePath(FSPathHelper.osSeps("a/b/c/aaa")); // $SLASH_OK
    }

    @Test(expected = IllegalStateException.class)
    public void testLookupByRootSourcePath__sourcesWithoutCommonRootPathValidation_fullPath() {
        AspectTargetInfo foo = getAspectTargetInfo("foo", FSPathHelper.osSeps("a/b/c/aaa/Foo.java"), // $SLASH_OK
            FSPathHelper.osSeps("x/y/z/aaa/Blah.java")); // $SLASH_OK

        AspectTargetInfos apis = new AspectTargetInfos(foo);

        apis.lookupByRootSourcePath(FSPathHelper.osSeps("a/b/c")); // $SLASH_OK
    }

    private static AspectTargetInfo getAspectTargetInfo(String label, String... sourcePaths) {
        return getAspectTargetInfo(label, JvmRuleInit.KIND_JAVA_LIBRARY, sourcePaths);
    }

    private static AspectTargetInfo getAspectTargetInfo(String label, BazelTargetKind targetKind,
            String... sourcePaths) {
        List<String> sourcePathList = Arrays.asList(sourcePaths);

        String workspaceRelativePath = "some" + File.separatorChar + "path";
        return new AspectTargetInfo(new File(""), workspaceRelativePath, targetKind.toString().toLowerCase(), label,
                new ArrayList<>(), sourcePathList);
    }

}
