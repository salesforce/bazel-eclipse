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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.salesforce.bazel.sdk.index.CodeIndex;
import com.salesforce.bazel.sdk.model.BazelWorkspace;

/**
 * An index of JVM types. This is the output of an indexer that knows how to traverse the file system looking for JVM
 * types. This is useful for tools that need to have a full list of available JVM types. For example, a Bazel IDE will
 * want to be able to list all types imported by the workspace.
 * <p>
 * There are three parts to the index: the artifactDictionary, fileDictionary and the typeDictionary.
 * <p>
 * The artifactDictionary maps the Maven style artifactId (e.g. junit, hamcrest-core, slf4j-api) to the one or more jar
 * files found that contains that artifactId. If your directories contains multiple versions of the same artifactId,
 * this will be a list of artifacts.
 * <p>
 * The fileDictionary maps the filename (e.g. junit-4.12.jar) to the one or more locations where that filename was
 * found.
 * <p>
 * The typeDictionary maps each found classname to the discovered location in jar files or raw source files.
 * <p>
 * This is intentionally a lighter indexing system than provided by the MavenIndexer project, which generates full
 * Lucene indexes of code. We found the performance of that indexing solution to be too slow for our needs.
 */
public class JvmCodeIndex extends CodeIndex {
    // See superclass for the collections
    //public Map<String, CodeIndexEntry> artifactDictionary = new TreeMap<>();
    //public Map<String, CodeIndexEntry> fileDictionary = new TreeMap<>();
    //public Map<String, CodeIndexEntry> typeDictionary = new TreeMap<>();

    /**
     * Global cache of indices, keyed by workspace name. (BazelWorkspace.name)
     */
    protected static Map<String, JvmCodeIndex> workspaceIndices = new ConcurrentHashMap<>();

    
    public JvmCodeIndex() {}
    
    public JvmCodeIndex(JvmCodeIndexerOptions indexerOptions) {
        this.indexOptions = indexerOptions;
    }
    
    // STATIC CACHE
    
    /**
     * Returns an existing index from cache if one exists. This does NOT compute a new index if one isn't available.
     */
    public static JvmCodeIndex getWorkspaceIndex(BazelWorkspace bazelWorkspace) {
        return workspaceIndices.get(bazelWorkspace.getName());
    }

    /**
     * Adds an index to the cache.
     */
    public static void addWorkspaceIndex(BazelWorkspace bazelWorkspace, JvmCodeIndex index) {
        workspaceIndices.put(bazelWorkspace.getName(), index);
    }

    /**
     * Clears an index from the cache.
     */
    public static JvmCodeIndex clearWorkspaceIndex(BazelWorkspace bazelWorkspace) {
        return workspaceIndices.remove(bazelWorkspace.getName());
    }

    // GETTERS
    
    public JvmCodeIndexerOptions getJvmOptions() {
        return (JvmCodeIndexerOptions)this.indexOptions;
    }
    
}
