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
 */
package com.salesforce.bazel.sdk.index;

import java.io.File;

import com.salesforce.bazel.sdk.index.jar.JarIdentiferResolver;
import com.salesforce.bazel.sdk.index.jar.JavaJarCrawler;
import com.salesforce.bazel.sdk.index.source.JavaSourceCrawler;
import com.salesforce.bazel.sdk.util.BazelPathHelper;

/**
 * Indexer for building a JVM type index from nested sets of directories. Supports indexing both source files, and
 * compiled classes in jar files. Includes a command line launcher.
 */
public class JvmCodeIndexer {
    protected String sourceRoot;
    protected String externalJarRoot;

    // COMMAND LINE LAUNCHER

    // This is the best way to learn how to use the indexer.

    // USE CASE 1: Bazel Workspace
    // Bazel workspace location on file system: /home/mbenioff/dev/myrepo
    //
    // java -jar bazel-java-sdk.jar com.salesforce.bazel.sdk.index.FileSystemCodeIndexer \
    //      /home/mbenioff/dev/myrepo/bazel-bin/external /home/mbenioff/dev/myrepo

    // USE CASE 2: Maven repository
    // Maven repository location on file system: /home/mbenioff/.m2/repository
    //
    // java -jar bazel-java-sdk.jar com.salesforce.bazel.sdk.index.FileSystemCodeIndexer \
    //      /home/mbenioff/.m2/repository

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println(
                "Usage: CodeIndexer [full path to external java jars directory] [optional full path to source root directory]");
            return;
        }

        // build the indexer
        String externalJarRoot = args[0];
        String sourceRoot = null;
        if (args.length > 1) {
            sourceRoot = args[1];
        }
        JvmCodeIndexer indexer = new JvmCodeIndexer(externalJarRoot, sourceRoot);

        // run the index
        long startTime = System.currentTimeMillis();
        JvmCodeIndex index = indexer.buildIndex();
        long endTime = System.currentTimeMillis();

        // print the results
        index.printIndex();
        System.out.println("\nTotal processing time (milliseconds): " + (endTime - startTime));

    }

    // INDEXER

    public JvmCodeIndexer(String externalJarRoot, String sourceRoot) {
        this.sourceRoot = sourceRoot;
        this.externalJarRoot = externalJarRoot;
    }

    public JvmCodeIndex buildIndex() {
        JvmCodeIndex index = new JvmCodeIndex();

        if (externalJarRoot.contains("bazel-out")) {
            if (externalJarRoot.endsWith("bin")) {
                externalJarRoot = BazelPathHelper.osSeps(externalJarRoot + "/" + "external"); // $SLASH_OK
            }
        }
        File externalJarRootFile = new File(externalJarRoot);
        if (!externalJarRootFile.exists()) {
            logError("The provided external java jars directory does not exist. This is invalid.");
            return index;
        }

        JarIdentiferResolver jarResolver = pickJavaJarResolver(externalJarRoot);
        if (jarResolver != null) {
            JavaJarCrawler jarCrawler = new JavaJarCrawler(index, jarResolver);
            jarCrawler.index(externalJarRootFile, true);
        } else {
            logInfo("Could not determine the build system (maven/bazel) from the jar root. Skipping jar scanning...");
        }

        if (sourceRoot != null) {
            File sourceRootFile = new File(sourceRoot);
            if (!sourceRootFile.exists()) {
                logInfo("The provided source code root directory does not exist. This is ok.");
                return index;
            }
            JavaSourceCrawler sourceCrawler =
                    new JavaSourceCrawler(index, pickJavaSourceArtifactMarker(externalJarRoot));
            sourceCrawler.index(sourceRootFile);
        } else {
            logInfo("The provided source code root directory does not exist. This is ok.");
        }

        return index;
    }

    private static JarIdentiferResolver pickJavaJarResolver(String jarRepoPath) {
        return new JarIdentiferResolver();
    }

    private static String pickJavaSourceArtifactMarker(String jarRepoPath) {
        if (jarRepoPath.contains(BazelPathHelper.osSeps(".m2/repository"))) { // $SLASH_OK
            return "pom.xml";
        } else if (jarRepoPath.contains("bazel-out") || jarRepoPath.contains("bazel-bin")) {
            return "BUILD";
        }
        return null;
    }

    // override class to redirect this to a real logger
    protected void logInfo(String msg) {
        System.out.println(msg);
    }

    // override class to redirect this to a real logger
    protected void logError(String msg) {
        System.err.println(msg);
    }
}
