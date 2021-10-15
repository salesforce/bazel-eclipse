/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.sdk.lang.jvm.external;

 import java.io.File;
 import java.util.List;

import com.salesforce.bazel.sdk.index.jvm.jar.JarIdentifier;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
 import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

 /**
  * An instance of one of the supported types of Maven jar integration (maven_install, etc)
  */
 public class BazelExternalJarRuleType {
     public final String ruleName;
     protected final OperatingEnvironmentDetectionStrategy os;

     protected List<File> downloadedJarLocations;
     protected boolean isUsedInWorkspace = false;

     /**
      * This ctor should only be used by subclasses.
      */
     public BazelExternalJarRuleType(String ruleName, OperatingEnvironmentDetectionStrategy os) {
         this.ruleName = ruleName;
         this.os = os;
         downloadedJarLocations = null;
     }

     /**
      * Use this method only if there is a new rule type, and you don't want to implement a specialized subclass.
      * Normally this is only used for tests, but if someone implemented a rule that used ~/.m2/repository this would be
      * the way to implement it.
      */
     public BazelExternalJarRuleType(String ruleName, OperatingEnvironmentDetectionStrategy os,
             List<File> downloadedJarLocations, boolean isUsedInWorkspace) {
         this.ruleName = ruleName;
         this.os = os;
         this.downloadedJarLocations = downloadedJarLocations;
         this.isUsedInWorkspace = isUsedInWorkspace;
     }

     /**
      * This rule type is known to the Bazel SDK, but is it being used by the workspace? Specialized implementations of
      * this method will likely look into the WORKSPACE file to determine this.
      */
     public boolean isUsedInWorkspace(BazelWorkspace bazelWorkspace) {
         return isUsedInWorkspace;
     }

     /**
      * Get the locations of the local jars downloaded from the remote repo. These are the root directories, and the jars
      * can be nested at arbitrary depths below each of these locations.
      */
     public List<File> getDownloadedJarLocations(BazelWorkspace bazelWorkspace) {
         return downloadedJarLocations;
     }

     /**
      * Something about the workspace changed. Discard computed work for the passed workspace. If the parameter is null,
      * discard the work for all workspaces.
      */
     public void discardComputedWork(BazelWorkspace bazelWorkspace) {
     }
     
     /**
      * Is the passed file path a file downloaded by this rule type?
      */
     public boolean doesBelongToRuleType(BazelWorkspace bazelWorkspace, String absoluteFilepath) {
         return false;
     }

     /**
      * Attempt to derive the Bazel label for this external jar file based solely on filepath.
      * \@maven//:org_slf4j_slf4j_api
      * This won't be possible in all cases; returning null is acceptable.
      */
     public String deriveBazelLabel(BazelWorkspace bazelWorkspace, String absoluteFilepath, JarIdentifier jarId) {
         return null;
     }

 }
