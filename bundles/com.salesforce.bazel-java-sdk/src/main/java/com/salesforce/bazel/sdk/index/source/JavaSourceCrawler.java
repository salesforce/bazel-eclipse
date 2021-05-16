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
package com.salesforce.bazel.sdk.index.source;

import java.io.File;
import java.io.FilenameFilter;

import com.salesforce.bazel.sdk.index.JvmCodeIndex;
import com.salesforce.bazel.sdk.index.model.ClassIdentifier;
import com.salesforce.bazel.sdk.index.model.CodeLocationDescriptor;
import com.salesforce.bazel.sdk.index.model.CodeLocationIdentifier;
import com.salesforce.bazel.sdk.util.BazelPathHelper;

/**
 * Crawler that descends into nested directories of source files and adds found files
 * to the index.
 */
public class JavaSourceCrawler {
    private final JvmCodeIndex index;
    private final String artifactMarkerFileName;

    public JavaSourceCrawler(JvmCodeIndex index, String artifactMarkerFileName) {
        this.index = index;
        this.artifactMarkerFileName = artifactMarkerFileName;
    }

    public void index(File basePath) {
        indexRecur(basePath, "", null, true);
    }

    protected void indexRecur(File path, String relativePathToClosestArtifact, CodeLocationDescriptor closestArtifactLocationDescriptor,
            boolean isRootDir) {
        if (path.isDirectory()) {
            // have we descended into a new artifact? (i.e. directory contains pom.xml for Maven, or BUILD for Bazel)
            String[] artifactMarkerSearch = path.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return artifactMarkerFileName.equals(name);
                }});
            if (artifactMarkerSearch.length > 0) {
                String parentId = "";
                if (closestArtifactLocationDescriptor != null) {
                    parentId = closestArtifactLocationDescriptor.id.locationIdentifier + File.separatorChar;
                }
                CodeLocationIdentifier myId = new CodeLocationIdentifier(parentId+relativePathToClosestArtifact);
                closestArtifactLocationDescriptor = new CodeLocationDescriptor(path, myId);

                index.addArtifactLocation(path.getName(), closestArtifactLocationDescriptor);
                relativePathToClosestArtifact = "";
            }
        }

        File[] candidateFiles = path.listFiles();
        String packageName = null;
        for (File candidateFile : candidateFiles) {
            try {
                if (candidateFile.isDirectory()) {
                    if (isRootDir && candidateFile.getName().startsWith("bazel-")) {
                        // this is a soft link into the output folders, ignore
                        continue;
                    }
                    if (isRootDir && candidateFile.getName().equals("tools")) {
                        // this is the standard location for bazel build tools, ignore //tools
                        continue;
                    }
                    String childRelative = candidateFile.getName();
                    if (!relativePathToClosestArtifact.isEmpty()) {
                        childRelative =
                                BazelPathHelper.osSeps(relativePathToClosestArtifact + "/" + candidateFile.getName()); // $SLASH_OK
                    }
                    indexRecur(candidateFile, childRelative, closestArtifactLocationDescriptor, false);
                } else if (candidateFile.canRead()) {
                    if (candidateFile.getName().endsWith(".java")) {
                        packageName = foundSourceFile(candidateFile, closestArtifactLocationDescriptor, packageName);
                    }
                }
            }
            catch (Exception anyE) {
                log("Reading java source file lead to unexpected error", anyE.getMessage(), candidateFile.getPath());
                anyE.printStackTrace();
            }
        }
    }

    protected String foundSourceFile(File javaSourceFile, CodeLocationDescriptor artifactLocationDescriptor, String packageName) {
        // isolate the classname by stripping off the .java and the prefix
        String fqClassName = javaSourceFile.getPath();
        fqClassName = fqClassName.substring(0, fqClassName.length()-5);
        int javaIndex = fqClassName.indexOf("java"); // TODO this only works for projects that follow Maven conventions
        fqClassName = fqClassName.substring(javaIndex+5);
        fqClassName = fqClassName.replace(File.pathSeparator, ".");

        ClassIdentifier classId = new ClassIdentifier(fqClassName);
        SourceFileIdentifier sourceFileId = new SourceFileIdentifier(artifactLocationDescriptor, classId);
        CodeLocationDescriptor sourceLocationDescriptor = new CodeLocationDescriptor(javaSourceFile, sourceFileId);

        sourceLocationDescriptor.addClass(classId);
        index.addClassnameLocation(fqClassName, sourceLocationDescriptor);

        return packageName;
    }

    protected static void log(String msg, String param1, String param2) {
        if (param1 == null) {
            System.err.println(msg);
        } else if (param2 == null) {
            System.err.println(msg+" ["+param1+"] ");
        } else {
            System.err.println(msg+" ["+param1+"] ["+param2+"]");
        }
    }

}
