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

import java.util.Map;
import java.util.TreeMap;

import com.salesforce.bazel.sdk.index.model.CodeLocationDescriptor;

/**
 * An index of JVM types. This is the output of an indexer that knows how to traverse the file system looking for JVM
 * types. This is useful for tools that need to have a full list of available JVM types. For example, a Bazel IDE will
 * want to be able to list all types imported by the workspace.
 * <p>
 * There are two parts to the index: the artifactDictionary and the classDictionary.
 * <p>
 * The artifactDictionary maps the Maven style artifactId to the one or more jar files found that contains that
 * artifactId. If your directories contains multiple versions of the same artifactId, this will be a list of artifacts.
 * <p>
 * The classDictionary maps each found classname to the discovered location in jar files or raw source files.
 * <p>
 * This is intentionally a lighter indexing system than provided by the MavenIndexer project, which generates full
 * Lucene indexes of code. We found the performance of that indexing solution to be too slow for our needs.
 */
public class JvmCodeIndex {
    // key: artifact name  value: jar locations
    Map<String, JvmCodeIndexEntry> artifactDictionary = new TreeMap<>();
    // key: classname  value: locations where that classname is found
    Map<String, JvmCodeIndexEntry> classDictionary = new TreeMap<>();

    public void addArtifactLocation(String artifact, CodeLocationDescriptor location) {
        JvmCodeIndexEntry indexEntry = this.artifactDictionary.get(artifact);
        if (indexEntry == null) {
            indexEntry = new JvmCodeIndexEntry();
        }
        indexEntry.addLocation(location);
        this.artifactDictionary.put(artifact, indexEntry);
    }

    public void addClassnameLocation(String classname, CodeLocationDescriptor location) {
        JvmCodeIndexEntry indexEntry = this.classDictionary.get(classname);
        if (indexEntry == null) {
            indexEntry = new JvmCodeIndexEntry();
        }
        indexEntry.addLocation(location);
        this.classDictionary.put(classname, indexEntry);
    }

    public void printIndex() {
        println("");
        println("ARTIFACT INDEX (" + artifactDictionary.size() + " entries)");
        println("----------------------------------------");
        for (String artifact : artifactDictionary.keySet()) {
            printArtifact(artifact, artifactDictionary.get(artifact));
        }
        println("");
        println("CLASSNAME INDEX (" + classDictionary.size() + " entries)");
        println("----------------------------------------");
        for (String classname : classDictionary.keySet()) {
            printArtifact(classname, classDictionary.get(classname));
        }
        println("");
    }

    private void printArtifact(String artifact, JvmCodeIndexEntry entry) {
        println("  " + artifact);
        if (entry.singleLocation != null) {
            println("    " + entry.singleLocation.id.locationIdentifier);
        } else {
            for (CodeLocationDescriptor loc : entry.multipleLocations) {
                println("    " + loc.id.locationIdentifier);
            }
        }
    }

    private void println(String line) {
        System.out.println(line);
    }
}
