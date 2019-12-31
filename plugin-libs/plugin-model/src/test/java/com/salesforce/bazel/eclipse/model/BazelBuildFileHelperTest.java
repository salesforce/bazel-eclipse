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
package com.salesforce.bazel.eclipse.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.junit.Test;

import com.salesforce.bazel.eclipse.model.BazelBuildFileHelper;

public class BazelBuildFileHelperTest {

    @Test
    public void testJavaRulesLines_positive() {
        assertTrue(BazelBuildFileHelper.hasJavaRulesInLine("java_binary("));
        assertTrue(BazelBuildFileHelper.hasJavaRulesInLine("java_library("));
        assertTrue(BazelBuildFileHelper.hasJavaRulesInLine("java_test("));
        assertTrue(BazelBuildFileHelper.hasJavaRulesInLine("java_web_test_suite("));
        assertTrue(BazelBuildFileHelper.hasJavaRulesInLine("springboot"));
        assertTrue(BazelBuildFileHelper.hasJavaRulesInLine("springboot_test("));

        // spaces
        assertTrue(BazelBuildFileHelper.hasJavaRulesInLine("   java_binary("));
        // tab
        assertTrue(BazelBuildFileHelper.hasJavaRulesInLine("\tjava_binary("));
    }

    @Test
    public void testJavaRulesLines_negative() {
        assertFalse(BazelBuildFileHelper.hasJavaRulesInLine("ajava_binary("));
        assertFalse(BazelBuildFileHelper.hasJavaRulesInLine("# java_library("));
        assertFalse(BazelBuildFileHelper.hasJavaRulesInLine("javatest("));
        assertFalse(BazelBuildFileHelper.hasJavaRulesInLine("spring boot("));
        assertFalse(
            BazelBuildFileHelper.hasJavaRulesInLine("load(\"//tools/springboot:springboot.bzl\", \"springboot\")"));

        assertFalse(BazelBuildFileHelper.hasJavaRulesInLine("\n"));
        assertFalse(BazelBuildFileHelper.hasJavaRulesInLine(" "));
        assertFalse(BazelBuildFileHelper.hasJavaRulesInLine(""));
    }

    @Test
    public void testJavaRules_InputStream_positive() throws Exception {
        StringBuffer sb = new StringBuffer();

        sb.append("load(\"//tools/springboot:springboot.bzl\", \"springboot\", \"springboot_test\")\n");
        sb.append("# some comment \n");
        sb.append("springboot(\n");
        sb.append("  name = \"basic-rest-service\",\n");
        sb.append("  boot_app_class = \"com.salesforce.basicrestservice.BasicRestService\",\n");
        sb.append("  deps = deps,\n");
        sb.append(")\n");
        sb.append("\n");

        InputStream is = new ByteArrayInputStream(sb.toString().getBytes(Charset.forName("UTF-8")));
        assertTrue(BazelBuildFileHelper.hasJavaRules(is));
    }

    @Test
    public void testJavaRules_InputStream_negative() throws Exception {
        StringBuffer sb = new StringBuffer();

        sb.append("load(\"//tools/springboot:springboot.bzl\", \"springboot\", \"springboot_test\")\n");
        sb.append("# some comment \n");
        sb.append("not_springboot(\n");
        sb.append("  name = \"basic-rest-service\",\n");
        sb.append("  boot_app_class = \"com.salesforce.basicrestservice.BasicRestService\",\n");
        sb.append("  deps = deps,\n");
        sb.append(")\n");
        sb.append("\n");

        InputStream is = new ByteArrayInputStream(sb.toString().getBytes(Charset.forName("UTF-8")));
        assertFalse(BazelBuildFileHelper.hasJavaRules(is));
    }

    @Test
    public void testJavaRules_InputStream_negative_emptyBUILDfile() throws Exception {
        InputStream is = new ByteArrayInputStream("".getBytes(Charset.forName("UTF-8")));
        assertFalse(BazelBuildFileHelper.hasJavaRules(is));
    }
}
