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
import java.util.HashSet;
import java.util.Set;

import com.salesforce.bazel.sdk.index.CodeIndex;
import com.salesforce.bazel.sdk.index.model.CodeLocationDescriptor;
import com.salesforce.bazel.sdk.index.model.CodeLocationIdentifier;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.path.BazelPathHelper;

/**
 * Crawler that descends into nested directories of source files and adds found files to the index.
 */
public class SourceFileCrawler {
    private static final LogHelper LOG = LogHelper.log(SourceFileCrawler.class);

    protected final CodeIndex index;
    protected final String artifactMarkerFileName;
    protected final Set<String> matchFileSuffixes = new HashSet<>();

    /**
     * In most workspaces, the files in the //tools folder is outside the scope of most normal tool operations. We
     * ignored //tools by default.
     */
    private boolean ignoreTools = true;

    public SourceFileCrawler(CodeIndex index, String artifactMarkerFileName) {
        this.index = index;
        this.artifactMarkerFileName = artifactMarkerFileName;
    }

    public void index(File basePath) {
        indexRecur(basePath, "", null, true);
    }

    /**
     * In most workspaces, the files in the //tools folder is outside the scope of most normal tool operations. We
     * ignored //tools by default.
     */
    public void ignoreTools(boolean ignore) {
        ignoreTools = ignore;
    }

    // INTERNALS

    protected void indexRecur(File path, String relativePathToClosestArtifact,
            CodeLocationDescriptor closestArtifactLocationDescriptor, boolean isRootDir) {
        if (path.isDirectory()) {
            // have we descended into a new artifact? (i.e. directory contains pom.xml for Maven, or BUILD for Bazel)
            String[] artifactMarkerSearch = path.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return artifactMarkerFileName.equals(name);
                }
            });
            if (artifactMarkerSearch.length > 0) {
                String parentId = "";
                if (closestArtifactLocationDescriptor != null) {
                    parentId = closestArtifactLocationDescriptor.id.locationIdentifier + File.separatorChar;
                }
                CodeLocationIdentifier myId = new CodeLocationIdentifier(parentId + relativePathToClosestArtifact);
                closestArtifactLocationDescriptor = new CodeLocationDescriptor(path, myId);

                index.addArtifactLocation(path.getName(), closestArtifactLocationDescriptor);
                relativePathToClosestArtifact = "";
            }

            File[] candidateFiles = path.listFiles();
            for (File candidateFile : candidateFiles) {
                try {
                    if (candidateFile.isDirectory()) {
                        if (isRootDir && candidateFile.getName().startsWith("bazel-")) {
                            // this is a soft link into the output folders, ignore
                            continue;
                        }
                        if (isRootDir && ignoreTools && candidateFile.getName().equals("tools")) {
                            // this is the standard location for bazel build tools, ignore //tools
                            continue;
                        }
                        String childRelative = candidateFile.getName();
                        if (!relativePathToClosestArtifact.isEmpty()) {
                            childRelative = relativePathToClosestArtifact + BazelPathHelper.UNIX_SLASH
                                    + candidateFile.getName();
                            // convert to Windows path if necessary
                            childRelative = BazelPathHelper.osSeps(childRelative);
                        }
                        indexRecur(candidateFile, childRelative, closestArtifactLocationDescriptor, false);
                    } else if (candidateFile.canRead()) {
                        if (isSourceFile(candidateFile)) {
                            foundSourceFile(candidateFile, closestArtifactLocationDescriptor);
                        }
                    }
                } catch (Exception anyE) {
                    LOG.error("Reading java source file [{}] lead to unexpected error", anyE, candidateFile.getPath());
                }
            }
        }
    }

    /**
     * Is this file interesting to the crawler? The default impl matches based on file suffix, but a subclass can
     * override to do something else.
     */
    protected boolean isSourceFile(File candidateFile) {
        String candidateName = candidateFile.getName();
        for (String suffix : matchFileSuffixes) {
            if (candidateName.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Callback that is invoked when a source file is found. Default implementation does nothing, but a subclass may do
     * something with this information.
     */
    protected void foundSourceFile(File sourceFile, CodeLocationDescriptor sourceLocationDescriptor) {}
}
