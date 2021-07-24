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
 import java.util.HashMap;
 import java.util.Map;

 import com.salesforce.bazel.sdk.model.BazelWorkspace;
 import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

 /**
  * coursier is a Maven repo integration tool that is used in some cases by maven_install. This class encapsulates the
  * behaviors of coursier.
  */
 public class CoursierUtil {

     // TODO windows

     private static final String COURSIER_CACHE_LOCATION_LINUX = "/.cache/coursier/v1";
     private static final String COURSIER_CACHE_LOCATION_MACOS = "/Library/Caches/Coursier/v1";
     private static final String COURSIER_CACHE_LOCATION_WINDOWS = "/Coursier/cache/v1";
     private final Map<String, File> coursierCacheLocations = new HashMap<>();

     /**
      * If the user ran this: bazel run @unpinned_maven//:pin This invoked Coursier (a jar downloader) which populated
      * the Coursier cache on the machine. The cache location is platform dependent, and global per user. So if you have
      * multiple Bazel workspaces this cache location will have the union of all jars used by the workspaces.
      */
     public File addCoursierCacheLocation(BazelWorkspace bazelWorkspace, OperatingEnvironmentDetectionStrategy os) {

         // workspace name is the key to our cached data
         String workspaceName = bazelWorkspace.getName();
         File coursierCacheLocation = coursierCacheLocations.get(workspaceName);
         if (coursierCacheLocation == null) {
             // TODO we are just computing the default cache location, but there are ways to force coursier to use alternate locations.
             // we should also factor that in, but that will be HARD. Details:
             //     https://get-coursier.io/docs/2.0.0-RC5-3/cache.html#default-location

             String defaultLocation = null;
             String homedir = System.getProperty("user.home");
             String osName = os.getOperatingSystemName();
             if ("linux".equals(osName)) {
                 defaultLocation = homedir + COURSIER_CACHE_LOCATION_LINUX;
             } else if ("darwin".equals(osName)) {
                 defaultLocation = homedir + COURSIER_CACHE_LOCATION_MACOS;
             } else if ("windows".equals(osName)) {
                 defaultLocation = homedir + COURSIER_CACHE_LOCATION_WINDOWS;
             } else {
                 // or is there a better default?
                 defaultLocation = homedir + COURSIER_CACHE_LOCATION_LINUX;
             }

             coursierCacheLocation = new File(defaultLocation);
             coursierCacheLocations.put(workspaceName, coursierCacheLocation);
         }
         return coursierCacheLocation;
     }

     public void discardComputedWork(BazelWorkspace bazelWorkspace) {
         String workspaceName = bazelWorkspace.getName();
         coursierCacheLocations.remove(workspaceName);
     }
 }
