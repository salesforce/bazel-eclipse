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

/**
 * Encapsulates the logic that builds the path to the location under "bazel-bin" that has compiled classes.
 * 
 * @author stoens
 * @since summer 2019
 *
 */
public class BazelOutputDirectoryBuilder {

    public String getOutputDirectoryPath(TargetKind targetKind, BazelLabel label) {

        // Salesforce Issue #3: provide the Bazel output directory for compiled classfiles so that Launchers will work
        // bazel-bin/java/libs/apple/apple-api/_javac/apple-api/libapple-api_classes/demo/apple/api/AppleOrchard.class
        // bazel-bin/[PACKAGE_PATH]/_javac/[TARGET_NAME]/[TARGET_TYPE][LAST_PATH_COMP_TARGET_NAME]_classes

        // PACKAGE_PATH=the path to the package, for ex java/libs/apple/apple-api
        // TARGET_NAME=apple-api
        // TARGET_TYPE="lib" for java_library, empty-string for java_binary|java_test
        // LAST_PATH_COMP_TARGET_NAME=if the target name is a path-like structure (typical for tests - for ex src/main/java/foo/MyTest), 
        //                            the value is the last part of the path: src/main/java/foo/MyTest -> MyTest
        //                            if the target name is not path-like, this is just the target name
        //
        // TODO: it would be nice if we could just ask Bazel for this, instead of reverse-engineering the directory structure here        

        String lastCompTargetName = label.getTargetName();
        int i = lastCompTargetName.lastIndexOf("/");
        if (i != -1) {
            lastCompTargetName = lastCompTargetName.substring(i + 1);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("bazel-bin/").append(label.getPackagePath()).append("/_javac/").append(label.getTargetName())
                .append("/").append(targetKind == TargetKind.JAVA_LIBRARY ? "lib" : "").append(lastCompTargetName)
                .append("_classes");
        return sb.toString();
    }

}
