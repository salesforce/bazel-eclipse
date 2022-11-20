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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JavaJarCrawlerTest {

    @Test
    public void testSkipDirectory() {
        boolean result = JavaJarCrawler.doSkipDirectory("com/salesforce/foo.runfiles/Bar.jar"); // we don't index runfiles
        assertTrue(result);

        result = JavaJarCrawler.doSkipDirectory("com/salesforce/foo/Bar.jar");
        assertFalse(result);
    }

    @Test
    public void testHasClassfileName() {
        boolean result = JavaJarCrawler.hasClassfileName("com/salesforce/foo/Bar.class");
        assertTrue(result);

        result = JavaJarCrawler.hasClassfileName("Bar.class");
        assertTrue(result);

        result = JavaJarCrawler.hasClassfileName("com/foo/Bar.java");
        assertFalse(result);

        result = JavaJarCrawler.hasClassfileName("com/foo/Bar.txt");
        assertFalse(result);

        // by design, this method is not interested in inner classes
        result = JavaJarCrawler.hasClassfileName("com/foo/Bar$Inner.class");
        assertFalse(result);

        result = JavaJarCrawler.hasClassfileName("");
        assertFalse(result);

        result = JavaJarCrawler.hasClassfileName(null);
        assertFalse(result);
    }

    @Test
    public void testConvertClassfileNameToClassname() {
        String result = JavaJarCrawler.convertClassfileNameToClassname("com/salesforce/foo/Bar.class");
        assertEquals("com.salesforce.foo.Bar", result);

        result = JavaJarCrawler.convertClassfileNameToClassname("Bar.class");
        assertEquals("Bar", result);

        result = JavaJarCrawler.convertClassfileNameToClassname("");
        assertNull(result);

        result = JavaJarCrawler.convertClassfileNameToClassname("a/b/c");
        assertNull(result);

        result = JavaJarCrawler.convertClassfileNameToClassname(".class");
        assertNull(result);

        result = JavaJarCrawler.convertClassfileNameToClassname("class");
        assertNull(result);

        result = JavaJarCrawler.convertClassfileNameToClassname(null);
        assertNull(result);
    }
}
