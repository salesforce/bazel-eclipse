/**
 * Copyright (c) 2021, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.sdk.init;

import com.salesforce.bazel.sdk.aspect.AspectTargetInfoFactory;
import com.salesforce.bazel.sdk.aspect.jvm.JVMAspectTargetInfoFactoryProvider;
import com.salesforce.bazel.sdk.lang.jvm.JavaSourcePathSplitterStrategy;
import com.salesforce.bazel.sdk.lang.jvm.MavenProjectStructureStrategy;
import com.salesforce.bazel.sdk.model.BazelSourceFile;
import com.salesforce.bazel.sdk.model.BazelTargetKind;
import com.salesforce.bazel.sdk.path.SourcePathSplitterStrategy;
import com.salesforce.bazel.sdk.project.structure.ProjectStructureStrategy;

/**
 * Initializer to install support for Java rules into the SDK. Call initialize() once at startup.
 * <p>
 * This will eventually support other JVM langs like Kotlin and Scala.
 */
public class JvmRuleInit {

    // register the collection of Java rules that we want to handle
    public static final BazelTargetKind KIND_JAVA_LIBRARY = new BazelTargetKind("java_library", false, false);
    public static final BazelTargetKind KIND_JAVA_IMPORT = new BazelTargetKind("java_import", false, false);
    public static final BazelTargetKind KIND_JAVA_TEST = new BazelTargetKind("java_test", false, true);
    public static final BazelTargetKind KIND_JAVA_BINARY = new BazelTargetKind("java_binary", true, false);
    public static final BazelTargetKind KIND_SELENIUM_TEST = new BazelTargetKind("java_web_test_suite", false, true);
    public static final BazelTargetKind KIND_SPRINGBOOT = new BazelTargetKind("springboot", true, false);
    public static final BazelTargetKind KIND_PROTO_LIBRARY = new BazelTargetKind("java_proto_library", false, false);
    public static final BazelTargetKind KIND_PROTO_LITE_LIBRARY =
            new BazelTargetKind("java_lite_proto_library", false, false);
    public static final BazelTargetKind KIND_GRPC_LIBRARY = new BazelTargetKind("java_grpc_library", false, false);

    // The Maven strategy quickly locates source directories if it detects a Bazel package has a Maven-like layout (src/main/java).
    // We will always want the Maven strategy enabled for official builds, but for internal testing we sometimes want
    // to disable this so we can be sure the Bazel Query strategy is working for some of our internal repos that happen to
    // follow Maven conventions.
    public static boolean ENABLE_MAVEN_STRUCTURE_STRATEGY = true;

    /**
     * Call once at the start of the tool to initialize the JVM rule support.
     */
    public static void initialize() {
        // Provider that can parse the JVM specific bits out of the Aspect data files
        AspectTargetInfoFactory.addProvider(new JVMAspectTargetInfoFactoryProvider());

        // Identify .java files as source code files
        BazelSourceFile.sourceFileExtensions.add(".java");

        // Strategy impl for short cutting our computation of source folders if we detect that the project layout
        // follows Maven conventions
        if (ENABLE_MAVEN_STRUCTURE_STRATEGY) {
            ProjectStructureStrategy.projectStructureStrategies.add(0, new MavenProjectStructureStrategy());
        }

        // Strategy impl for splitting Java source file paths (source/java/com/salesforce/foo/Foo.java) into
        // the sourceDir part and file part (source/java and com/salesforce/foo/Foo.java). This is used when
        // a project does not follow Maven conventions and we have to determine the correct directories to use
        // as source folders
        SourcePathSplitterStrategy.splitterStrategies.put(".java", new JavaSourcePathSplitterStrategy());
    }

}
