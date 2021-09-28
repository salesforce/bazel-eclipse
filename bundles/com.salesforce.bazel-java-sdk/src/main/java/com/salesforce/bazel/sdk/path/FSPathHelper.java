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
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;

/**
 * Constants and utils for file system paths.
 */
public final class FSPathHelper {
    private static final LogHelper LOG = LogHelper.log(FSPathHelper.class);

    // Slash character for unix file paths
    public static final String UNIX_SLASH = "/";

    // Regex pattern to use to capture the / for unix file paths.
    // Please consider calling osSepRegex() instead of using this directly.
    // This is just /. The variable name is what we are after - make it clear this is a regex use case
    // so we have visual cue that we need to use the Windows regex if on Windows.
    public static final String UNIX_SLASH_REGEX = "/";

    // Backslash character; this is provided as a constant to help code searches
    // for Windows specific code. There are two backslash characters because Java
    // requires a leading backslash to encode a backslash
    public static final String WINDOWS_BACKSLASH = "\\";

    // Regex pattern to use to look for a single backslash character in a path.
    // Please consider calling osSepRegex() instead of using this directly.
    // Why 4 backslashes?
    // Regex needs a double \ to escape backslash in the matcher (1+1=2)
    // Java requires a backslash to encode a backslash in the String (2x2=4)
    public static final String WINDOWS_BACKSLASH_REGEX = "\\\\";

    // Slash character for file paths in jar files
    public static final String JAR_SLASH = "/";
    
    private FSPathHelper() {

    }

    /**
     * Primary feature toggle. isUnix is true for all platforms except Windows. TODO this needs to be reworked in SDK
     * Issue #32. Only should be tweaked manually for tests
     */
    public static boolean isUnix = true;
    static {
        if (System.getProperty("os.name").contains("Windows")) {
            FSPathHelper.isUnix = false;
        }
    }

    /**
     * Resolve softlinks and other abstractions in the workspace paths.
     */
    public static File getCanonicalFileSafely(File directory) {
        if (directory == null) {
            return null;
        }
        try {
            directory = directory.getCanonicalFile();
        } catch (IOException ioe) {
            LOG.error("error locating path [{}] on the file system", ioe, directory.getAbsolutePath());
        }
        return directory;
    }

    /**
     * Resolve softlinks and other abstractions in the workspace paths.
     */
    public static String getCanonicalPathStringSafely(File directory) {
        String path = null;
        if (directory == null) {
            return null;
        }
        try {
            path = directory.getCanonicalPath();
        } catch (IOException ioe) {
            LOG.error("error locating path [{}] on the file system", ioe, directory.getAbsolutePath());
        }
        if (path == null) {
            // fallback to absolute path in case canonical path fails
            path = directory.getAbsolutePath();
        }
        return path;
    }

    /**
     * Resolve softlinks and other abstractions in the workspace paths.
     */
    public static String getCanonicalPathStringSafely(String path) {
        if (path == null) {
            return null;
        }
        try {
            path = new File(path).getCanonicalPath();
        } catch (IOException ioe) {
            LOG.error("error locating path [{}] on the file system", ioe, path);
        }
        return path;
    }

    public static String osSepRegex() {
        if (isUnix) {
            return UNIX_SLASH_REGEX;
        }
        return WINDOWS_BACKSLASH_REGEX;
    }

    /**
     * Convert a slash style relative path to Windows backslash, if running on Windows
     */
    public static String osSeps(String unixStylePath) {
        String path = unixStylePath;
        if (!isUnix) {
            path = unixStylePath.replace(UNIX_SLASH, WINDOWS_BACKSLASH);
        }
        return path;
    }

    /**
     * Convert a slash style relative path to Windows backslash, if running on Windows. Replace with two back slashes if
     * so, as the consumer needs escaped backslashes.
     */
    public static String osSepsEscaped(String unixStylePath) {
        String path = unixStylePath;
        if (!isUnix) {
            // Basic case: a/b/c => a\\b\\c
            
            // Variants
            // 1. we need to handle the case where a path has been converted already slash->backslash, and then is passed into
            // this method, which then needs to escape each backslash in addition to converting any new slashes
            // a/b\c => a\\b\\c
            // 2. we need to handle the case where a path has been converted and escaped already slash->backslash+backslash, 
            // and then is passed into this method, which then needs to escape each backslash in addition to converting any new slashes
            // a/b\\c\d => a\\b\\c\\d
            
            path = unixStylePath.replace(WINDOWS_BACKSLASH + WINDOWS_BACKSLASH, WINDOWS_BACKSLASH);
            path = path.replace(UNIX_SLASH, WINDOWS_BACKSLASH);
            path = path.replace(WINDOWS_BACKSLASH, WINDOWS_BACKSLASH + WINDOWS_BACKSLASH);
        }
        return path;
    }

    /**
     * Given a set of tokens, this function walks through the provided path looking for a node in the hierarchy with
     * that name. If a resource (file/directory) is found, this function returns true.
     * <p>
     * An example use case is looking for a directory named 'test' or 'tests' in a file system path.
     */
    public static boolean doesPathContainNamedResource(String filesystemPath, Set<String> targetNames) {
        String[] pathElements = filesystemPath.split(osSepRegex());

        for (String element : pathElements) {
            if (targetNames.contains(element)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes the last resource from the path.
     * <p>
     * Example: source/java/com/salesforce/foo/Foo.java => source/java/com/salesforce/foo
     */
    public static String removeLastResource(String filesystemPath) {
        int lastSlash = filesystemPath.lastIndexOf(File.separator);
        if (lastSlash > -1) {
            return filesystemPath.substring(0, lastSlash);
        }
        return "";
    }

    /**
     * Utility to find all files in a tree with a particular extension.
     */
    public static Set<File> findFileLocations(File dir, String extension, WorkProgressMonitor monitor, int depth) {
        if (!dir.isDirectory()) {
            return null;
        }
        Set<File> fileLocations = new TreeSet<>();

        try {

            // collect all Java files
            List<Path> files = new ArrayList<>(1000);

            Path start = dir.toPath();
            Files.walkFileTree(start, new FileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (isFileWithExtension(file, extension)) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

            });

            // normally in the SDK we do not use Java streams, to make the code more accessible, but the parallel
            // streaming here really speeds up the file system scan
            Set<File> syncSet = Collections.synchronizedSet(fileLocations);
            files.parallelStream().forEach(file -> {
                syncSet.add(FSPathHelper.getCanonicalFileSafely(file.toFile()));
            });

        } catch (Exception anyE) {
            LOG.error("ERROR scanning for files with extension {} in location {}", anyE, extension,
                dir.getAbsolutePath());
        }
        return fileLocations;
    }

    private static boolean isFileWithExtension(Path candidate, String extension) {
        return candidate.getFileName().toString().endsWith(extension);
    }

}
