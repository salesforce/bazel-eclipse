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
package com.salesforce.bazel.sdk.index.jvm;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.sdk.index.CodeIndexEntry;
import com.salesforce.bazel.sdk.index.model.CodeLocationDescriptor;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathData;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathEntry;
import com.salesforce.bazel.sdk.lang.jvm.classpath.impl.JvmInMemoryClasspath;
import com.salesforce.bazel.sdk.lang.jvm.external.BazelExternalJarRuleManager;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

/**
 * Creates a classpath containing all downloaded jars (binary and source) and all workspace built jars in the Bazel
 * workspace. This is typically used to support a search function for IDE type search features to help users discover
 * types that they could use. It could also be used for build tooling that needs to enumerate all available types in a
 * workspace.
 */
public class BazelJvmIndexClasspath extends JvmInMemoryClasspath {
    /**
     * Associated workspace.
     */
    protected BazelWorkspace bazelWorkspace;

    // collaborators
    protected OperatingEnvironmentDetectionStrategy os;
    protected BazelExternalJarRuleManager externalJarRuleManager;

    /**
     * User provided locations to search.
     */
    protected List<File> additionalJarLocations;

    // computed data
    protected JvmCodeIndex index;
    protected JvmClasspathData cachedClasspath;

    /**
     * Ctor with workspace.
     */
    public BazelJvmIndexClasspath(BazelWorkspace bazelWorkspace, OperatingEnvironmentDetectionStrategy os,
            BazelExternalJarRuleManager externalJarRuleManager, List<File> additionalJarLocations) {
        super("Global Index Classpath", -1);
        
        this.bazelWorkspace = bazelWorkspace;
        this.os = os;
        this.externalJarRuleManager = externalJarRuleManager;
        this.additionalJarLocations = additionalJarLocations;
    }

    // API
    
    /**
     * Computes the JVM classpath for the associated Bazel workspace. The first invocation is expected to take a long
     * time, but subsequent invocations will read from cache.
     */
    public JvmClasspathData computeClasspath(WorkProgressMonitor progressMonitor) {
        synchronized (this) {
            if (cachedClasspath != null) {
                return cachedClasspath;
            }
            
            // make sure the index is loaded
            getIndex(progressMonitor);
            
            // build the Global classpath
            cachedClasspath = convertIndexIntoResponse(index);
        }
        
        return cachedClasspath;
    }

    /**
     * Gets the computed index.
     */
    public synchronized JvmCodeIndex getIndex(WorkProgressMonitor progressMonitor) {
        if (index != null) {
            return index;
        }
        JvmCodeIndexer indexer = new JvmCodeIndexer();
        JvmCodeIndexerOptions indexerOptions = JvmCodeIndexerOptions.buildJvmGlobalSearchOptions();

        index = indexer.buildWorkspaceIndex(bazelWorkspace, externalJarRuleManager, indexerOptions, additionalJarLocations,
            progressMonitor);
        return index;
    }

    /**
     * Clears the cache, which will make the next invocation of getClasspathEntries() expensive.
     */
    @Override
    public void clean() {
        synchronized (this) {
            index = null;
            cachedClasspath = null;
            super.clean();
        }
    }
    
    // INTERNAL

    protected JvmClasspathData convertIndexIntoResponse(JvmCodeIndex index) {
        JvmClasspathData response = new JvmClasspathData();
        List<JvmClasspathEntry> entries = new ArrayList<>();

        for (String artifact : index.artifactDictionary.keySet()) {
            CodeIndexEntry entry = index.artifactDictionary.get(artifact);
            if (entry.multipleLocations != null) {
                // this is the case where the workspace has multiple versions of the same artifact (e.g. guava)
                // add each version, as there could be classes in one version but not another
                for (CodeLocationDescriptor location : entry.multipleLocations) {
                    addJarEntry(entries, location);
                }
            } else if (entry.singleLocation != null) {
                addJarEntry(entries, entry.singleLocation);
            }
        }

        response.jvmClasspathEntries = entries.toArray(new JvmClasspathEntry[] {});

        return response;
    }

    protected void addJarEntry(List<JvmClasspathEntry> entries, CodeLocationDescriptor location) {
        // try to find the source jar
        String path = location.locationOnDisk.getPath();
        String pathWithoutJarExtension = path.substring(0, path.length() - 4);

        // if this is a Bazel output dir jar, we should find it at xyz-src.jar
        File candidateSourcePath = new File(pathWithoutJarExtension + "-src.jar");
        if (!candidateSourcePath.exists()) {
            // external Maven artifacts
            candidateSourcePath = new File(pathWithoutJarExtension + "-sources.jar");
        }

        JvmClasspathEntry cpEntry = null;
        if (!candidateSourcePath.exists()) {
            cpEntry = new JvmClasspathEntry(location.locationOnDisk.getPath(), false, false);
        } else {
            cpEntry = new JvmClasspathEntry(location.locationOnDisk.getPath(), candidateSourcePath.getPath(), false, false);
        }
        entries.add(cpEntry);
    }
}