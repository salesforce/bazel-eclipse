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
package com.salesforce.bazel.sdk.lang.jvm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.StringReader;

import org.junit.Test;

public class JavaSourceFileTest {

    @Test
    public void happyPackageLineTests() {
        JavaSourceFile javaFile = new JavaSourceFile(new File("FakeJavaFile.java"));

        String packageName = javaFile.getPackageFromLine("package com.salesforce.foo;");
        assertNotNull(packageName);
        assertEquals("com.salesforce.foo", packageName);

        packageName = javaFile.getPackageFromLine("package foo;");
        assertNotNull(packageName);
        assertEquals("foo", packageName);

        // seriously, do people do this?
        packageName = javaFile.getPackageFromLine("package     com.salesforce.foo-bar.feelz     ;");
        assertNotNull(packageName);
        assertEquals("com.salesforce.foo-bar.feelz", packageName);
    }

    @Test
    public void negativePackageLineTests() {
        JavaSourceFile javaFile = new JavaSourceFile(new File("FakeJavaFile.java"));
        String packageName = javaFile.getPackageFromLine("public class FakeJavaFile {");
        assertNull(packageName);

        // somebody put the package statement on a different line than the package. no sympathy.
        packageName = javaFile.getPackageFromLine("package");
        assertNull(packageName);
    }

    @Test
    public void happyFileTests() {
        JavaSourceFile javaFile = new JavaSourceFile(new File("FakeJavaFile.java"));

        StringBuffer sb = new StringBuffer();
        sb.append("// just sample content\n");
        sb.append("// just sample content\n");
        sb.append("// just sample content\n");
        sb.append("package com.salesforce.foo;\n");
        sb.append("// just sample content\n");
        sb.append("// just sample content\n");
        sb.append("// just sample content\n");

        StringReader sr = new StringReader(sb.toString());

        String packageName = javaFile.getPackageFromReader(sr);
        assertNotNull(packageName);
        assertEquals("com.salesforce.foo", packageName);
    }
}
