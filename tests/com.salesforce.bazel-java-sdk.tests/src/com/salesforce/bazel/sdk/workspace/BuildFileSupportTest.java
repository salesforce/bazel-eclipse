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
package com.salesforce.bazel.sdk.workspace;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.junit.Before;
import org.junit.Test;

import com.salesforce.bazel.sdk.init.JvmRuleSupport;

public class BuildFileSupportTest {

    @Before
    public void setup() {
        JvmRuleSupport.initialize();
    }
    
    @Test
    public void testJavaRulesLines_positive() {
        assertTrue(BuildFileSupport.hasRegisteredRuleInLine("java_binary("));
        assertTrue(BuildFileSupport.hasRegisteredRuleInLine("java_library("));
        assertTrue(BuildFileSupport.hasRegisteredRuleInLine("java_test("));
        assertTrue(BuildFileSupport.hasRegisteredRuleInLine("java_web_test_suite("));
        assertTrue(BuildFileSupport.hasRegisteredRuleInLine("springboot"));
        assertTrue(BuildFileSupport.hasRegisteredRuleInLine("springboot_test("));

        // spaces
        assertTrue(BuildFileSupport.hasRegisteredRuleInLine("   java_binary("));
        // tab
        assertTrue(BuildFileSupport.hasRegisteredRuleInLine("\tjava_binary("));
    }

    @Test
    public void testJavaRulesLines_negative() {
        assertFalse(BuildFileSupport.hasRegisteredRuleInLine("ajava_binary("));
        assertFalse(BuildFileSupport.hasRegisteredRuleInLine("# java_library("));
        assertFalse(BuildFileSupport.hasRegisteredRuleInLine("javatest("));
        assertFalse(BuildFileSupport.hasRegisteredRuleInLine("spring boot("));
        assertFalse(
            BuildFileSupport.hasRegisteredRuleInLine("load(\"//tools/springboot:springboot.bzl\", \"springboot\")")); // $SLASH_OK bazel path

        assertFalse(BuildFileSupport.hasRegisteredRuleInLine("\n"));
        assertFalse(BuildFileSupport.hasRegisteredRuleInLine(" "));
        assertFalse(BuildFileSupport.hasRegisteredRuleInLine(""));
    }

    @Test
    public void testJavaRules_InputStream_positive() throws Exception {
        StringBuffer sb = new StringBuffer();

        sb.append("load(\"//tools/springboot:springboot.bzl\", \"springboot\", \"springboot_test\")\n"); // $SLASH_OK bazel path
        sb.append("# some comment \n");
        sb.append("springboot(\n");
        sb.append("  name = \"basic-rest-service\",\n"); // $SLASH_OK escape char
        sb.append("  boot_app_class = \"com.salesforce.basicrestservice.BasicRestService\",\n"); // $SLASH_OK escape char
        sb.append("  deps = deps,\n");
        sb.append(")\n");
        sb.append("\n");

        InputStream is = new ByteArrayInputStream(sb.toString().getBytes(Charset.forName("UTF-8")));
        assertTrue(BuildFileSupport.hasRegisteredRules(is));
    }

    @Test
    public void testJavaRules_InputStream_negative() throws Exception {
        StringBuffer sb = new StringBuffer();

        sb.append("load(\"//tools/springboot:springboot.bzl\", \"springboot\", \"springboot_test\")\n");
        sb.append("# some comment \n");
        sb.append("not_springboot(\n");
        sb.append("  name = \"basic-rest-service\",\n"); // $SLASH_OK escape char
        sb.append("  boot_app_class = \"com.salesforce.basicrestservice.BasicRestService\",\n"); // $SLASH_OK escape char
        sb.append("  deps = deps,\n");
        sb.append(")\n");
        sb.append("\n");

        InputStream is = new ByteArrayInputStream(sb.toString().getBytes(Charset.forName("UTF-8")));
        assertFalse(BuildFileSupport.hasRegisteredRules(is));
    }

    @Test
    public void testJavaRules_InputStream_negative_emptyBUILDfile() throws Exception {
        InputStream is = new ByteArrayInputStream("".getBytes(Charset.forName("UTF-8")));
        assertFalse(BuildFileSupport.hasRegisteredRules(is));
    }
}
