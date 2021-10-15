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
 import java.io.FilenameFilter;
 import java.util.ArrayList;
 import java.util.HashMap;
 import java.util.List;
 import java.util.Map;

import com.salesforce.bazel.sdk.index.jvm.jar.JarIdentifier;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

 /**
  * Specialization of BazelExternalJarRuleType for the maven_install rule.
  * <p>
  * The maven_install rule is a bit tricky, in that it is not reliable in where to find the downloaded jars. Also, the
  * source jars may not be there. This will be an evolving solution as we better understand how to make this more
  * reliable. We look for the downloaded jars in two entirely different locations:
  * bazel-outputbase/external/maven/v1/https/repo1.maven.org/maven2/com/google/guava/guava/20.0/guava-20.0-sources.jar
  * bazel-bin/maven/v1/https/ourinternalrepo.com/path/public/com/google/guava/guava/30.1-jre/guava-30.1-jre.jar
  */
 public class MavenInstallExternalJarRuleType extends BazelExternalJarRuleType {
     // these options are expected to be driven by tool preferences
     public static boolean cachedJars_supplyCoursierCacheLocation = false;
     public static boolean cachedJars_supplyWorkspaceBazelBinLocations = true;
     public static boolean cachedJars_supplyWorkspaceBazelOutputBaseLocations = true;

     // derived from the WORKSPACE file (or .bzl files included from the WORKSPACE)
     // each maven_install rule invocation must have a unique namespace, the default value is "maven"
     private static Map<String, List<String>> mavenInstallNamespaces;

     // maven_install can sometimes use coursier to download jars
     // delegate to a dedicated util to worry about that
     private final CoursierUtil coursierUtil = new CoursierUtil();

     public MavenInstallExternalJarRuleType(OperatingEnvironmentDetectionStrategy os) {
         super(BazelExternalJarRuleManager.MAVEN_INSTALL, os);
         init();
     }

     private void init() {
         mavenInstallNamespaces = new HashMap<>();
     }

     /**
      * This rule type is known to the Bazel SDK, but is it being used by the workspace? Specialized implementations of
      * this method will likely look into the WORKSPACE file to determine this.
      */
     @Override
     public boolean isUsedInWorkspace(BazelWorkspace bazelWorkspace) {
         // TODO BazelWorkspace should have some parsing functions for retrieving rule data.
         // until we have that, we are going to do a cheat and just look for the default maven namespace directory in bazel-bin/external
         File externalDir = new File(bazelWorkspace.getBazelBinDirectory(), "external");
         if (externalDir.exists()) {
             File[] markerFiles = externalDir.listFiles(new FilenameFilter() {
                 @Override
                 public boolean accept(File dir, String name) {
                     return "maven".equals(name) || "deprecated".equals(name); // TODO support multiple namespaces
                 }
             });
             isUsedInWorkspace = markerFiles.length > 0;
         }
         if (!isUsedInWorkspace) {
             File outputExternalDir = new File(bazelWorkspace.getBazelOutputBaseDirectory(), "external");
             if (outputExternalDir.exists()) {
                 File[] markerFiles = outputExternalDir.listFiles(new FilenameFilter() {
                     @Override
                     public boolean accept(File dir, String name) {
                         return "maven".equals(name) || "deprecated".equals(name); // TODO support multiple namespaces
                     }
                 });
                 isUsedInWorkspace = markerFiles.length > 0;
             }
         }

         return isUsedInWorkspace;
     }

     /**
      * Get the locations of the local jars downloaded from the remote repo. These are the root directories, and the jars
      * can be nested at arbitrary depths below each of these locations.
      */
     @Override
     public List<File> getDownloadedJarLocations(BazelWorkspace bazelWorkspace) {
         // workspace name is the key to our cached data
         String workspaceName = bazelWorkspace.getName();
         List<String> namespaces = mavenInstallNamespaces.get(workspaceName);

         // namespaces are cached, as they will change almost never
         if (namespaces == null) {
             namespaces = loadNamespaces(bazelWorkspace);
             mavenInstallNamespaces.put(workspaceName, namespaces);
         }

         // locations are computed each time, as they can change based bazel clean activities
         List<File> localJarLocationsNew = new ArrayList<>();
         if (cachedJars_supplyCoursierCacheLocation) {
             File coursierCacheLocation = coursierUtil.addCoursierCacheLocation(bazelWorkspace, os);
             if (coursierCacheLocation != null) {
                 localJarLocationsNew.add(coursierCacheLocation);
             }
         }
         if (cachedJars_supplyWorkspaceBazelBinLocations) {
             addBazelBinLocations(bazelWorkspace, namespaces, localJarLocationsNew);
         }
         if (cachedJars_supplyWorkspaceBazelOutputBaseLocations) {
             addBazelOutputBaseLocations(bazelWorkspace, namespaces, localJarLocationsNew);
         }

         // for thread safety, we build the list in a local var, and then switch at the end here
         downloadedJarLocations = localJarLocationsNew;
         return downloadedJarLocations;
     }

     /**
      * Something about the workspace changed. Discard computed work for the passed workspace. If the parameter is null,
      * discard the work for all workspaces.
      */
     @Override
     public void discardComputedWork(BazelWorkspace bazelWorkspace) {
         if (bazelWorkspace == null) {
             init();
             return;
         }
         String workspaceName = bazelWorkspace.getName();
         mavenInstallNamespaces.remove(workspaceName);

         coursierUtil.discardComputedWork(bazelWorkspace);
     }
     
     /**
      * Is the passed file path a file downloaded by this rule type?
      */
     @Override
     public boolean doesBelongToRuleType(BazelWorkspace bazelWorkspace, String absoluteFilepath) {
         // bazel-outputbase/external/maven/v1/https/ or bazel-bin/maven/v1/https
         return absoluteFilepath.contains("v1"+File.separator+"http");
     }
     
     /**
      * Attempt to derive the Bazel label for this external jar file based solely on filepath.
      * \@maven//:org_slf4j_slf4j_api
      * This won't be possible in all cases; returning null is acceptable.
      */
     @Override
     public String deriveBazelLabel(BazelWorkspace bazelWorkspace, String absoluteFilepath, JarIdentifier jarId) {
         // find the 'external' directory that contains this jar 
         File externalDir = new File(bazelWorkspace.getBazelBinDirectory(), "external");
         String externalPath = externalDir.getAbsolutePath();
         if (!absoluteFilepath.startsWith(externalPath)) {
             externalDir = new File(bazelWorkspace.getBazelOutputBaseDirectory(), "external");
             externalPath = externalDir.getAbsolutePath();
             if (!absoluteFilepath.startsWith(externalPath)) {
                 return null;
             }
         }
         
         // the next directory below the external directory is the maven_install namespace, make it relative
         // maven/v1/https/repo1.maven.org/maven2/com/google/guava/guava/20.0/guava-20.0-sources.jar
         String relativeFilepath = absoluteFilepath.substring(externalPath.length()+1); 
         String[] tokens = FSPathHelper.split(relativeFilepath);
         if (tokens.length == 0) {
             return null;
         }
         String mavenInstallNamespace = tokens[0];
         String groupName = jarId.group.replace("-", "_");
         groupName = jarId.group.replace(".", "_");
         String artifactName = jarId.artifact.replace("-", "_");
         String label = "@"+mavenInstallNamespace+"//:"+groupName+"_"+artifactName;
         return label;
     }
     

     
     // INTERNAL

     protected List<String> loadNamespaces(BazelWorkspace bazelWorkspace) {
         List<String> namespaces = new ArrayList<>();

         // TODO 'maven' is the default namespace, but there may be others.
         // for each invocation of maven_install rule, there is a distinct namespace identified by the name attribute:
         //        maven_install(name = "deprecated", ...
         // TODO BazelWorkspace should have some parsing functions for retrieving rule data.
         // in this case, we need to find maven_install rule invocations, and pluck the list of name attributes.
         // for our primary internal repo, this is complicated by the fact that the maven_install rules are actually in
         // .bzl files brought in by load() statements in the WORKSPACE
         namespaces.add("maven"); // TODO support multiple namespaces
         namespaces.add("deprecated"); // TODO support multiple namespaces

         return namespaces;
     }

     /**
      * maven_install will download jars (and sometimes source jars) into directories such as:
      * ROOT/bazel-bin/external/maven ROOT/bazel-bin/external/webtest if you have two maven_install rules with names
      * 'maven' and 'webtest'
      */
     protected void addBazelBinLocations(BazelWorkspace bazelWorkspace, List<String> namespaces,
             List<File> localJarLocationsNew) {
         File bazelbinDir = bazelWorkspace.getBazelBinDirectory();
         File externalDir = new File(bazelbinDir, "external");

         for (String namespace : namespaces) {
             File rootMavenInstallDir = new File(externalDir, namespace);
             localJarLocationsNew.add(rootMavenInstallDir);
         }
     }

     /**
      * maven_install will download jars (and sometimes source jars) into directories such as:
      * ROOT/bazel-bin/external/maven ROOT/bazel-bin/external/webtest if you have two maven_install rules with names
      * 'maven' and 'webtest'
      */
     protected void addBazelOutputBaseLocations(BazelWorkspace bazelWorkspace, List<String> namespaces,
             List<File> localJarLocationsNew) {
         File bazeloutputbaseDir = bazelWorkspace.getBazelOutputBaseDirectory();
         File externalDir = new File(bazeloutputbaseDir, "external");

         for (String namespace : namespaces) {
             File rootMavenInstallDir = new File(externalDir, namespace);
             localJarLocationsNew.add(rootMavenInstallDir);
         }
     }
     
     
}
