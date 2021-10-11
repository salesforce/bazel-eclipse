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

 /**
  * Isolates the code that looks up operating environment data so that it can be mocked for tests.
  *
  */
 public interface OperatingEnvironmentDetectionStrategy {

     /**
      * Returns the operating system running Bazel and our BEF: osx, linux, windows
      * https://github.com/bazelbuild/bazel/blob/c35746d7f3708acb0d39f3082341de0ff09bd95f/src/main/java/com/google/devtools/build/lib/util/OS.java#L21
      */
     String getOperatingSystemName();

     /**
      * Returns the OS identifier used in file system constructs: darwin, linux, windows
      */
     default String getOperatingSystemDirectoryName(String osName) {
         String operatingSystemFoldername = null;
         if (osName.contains("mac")) {
             operatingSystemFoldername = "darwin";
         } else if (osName.contains("darwin")) {
             operatingSystemFoldername = "darwin";
         } else if (osName.contains("win")) {
             operatingSystemFoldername = "windows";
         } else {
             // assume linux
             operatingSystemFoldername = "linux";
         }
         return operatingSystemFoldername;
     }

     /**
      * When running inside a tool (like an IDE) we sometimes want to handle errors and try to soldier on even if
      * something went awry. In particular, situations where timing issues impact an operation, the operation may get
      * rerun a little later and succeed.
      * <p>
      * But when we are running tests we want to be strict and fail on failures. This boolean should be set to true when
      * we are running tests.
      */
     default boolean isTestRuntime() {
         return false;
     }

 }
