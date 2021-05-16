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
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.salesforce.bazel.eclipse.classpath;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.lang.jvm.ImplicitClasspathHelper;
import com.salesforce.bazel.sdk.lang.jvm.JvmClasspathEntry;
import com.salesforce.bazel.sdk.model.BazelWorkspace;

/**
 * Bazel generally requires BUILD file authors to list all dependencies explicitly. However, there are a few legacy
 * cases in which dependencies are implied. For example, java_test implicitly brings in junit, hamcrest,
 * javax.annotation libraries.
 * <p>
 * This is unfortunate because external tools that need to construct the dependency graph (ahem, that's us, for JDT) we
 * need to know to append the implicit dependencies to the explicit ones identified by the Aspect.
 * <p>
 * This is a helper class for computing implicit dependencies. See https://github.com/salesforce/bazel-eclipse/issues/43
 * for details and design considerations for this class. $SLASH_OK url
 * <p>
 * This code is isolated from the classpath container code because this is somewhat of a hack and it is nice to have it
 * isolated.
 */
public class EclipseImplicitClasspathHelper extends ImplicitClasspathHelper {

    Set<IClasspathEntry> computeImplicitDependencies(IProject eclipseIProject, BazelWorkspace bazelWorkspace,
            AspectTargetInfo targetInfo) throws IOException {
        Set<IClasspathEntry> deps = new HashSet<>();
        Set<JvmClasspathEntry> generic_deps = new HashSet<>();

        generic_deps = this.computeImplicitDependencies(bazelWorkspace, targetInfo);
        if (generic_deps.size() > 0) {
            // convert the generic classpath entries into Eclipse classpath entries
            for (JvmClasspathEntry generic_dep : generic_deps) {
                // now manufacture the classpath entry
                IPath runnerJarPath = org.eclipse.core.runtime.Path.fromOSString(generic_dep.pathToJar);
                IPath sourceAttachmentPath = null;
                IPath sourceAttachmentRootPath = null;
                boolean isTestLib = generic_dep.isTestJar;
                IClasspathEntry runnerJarEntry = BazelPluginActivator.getJavaCoreHelper().newLibraryEntry(runnerJarPath,
                    sourceAttachmentPath, sourceAttachmentRootPath, isTestLib);
                deps.add(runnerJarEntry);
            }
        }
        return deps;
    }
}
