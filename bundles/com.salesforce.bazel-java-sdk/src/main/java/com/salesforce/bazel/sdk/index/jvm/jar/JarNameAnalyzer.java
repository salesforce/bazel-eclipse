/**
 * Copyright (c) 2022, Salesforce.com, Inc. All rights reserved.
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
 */
package com.salesforce.bazel.sdk.index.jvm.jar;

/**
 * Utility methods for analyzing jar file names.
 * <p>
 * <b>ijars:</b> (from the Bazel docs)
 * The purpose of ijar is to produce, from a .jar file, a much smaller,
 * simpler .jar file containing only the parts that are significant for
 * the purposes of compilation.  In other words, an interface .jar
 * file.  By changing ones compilation dependencies to be the interface
 * jar files, unnecessary recompilation is avoided when upstream
 * changes don't affect the interface.
 * <p>
 * <b>header jars:</b>
 * Similar to ijars. In addition, there are also native header files which are
 * jars containing CC header files supporting native method implementation.
 * <p>
 * <b>test jars:</b>
 * Bazel builds a runnable test jar for each test in your package. This will include junit and other harness classpath jars.
 * <p>
 * <b>source jars:</b>
 * Standard pattern in the Java ecosystem where jars are created with the source code used to build an artifact. These are used
 * by IDEs typically.
 * <p>
 * <b>deploy jars</b>
 * Bazel builds an executable jar for java_binary and springboot targets. These contain full copies of the classpath jars.
 */
public class JarNameAnalyzer {
    /**
     * Does the jar file name indicate this is not a user-facing jar file for index purposes? Examples include internal
     * Bazel optimized jar files (the header jars) and test jars. 
     * <p>
     * See class comment for more information on the jar file types.
     */
    public static boolean doIgnoreJarFile(String jarFileName, boolean ignoreHeaderJars, boolean ignoreTestJars,
            boolean ignoreInterfaceJars, boolean ignoreSourceJars, boolean ignoreDeployJars) {

        if (ignoreHeaderJars) {
            if (jarFileName.endsWith("-hjar.jar")) {
                return true; // perf optimization jar, not intended for non-Bazel consumption
            }
            if (jarFileName.startsWith("header_")) {
                return true; // perf optimization jar, not intended for non-Bazel consumption
            }
            if (jarFileName.endsWith("-native-header.jar")) {
                return true; // perf optimization jar, not intended for non-Bazel consumption
            }
            if (jarFileName.endsWith("-class.jar")) {
                return true; // perf optimization jar, not intended for non-Bazel consumption
            }
        }

        if (ignoreInterfaceJars && jarFileName.endsWith("-ijar.jar")) {
            return true;
        }

        if (ignoreSourceJars) {
            if (jarFileName.endsWith("-src.jar")) {
                return true; // source jar, this will be pulled in as an attribute of the main jar
            }
            if (jarFileName.endsWith("-sources.jar")) {
                return true; // source jar, this will be pulled in as an attribute of the main jar
            }
            if (jarFileName.endsWith("-gensrc.jar")) {
                return true; // generated source jar, this will be pulled in as an attribute of the main jar
            }
        }

        if (ignoreDeployJars && jarFileName.endsWith("_deploy.jar")) {
            return true; // uber jar, which contains exploded classes from other jars
        }

        if (ignoreInterfaceJars && jarFileName.endsWith("-ijar.jar")) {
            return true;
        }

        if (ignoreTestJars) {
            if (jarFileName.endsWith("Test.jar")) {
                return true; // by convention, a jar that contains a test to run in bazel
            }
            if (jarFileName.endsWith("IT.jar")) {
                return true; // by convention, a jar that contains a test to run in bazel
            }
        }
        return false;
    }

}
