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

import com.salesforce.bazel.sdk.index.index.CodeIndex;
import com.salesforce.bazel.sdk.index.jar.JarIdentiferResolver;
import com.salesforce.bazel.sdk.index.jar.JavaJarCrawler;
import com.salesforce.bazel.sdk.index.jar.MavenLayoutJarIdentifierResolver;
import com.salesforce.bazel.sdk.index.source.JavaSourceCrawler;

public class CodeIndexer {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: CodeIndexer [full path to source root directory] [full path to external java jars directory]");
            return;
        }
        String sourceRoot = args[0];
        String externalJarRoot = args[1];

        System.out.println("Generating index for source tree ["+sourceRoot+"] with external java jars ["+externalJarRoot+"] ...");
        long startTime = System.currentTimeMillis();

        File sourceRootFile = new File(sourceRoot);
        if (!sourceRootFile.exists()) {
            System.err.println("Provided source code root directory does not exist.");
            return;
        }

        if (externalJarRoot.contains("bazel-out")) {
            if (externalJarRoot.endsWith("bin")) {
                externalJarRoot = externalJarRoot + "/external";
            }
        }
        File externalJarRootFile = new File(externalJarRoot);
        if (!externalJarRootFile.exists()) {
            System.err.println("Provided external java jars directory does not exist.");
            return;
        }

        CodeIndex index = new CodeIndex();
        JarIdentiferResolver jarResolver = pickJavaJarResolver(externalJarRoot);
        if (jarResolver != null) {
            JavaJarCrawler jarCrawler = new JavaJarCrawler(index, jarResolver);
            jarCrawler.index(externalJarRootFile, true);
        }
        JavaSourceCrawler sourceCrawler = new JavaSourceCrawler(index, pickJavaSourceArtifactMarker(externalJarRoot));
        sourceCrawler.index(sourceRootFile);

        long endTime = System.currentTimeMillis();

        index.printIndex();
        System.out.println("\nTotal processing time (milliseconds): "+(endTime-startTime));
    }

    private static JarIdentiferResolver pickJavaJarResolver(String jarRepoPath) {
        if (jarRepoPath.contains(".m2/repository")) {
            return new MavenLayoutJarIdentifierResolver("repository");
        } else if (jarRepoPath.contains("bazel-out")) {
            return new MavenLayoutJarIdentifierResolver("public");
        }
        return null;
    }

    private static String pickJavaSourceArtifactMarker(String jarRepoPath) {
        if (jarRepoPath.contains(".m2/repository")) {
            return "pom.xml";
        } else if (jarRepoPath.contains("bazel-out")) {
            return "BUILD";
        }
        return null;
    }
}
