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

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.TreeMap;

import com.salesforce.bazel.sdk.index.model.CodeLocationDescriptor;
import com.salesforce.bazel.sdk.logging.LogHelper;

/**
 * An index of types. This is the output of an indexer that knows how to traverse the file system looking for types for
 * a specific language (e.g. Java). This is useful for tools that need to have a full list of available types. For
 * example, a Bazel IDE will want to be able to list all types imported by the workspace.
 * <p>
 * There are three parts to the index: the artifactDictionary, fileDictionary and the typeDictionary.
 * <p>
 * The artifactDictionary maps the artifactId (e.g. junit, hamcrest-core, slf4j-api) to the one or more archives found
 * that contains that artifactId. If your directories contains multiple versions of the same artifactId, this will be a
 * list of artifacts.
 * <p>
 * The fileDictionary maps the filename (e.g. junit-4.12.jar) to the one or more locations where that filename was
 * found.
 * <p>
 * The typeDictionary maps each found type name (e.g. the fully qualified Java classname) to the discovered location in
 * archive files or raw source files.
 */
public class CodeIndex {
    private static final LogHelper LOG = LogHelper.log(MethodHandles.lookup().lookupClass());

    // map artifact name (e.g. junit, hamcrest-core, slf4j-api) to entry(s) 
    public Map<String, CodeIndexEntry> artifactDictionary = new TreeMap<>();
    // map artifact file (e.g. junit-4.12.jar) to entry(s) 
    public Map<String, CodeIndexEntry> fileDictionary = new TreeMap<>();
    // map class name to entry(s)
    public Map<String, CodeIndexEntry> typeDictionary = new TreeMap<>();

    public void addArtifactLocation(String artifact, CodeLocationDescriptor location) {
        CodeIndexEntry indexEntry = artifactDictionary.get(artifact);
        if (indexEntry == null) {
            indexEntry = new CodeIndexEntry();
        }
        indexEntry.addLocation(location);
        artifactDictionary.put(artifact, indexEntry);
        LOG.debug("add artifact ({}): {}", artifact, location.locationOnDisk.getPath());
    }

    public void addFileLocation(String filename, CodeLocationDescriptor location) {
        CodeIndexEntry indexEntry = fileDictionary.get(filename);
        if (indexEntry == null) {
            indexEntry = new CodeIndexEntry();
        }
        indexEntry.addLocation(location);
        fileDictionary.put(filename, indexEntry);
        LOG.debug("add file ({}): {}", filename, location.locationOnDisk.getPath());
    }

    public void addTypeLocation(String typeName, CodeLocationDescriptor location) {
        CodeIndexEntry indexEntry = typeDictionary.get(typeName);
        if (indexEntry == null) {
            indexEntry = new CodeIndexEntry();
        }
        indexEntry.addLocation(location);
        typeDictionary.put(typeName, indexEntry);
        LOG.debug("add type ({}): {}", typeName, location.locationOnDisk.getPath());
    }

    public void printIndex() {
        println("");
        println("ARTIFACT INDEX (" + artifactDictionary.size() + " entries)");
        println("----------------------------------------");
        for (String artifact : artifactDictionary.keySet()) {
            printArtifact(artifact, artifactDictionary.get(artifact));
        }
        println("");
        println("FILE INDEX (" + fileDictionary.size() + " entries)");
        println("----------------------------------------");
        for (String filename : fileDictionary.keySet()) {
            printArtifact(filename, fileDictionary.get(filename));
        }
        println("");
        println("TYPE INDEX (" + typeDictionary.size() + " entries)");
        println("----------------------------------------");
        for (String classname : typeDictionary.keySet()) {
            printArtifact(classname, typeDictionary.get(classname));
        }
        println("");
    }

    private void printArtifact(String artifact, CodeIndexEntry entry) {
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
