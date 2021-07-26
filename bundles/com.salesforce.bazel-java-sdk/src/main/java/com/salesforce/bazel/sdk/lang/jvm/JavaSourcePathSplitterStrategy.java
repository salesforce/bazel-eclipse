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
package com.salesforce.bazel.sdk.lang.jvm;

import java.io.File;

import com.salesforce.bazel.sdk.path.SourcePathSplitterStrategy;
import com.salesforce.bazel.sdk.path.SplitSourcePath;

/**
 * Takes a project relative path to a Java source file (source/java/com/salesforce/foo/Foo.java), and splits the path
 * into two parts:
 * <ul>
 * <li>source/java</li>
 * <li>com/salesforce/foo/Foo.java</li>
 * </ul>
 * <p>
 * To do this the splitter must parse the .java file to see the "package com.salesforce.foo;" statement which makes this
 * operation a bit expensive.
 * <p>
 * There is an anti-pattern of not using the package hierarchy in the path to the source files (e.g.
 * source/java/Foo.java) which is supported by Bazel and other build systems but is definitely not what people should be
 * doing. This anti-pattern is supported by this splitter when detected by returning the whole path, minus the file, as
 * the SplitSourcePath.sourceDirectoryPath. This may work with some tools (Bazel, for example, is ok with this), but
 * other tools (e.g. Eclipse) will flag this use case as an error.
 */
public class JavaSourcePathSplitterStrategy extends SourcePathSplitterStrategy {

    /**
     * if true, this tells the splitter to tolerate the case where the package name is not in the hierarchy; the tool
     * can set this to false to disable this behavior
     */
    public static boolean allowMissingPackageHierarchy = true;

    @Override
    public SplitSourcePath splitSourcePath(File basePath, String relativePathToSourceFile) {
        // we expect relativePathToSourceFile to be like this:  source/java/com/salesforce/foo/Foo.java

        String packageName = null;
        if (relativePathToSourceFile.endsWith(".java")) {
            packageName = findJavaFilePackage(basePath, relativePathToSourceFile);
        }

        // We expect the package name, like: com.salesforce.foo
        if (packageName == null) {
            // not enough information to split the path correctly
            return null;
        }
        // convert 'com.salesforce.foo' to 'com/salesforce/foo'
        String namespacePath = packageName.replace(".", File.separator);

        // split it
        SplitSourcePath sourcePath = SplitSourcePath.splitNamespacedPath(relativePathToSourceFile, namespacePath,
            allowMissingPackageHierarchy);

        return sourcePath;
    }

    /**
     * This method finds the Java file on the file system, reads it, and returns the package name (with dot separators)
     */
    private static String findJavaFilePackage(File basePath, String relativePathToSourceFile) {
        File srcFile = new File(basePath + File.separator + relativePathToSourceFile);
        JavaSourceFile javaSrcFile = new JavaSourceFile(srcFile);
        String packageName = javaSrcFile.readPackageFromFile();

        return packageName;

    }
}
