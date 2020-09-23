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
import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.sdk.index.jar.JavaJarCrawler;
import com.salesforce.bazel.sdk.index.jar.JarIdentiferResolver;
import com.salesforce.bazel.sdk.index.model.CodeLocationDescriptor;
import com.salesforce.bazel.sdk.lang.jvm.BazelJvmClasspathResponse;
import com.salesforce.bazel.sdk.lang.jvm.JvmClasspathEntry;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;

/**
 * Creates a classpath containing all downloaded jars (binary and source) and all workspace built jars 
 * in the Bazel workspace. This is typically used to support a search function for IDE type search
 * features to help users discover types that they could use. It could also be used for build tooling 
 * that needs to enumerate all available types in a workspace. 
 */
public class BazelJvmIndexClasspath {
    protected  BazelWorkspace bazelWorkspace;
    protected File bazelBin;
    protected File jarCacheDir;
    private List<File> bazelBinInternals = new ArrayList<>();

    private BazelJvmClasspathResponse cacheResponse;
    
    /**
     * Ctor with workspace.
     */
    public BazelJvmIndexClasspath(BazelWorkspace bazelWorkspace, File jarCacheDir) {
        this.bazelWorkspace = bazelWorkspace;
        this.bazelBin = bazelWorkspace.getBazelBinDirectory();
        
        if (jarCacheDir != null) {
            // maven_install for example stores all downloaded jars outside of the bazel-out directory structure
            this.jarCacheDir = jarCacheDir;
        } else {
            // default is to hunt around in the bazel-bin/external directory
            this.jarCacheDir = new File(bazelBin, "external");
        }
        
        File bazelBinInternal = new File(bazelBin, "projects"); // TODO needs to be configurable/computable, this is just our convention
        if (bazelBinInternal.exists()) {
            bazelBinInternals.add(bazelBinInternal);
        }
    }
    
    /**
     * Ctor with workspace, and the names of the root directories in the Bazel workspace that are interesting to
     * index. This will typically exclude //tools and other non-production code. For example, if your production code
     * base is rooted in //projects and //libs you would pass ['projects', 'libs'] for the second param. 
     */
    public BazelJvmIndexClasspath(BazelWorkspace bazelWorkspace, File jarCacheDir, List<String> internalRootDirectoryNames) {
        this(bazelWorkspace, jarCacheDir);
        
        for (String name : internalRootDirectoryNames) {
            File internalRoot = new File(bazelBin, name);
            if (internalRoot.exists()) {
                bazelBinInternals.add(internalRoot);
            }
        }
    }

    /**
     * Computes the JVM classpath for the associated BazelProject
     */
    public BazelJvmClasspathResponse getClasspathEntries(WorkProgressMonitor progressMonitor) {
        if (cacheResponse != null) {
            return cacheResponse; // TODO expire the cache
        }

        return getClasspathEntriesInternal(this.bazelBinInternals, this.jarCacheDir, progressMonitor);
    }
    
    /* visible for testing */
    BazelJvmClasspathResponse getClasspathEntriesInternal(List<File> bazelBinInternals, File jarCacheDir, 
            WorkProgressMonitor progressMonitor) {
        JvmCodeIndex index = new JvmCodeIndex();
        
        // EXTERNAL (aka maven_install downloaded)
        if (jarCacheDir != null) {
            JarIdentiferResolver jarResolver = new JarIdentiferResolver("/public/"); // TODO this is only maven_install convention
            JavaJarCrawler jarCrawler = new JavaJarCrawler(index, jarResolver);
            jarCrawler.index(jarCacheDir, false);
        }
        
        // INTERNAL (jars produced by Bazel)
        if (bazelBinInternals != null && bazelBinInternals.size() > 0) {
            
            // TODO we should actually add the source files directly instead of indexing the built jars
            
            JarIdentiferResolver jarResolver = new JarIdentiferResolver("/bin/");
            JavaJarCrawler jarCrawler = new JavaJarCrawler(index, jarResolver);
            for (File bazelBinInternal : bazelBinInternals) {
                jarCrawler.index(bazelBinInternal, false);
            }
        }
        
        cacheResponse = convertIndexIntoResponse(index);
        return cacheResponse;
    }
    
    protected BazelJvmClasspathResponse convertIndexIntoResponse(JvmCodeIndex index) {
        BazelJvmClasspathResponse response = new BazelJvmClasspathResponse();
        List<JvmClasspathEntry> entries = new ArrayList<>();

        for (String artifact : index.artifactDictionary.keySet()) {
            JvmCodeIndexEntry entry = index.artifactDictionary.get(artifact);
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
        String pathWithoutJarExtension = path.substring(0, path.length()-4);
        
        // if this is a Bazel output dir jar, we should find it at xyz-src.jar
        File candidateSourcePath = new File(pathWithoutJarExtension+"-src.jar");
        if (!candidateSourcePath.exists()) {
            // external Maven artifacts
            candidateSourcePath = new File(pathWithoutJarExtension+"-sources.jar");
        }
        
        JvmClasspathEntry cpEntry = null;
        if (!candidateSourcePath.exists()) {
            cpEntry = new JvmClasspathEntry(location.locationOnDisk.getPath(), false);
        } else {
            cpEntry = new JvmClasspathEntry(location.locationOnDisk.getPath(), candidateSourcePath.getPath(), false);
        }
        entries.add(cpEntry);
    }
}