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
package com.salesforce.bazel.sdk.index.jvm.jar;

import java.io.File;
import java.util.zip.ZipFile;

/**
 * Resolver that decides if the passed File is an interesting jar file, and if so it will deduce GAV information from
 * the path.
 */
public class JarIdentiferResolver {

    public JarIdentiferResolver() {}

    public JarIdentifier resolveJarIdentifier(File gavRoot, File pathFile, ZipFile zipFile) {
    	if (gavRoot == null) {
    		return null;
    	}
    	if (pathFile == null) {
    		return null;
    	}
    	
        String path = pathFile.getAbsolutePath();

        // do some quick Bazel exclusions (TODO how best to identify the interesting jars in the Bazel output dirs?)
        if (path.contains("-hjar.jar")) {
            return null; // perf optimization jar, not intended for non-Bazel consumption
        }
        if (path.contains("-native-header.jar")) {
            return null; // perf optimization jar, not intended for non-Bazel consumption
        }
        if (path.contains("-class.jar")) {
            return null; // perf optimization jar, not intended for non-Bazel consumption
        }
        if (path.contains("-src.jar")) {
            return null; // source jar, this will be pulled in as an attribute of the main jar
        }
        if (path.contains("-sources.jar")) {
            return null; // source jar, this will be pulled in as an attribute of the main jar
        }
        if (path.contains("-gensrc.jar")) {
            return null; // generated source jar, this will be pulled in as an attribute of the main jar
        }
        if (path.contains("_deploy.jar")) {
            return null; // uber jar, which contains exploded classes from other jars
        }
        if (path.contains("Test.jar")) {
            return null; // by convention, a jar that contains a test to run in bazel
        }
        if (path.contains("IT.jar")) {
            return null; // by convention, a jar that contains a test to run in bazel
        }

        // Maven compatible: com/acme/libs/my-blue-impl/0.1.8/my-blue-impl-0.1.8.jar  $SLASH_OK comment
        // Bazel internal:   com/acme/libs/my-blue-impl/0.1.8/my-blue-impl-0.1.8-ijar.jar  $SLASH_OK comment

        String gavPart = path.substring(gavRoot.getAbsolutePath().length());
        String[] gavParts = gavPart.split(File.separator);

        // open-context-impl-0.1.8.jar => open-context-impl
        String artifact = gavParts[gavParts.length - 1];
        if (artifact.endsWith("-ijar.jar")) {
            // Bazel convention, need to customize it here because the extra hyphen confuses the logic below
            artifact = artifact.substring(0, artifact.length() - 9);
        } else if (artifact.endsWith(".jar")) {
            // standard jars
            artifact = artifact.substring(0, artifact.length() - 4);
        }
        int artifactVersionIndex = artifact.lastIndexOf("-");
        String version = "none";
        int groupOffset = 1;
        if (artifactVersionIndex != -1) {
            artifact = artifact.substring(0, artifactVersionIndex); // remove the embedded version
            version = gavParts[gavParts.length - 2];
            groupOffset = 3;
        }

        String group = "";
        boolean firstToken = true;
        for (int i = 0; i < (gavParts.length - groupOffset); i++) {
            if (!firstToken) {
                group = group + "." + gavParts[i];
            } else if (!gavParts[i].isEmpty()) {
                group = gavParts[i];
                firstToken = false;
            }
        }
        JarIdentifier id = new JarIdentifier(group, artifact, version);
        return id;
    }

    // test
    public static void main(String[] args) {

        // Maven build system
        JarIdentiferResolver resolver = new JarIdentiferResolver();
        File gavRoot = new File("/Users/mbenioff/.m2/repository"); // $SLASH_OK sample code
        File pathFile =
                new File("/Users/mbenioff/.m2/repository/com/acme/libs/my-blue-impl/0.1.8/my-blue-impl-0.1.8.jar"); // $SLASH_OK sample code
        JarIdentifier id = resolver.resolveJarIdentifier(gavRoot, pathFile, null);
        System.out.println(id.locationIdentifier);
        if (!id.locationIdentifier.equals("com.acme.libs:my-blue-impl:0.1.8")) {
            System.err.println("FAIL!");
        }

        // Bazel build system
        gavRoot = new File(
                "/tmp/_bazel_benioff/dsf87dsfsl/execroot/__main__/bazel-out/darwin-fastbuild/bin/external/maven/v1/https/benioff%40nexus.acme.com/nexus/content/groups/public"); // $SLASH_OK sample code
        pathFile = new File(
                "/tmp/_bazel_benioff/dsf87dsfsl/execroot/__main__/bazel-out/darwin-fastbuild/bin/external/maven/v1/https/benioff%40nexus.acme.com/nexus/content/groups/public/com/acme/libs/my-blue-impl/0.1.8/my-blue-impl-0.1.8-ijar.jar"); // $SLASH_OK sample code
        id = resolver.resolveJarIdentifier(gavRoot, pathFile, null);
        System.out.println(id.locationIdentifier);
        if (!id.locationIdentifier.equals("com.acme.libs:my-blue-impl:0.1.8")) {
            System.err.println("FAIL!");
        }
    }
}
