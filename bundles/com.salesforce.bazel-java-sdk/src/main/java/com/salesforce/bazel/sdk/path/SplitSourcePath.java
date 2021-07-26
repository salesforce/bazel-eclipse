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
package com.salesforce.bazel.sdk.path;

import java.io.File;

/**
 * Models a project/package relative path to a source file, and splits the path into two parts like:
 * <ul>
 * <li>source/java</li>
 * <li>com/salesforce/foo/Foo.java</li>
 * </ul>
 */
public class SplitSourcePath {

    /**
     * The source directory part of the original path. Example: source/java
     */
    public String sourceDirectoryPath;

    /**
     * The file path part of the original path. Example: com/salesforce/foo/Foo.java
     */
    public String filePath;

    /**
     * Utility function that can be useful for all languages that use directory paths to express namespace/package (like
     * Java). It splits the passed path into the dir and file parts. You must know the namespace/package path in order
     * for this function to know where to make the split. The package path is usually retrieved from parsing the file
     * itself.
     *
     * @param relativePathToSourceFile
     *            source/java/com/salesforce/foo/Foo.java
     * @param packagePath
     *            com/salesforce/foo; default package would be empty string
     */
    public static SplitSourcePath splitNamespacedPath(String relativePathToSourceFile, String packagePath) {
        return splitNamespacedPath(relativePathToSourceFile, packagePath, false);
    }

    /**
     * Utility function that can be useful for all languages that use directory paths to express namespace/package (like
     * Java). It splits the passed path into the dir and file parts. You must know the namespace/package path in order
     * for this function to know where to make the split. The package path is usually retrieved from parsing the file
     * itself.
     *
     * @param relativePathToSourceFile
     *            source/java/com/salesforce/foo/Foo.java
     * @param packagePath
     *            com/salesforce/foo; default package would be empty string
     * @param allowMissingPackageHierarchy
     *            if true, this method will return the entire path (minus the last resource) as the source dir if the
     *            namespace hierarchy is not reflected in the relative path (e.g. src/java/Foo.java, and the package
     *            name is com.salesforce.foo). if false, this method will return null in this case
     */
    public static SplitSourcePath splitNamespacedPath(String relativePathToSourceFile, String packagePath,
            boolean allowMissingPackageHierarchy) {
        if ((relativePathToSourceFile == null) || (packagePath == null)) {
            return null;
        }

        if (relativePathToSourceFile.startsWith(File.separator)) {
            relativePathToSourceFile = relativePathToSourceFile.substring(1);
        }
        if (relativePathToSourceFile.endsWith(File.separator)) {
            relativePathToSourceFile = relativePathToSourceFile.substring(0, relativePathToSourceFile.length() - 1);
        }

        // Strip the filename:  source/java/com/salesforce/foo
        int lastSlash = relativePathToSourceFile.lastIndexOf(File.separator);
        if (lastSlash == -1) {
            // what?
            return null;
        }
        String relativePathToWithoutSourceFile = relativePathToSourceFile.substring(0, lastSlash);

        // We can now expect that relativePathToWithoutSourceFile ends with packagePath
        SplitSourcePath sourcePath = new SplitSourcePath();
        if (relativePathToWithoutSourceFile.endsWith(packagePath)) {
            // hooray, we know what we are doing, do some fancy math to figure it out
            int lastIndex = relativePathToWithoutSourceFile.length() - packagePath.length();
            if (packagePath.length() == 0) {
                // default package, makes for weird math
                lastIndex = relativePathToWithoutSourceFile.length() + 1;
            }
            sourcePath.sourceDirectoryPath = relativePathToWithoutSourceFile.substring(0, lastIndex - 1);
            sourcePath.filePath = relativePathToSourceFile.substring(lastIndex);
        } else {
            // This is a case in which the workspace file layout does not use the standard convention of encoding
            // the package/namespace into the file hierarchy. This might be legal but is a bad practice.
            if (allowMissingPackageHierarchy) {
                // In this case the best we can do is take the entire hierarchy as the source directory
                // source/java/Foo.java => source/java, Foo.java
                sourcePath.sourceDirectoryPath = relativePathToWithoutSourceFile;
                sourcePath.filePath = relativePathToSourceFile.substring(lastSlash + 1);
            } else {
                sourcePath = null;
            }
        }

        return sourcePath;

    }
}
