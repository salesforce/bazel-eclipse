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
package com.salesforce.bazel.sdk.index.index;

import java.util.Map;
import java.util.TreeMap;

import com.salesforce.bazel.sdk.index.model.CodeLocationDescriptor;

public class CodeIndex {
    // map artifact name to entry(s)
    public Map<String, CodeIndexEntry> artifactDictionary = new TreeMap<>();
    // map class name to entry(s)
    public Map<String, CodeIndexEntry> classDictionary = new TreeMap<>();

    public void addArtifactLocation(String artifact, CodeLocationDescriptor location) {
        CodeIndexEntry indexEntry = this.artifactDictionary.get(artifact);
        if (indexEntry == null) {
            indexEntry = new CodeIndexEntry();
        }
        indexEntry.addLocation(location);
        this.artifactDictionary.put(artifact, indexEntry);
        System.out.println("ADD: "+location.locationOnDisk.getPath());
    }

    public void addClassnameLocation(String classname, CodeLocationDescriptor location) {
        CodeIndexEntry indexEntry = this.classDictionary.get(classname);
        if (indexEntry == null) {
            indexEntry = new CodeIndexEntry();
        }
        indexEntry.addLocation(location);
        this.classDictionary.put(classname, indexEntry);
    }

    public void printIndex() {
        println("");
        println("ARTIFACT INDEX ("+artifactDictionary.size()+" entries)");
        println("----------------------------------------");
        for (String artifact : artifactDictionary.keySet()) {
            printArtifact(artifact, artifactDictionary.get(artifact));
        }
        println("");
        println("CLASSNAME INDEX ("+classDictionary.size()+" entries)");
        println("----------------------------------------");
        for (String classname : classDictionary.keySet()) {
            printArtifact(classname, classDictionary.get(classname));
        }
        println("");
    }

    private void printArtifact(String artifact, CodeIndexEntry entry) {
        println("  "+artifact);
        if (entry.singleLocation != null) {
            println("    "+entry.singleLocation.id.locationIdentifier);
        } else {
            for (CodeLocationDescriptor loc : entry.multipleLocations) {
                println("    "+loc.id.locationIdentifier);
            }
        }
    }

    private void println(String line) {
        System.out.println(line);
    }
}
