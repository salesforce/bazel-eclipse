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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BazelOutputDirectoryBuilderTest {

    private final BazelOutputDirectoryBuilder builder = new BazelOutputDirectoryBuilder();

    @Test
    public void testJavaLib() {
        BazelLabel label = new BazelLabel("//projects/libs/apple/apple-api");

        String path = builder.getOutputDirectoryPath(TargetKind.JAVA_LIBRARY, label);

        assertEquals("bazel-bin/projects/libs/apple/apple-api/_javac/apple-api/libapple-api_classes", path);
    }

    @Test
    public void testJavaBinary() {
        BazelLabel label = new BazelLabel("//projects/services/fruit-salad-service/fruit-salad:fruit-salad-service");

        String path = builder.getOutputDirectoryPath(TargetKind.JAVA_BINARY, label);

        assertEquals(
            "bazel-bin/projects/services/fruit-salad-service/fruit-salad/_javac/fruit-salad-service/fruit-salad-service_classes",
            path);
    }

    @Test
    public void testJavaTest() {
        BazelLabel label = new BazelLabel("//projects/libs/grapes/grapes-api:src/test/java/demo/grapes/api/GrapeTest");

        String path = builder.getOutputDirectoryPath(TargetKind.JAVA_TEST, label);

        assertEquals(
            "bazel-bin/projects/libs/grapes/grapes-api/_javac/src/test/java/demo/grapes/api/GrapeTest/GrapeTest_classes",
            path);
    }
}
