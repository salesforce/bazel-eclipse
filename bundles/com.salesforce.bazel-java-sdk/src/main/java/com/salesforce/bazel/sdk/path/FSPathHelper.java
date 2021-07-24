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

 import com.salesforce.bazel.sdk.logging.LogHelper;

 /**
  * Constants and utils for file system paths.
  */
 public final class FSPathHelper {
     private static final LogHelper LOG = LogHelper.log(FSPathHelper.class);

     // Slash character for unix file paths
     public static final String UNIX_SLASH = "/";

     // Backslash character; this is provided as a constant to help code searches
     // for Windows specific code. There are two backslash characters because Java
     // requires a leading backslash to encode a backslash
     public static final String WINDOWS_BACKSLASH = "\\";

     // Regex pattern to use to look for a single backslash character in a path
     // why 4?
     // Regex needs a double \ to escape backslash in the matcher (1+1=2)
     // Java requires a backslash to encode a backslash in the String (2x2=4)
     public static final String WINDOWS_BACKSLASH_REGEX = "\\\\";

     // Slash character for file paths in jar files
     public static final String JAR_SLASH = "/";

     private FSPathHelper() {

     }

     /**
      * Primary feature toggle. isUnix is true for all platforms except Windows. TODO this needs to be reworked in SDK
      * Issue #32
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
             return UNIX_SLASH;
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
             path = unixStylePath.replace(UNIX_SLASH, WINDOWS_BACKSLASH);
         }
         return path;
     }

 }
