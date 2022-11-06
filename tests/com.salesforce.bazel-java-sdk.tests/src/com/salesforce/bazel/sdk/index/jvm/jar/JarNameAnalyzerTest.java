/**
 * Copyright (c) 2022, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.sdk.index.jvm.jar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JarNameAnalyzerTest {

    @Test
    public void testIgnoreList() {
        boolean ignoreHeaderJars = true;
        boolean ignoreTestJars = true;
        boolean ignoreInterfaceJars = true;
        boolean ignoreSourceJars = true;
        boolean ignoreDeployJars = true;

        assertFalse(JarNameAnalyzer.doIgnoreJarFile("foo.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));

        assertTrue(JarNameAnalyzer.doIgnoreJarFile("foo-hjar.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertTrue(JarNameAnalyzer.doIgnoreJarFile("header_foo.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertTrue(JarNameAnalyzer.doIgnoreJarFile("foo-native-header.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertTrue(JarNameAnalyzer.doIgnoreJarFile("foo-class.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertTrue(JarNameAnalyzer.doIgnoreJarFile("foo-ijar.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertTrue(JarNameAnalyzer.doIgnoreJarFile("foo-src.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertTrue(JarNameAnalyzer.doIgnoreJarFile("foo-sources.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertTrue(JarNameAnalyzer.doIgnoreJarFile("foo-gensrc.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertTrue(JarNameAnalyzer.doIgnoreJarFile("foo_deploy.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertTrue(JarNameAnalyzer.doIgnoreJarFile("fooTest.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertTrue(JarNameAnalyzer.doIgnoreJarFile("fooIT.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
    }

    @Test
    public void testIgnoreList_negative() {
        boolean ignoreHeaderJars = true;
        boolean ignoreTestJars = true;
        boolean ignoreInterfaceJars = true;
        boolean ignoreSourceJars = true;
        boolean ignoreDeployJars = true;

        assertFalse(JarNameAnalyzer.doIgnoreJarFile("a-hjar-foo.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("a_header-foo.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("a-native-header-foo.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("a-class-foo.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("a-ijar-foo.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("a-src-foo.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("a-sources-foo.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("a-gensrc_foo.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("a_deploy_foo.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("a-Testfoo.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("a-ITfoo.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
    }

    @Test
    public void testIgnoreList_false() {
        boolean ignoreHeaderJars = false;
        boolean ignoreTestJars = false;
        boolean ignoreInterfaceJars = false;
        boolean ignoreSourceJars = false;
        boolean ignoreDeployJars = false;

        assertFalse(JarNameAnalyzer.doIgnoreJarFile("foo.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));

        assertFalse(JarNameAnalyzer.doIgnoreJarFile("foo-hjar.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("header_foo.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("foo-native-header.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("foo-class.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("foo-ijar.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("foo-src.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("foo-sources.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("foo-gensrc.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("foo_deploy.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("fooTest.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
        assertFalse(JarNameAnalyzer.doIgnoreJarFile("fooIT.jar", ignoreHeaderJars, ignoreTestJars,
            ignoreInterfaceJars, ignoreSourceJars, ignoreDeployJars));
    }

}
