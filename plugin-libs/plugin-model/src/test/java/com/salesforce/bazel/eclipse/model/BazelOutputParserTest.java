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

import java.util.List;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class BazelOutputParserTest {

    @Test
    public void testSingleJavaError() {
        BazelOutputParser p = new BazelOutputParser();
        List<String> lines = ImmutableList.of(
            "ERROR: /Users/stoens/bazel-build-example-for-eclipse/sayhello/BUILD:1:1: Building sayhello/libsayhello.jar (2 source files) failed (Exit 1)",
            "sayhello/src/main/java/com/blah/foo/hello/Main.java:16: error: cannot find symbol");

        List<BazelMarkerDetails> errors = p.getErrorBazelMarkerDetails(lines);

        assertEquals(1, errors.size());
        assertEquals("sayhello/src/main/java/com/blah/foo/hello/Main.java", errors.get(0).getResourcePath());
        assertEquals(16, errors.get(0).getLineNumber());
        assertEquals("Cannot find symbol", errors.get(0).getDescription());
    }

    @Test
    public void testMultipleJavaErrors() {
        BazelOutputParser p = new BazelOutputParser();
        List<String> lines = ImmutableList.of(
            "ERROR: /Users/stoens/bazel-build-example-for-eclipse/sayhello/BUILD:1:1: Building sayhello/libsayhello.jar (2 source files) failed (Exit 1)",
            "sayhello/src/main/java/com/blah/foo/hello/Main.java:16: error: cannot find symbol", "    blah 1 2 3",
            "             ^",
            "ERROR: /Users/stoens/bazel-build-example-for-eclipse/sayhello/BUILD:1:1: Building sayhello/libsayhello.jar (2 source files) failed (Exit 1)",
            "sayhello/src/main/java/com/blah/foo/hello/Main.java:17: error: cannot find symbols");

        List<BazelMarkerDetails> errors = p.getErrorBazelMarkerDetails(lines);

        assertEquals(2, errors.size());
        assertEquals("sayhello/src/main/java/com/blah/foo/hello/Main.java", errors.get(0).getResourcePath());
        assertEquals(16, errors.get(0).getLineNumber());
        assertEquals("Cannot find symbol: blah 1 2 3", errors.get(0).getDescription());

        assertEquals("sayhello/src/main/java/com/blah/foo/hello/Main.java", errors.get(1).getResourcePath());
        assertEquals(17, errors.get(1).getLineNumber());
        assertEquals("Cannot find symbols", errors.get(1).getDescription());
    }
    
    @Test
    public void testUnformattedError() {
        BazelOutputParser p = new BazelOutputParser();
        List<String> lines = ImmutableList.of(
            "ERROR: this is just a string that we should probably handle but we don't right now"
        );                

        List<BazelMarkerDetails> errors = p.getErrorBazelMarkerDetails(lines);

        assertEquals(0, errors.size());
    }
}
