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

import com.salesforce.bazel.sdk.index.jar.JarIdentiferResolver;
import com.salesforce.bazel.sdk.index.jar.JavaJarCrawler;
import com.salesforce.bazel.sdk.index.model.CodeLocationDescriptor;
import com.salesforce.bazel.sdk.lang.jvm.BazelJvmClasspathResponse;
import com.salesforce.bazel.sdk.lang.jvm.JvmClasspathEntry;
import com.salesforce.bazel.sdk.lang.jvm.external.BazelExternalJarRuleManager;
import com.salesforce.bazel.sdk.lang.jvm.external.BazelExternalJarRuleType;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

/**
 * Creates a classpath containing all downloaded jars (binary and source) and all workspace built jars 
 * in the Bazel workspace. This is typically used to support a search function for IDE type search
 * features to help users discover types that they could use. It could also be used for build tooling 
 * that needs to enumerate all available types in a workspace. 
 */
public class BazelJvmIndexClasspath {
    /**
     * Associated workspace.
     */
    protected  BazelWorkspace bazelWorkspace;
    
    // collaborators
    protected OperatingEnvironmentDetectionStrategy os;
    protected BazelExternalJarRuleManager externalJarRuleManager;
    
    /**
     * User provided locations to search.
     */
    protected List<File> additionalJarLocations;

    protected BazelJvmClasspathResponse cacheResponse;
    
    /**
     * Ctor with workspace.
     */
    public BazelJvmIndexClasspath(BazelWorkspace bazelWorkspace, OperatingEnvironmentDetectionStrategy os,
            BazelExternalJarRuleManager externalJarRuleManager, List<File> additionalJarLocations) {
        this.bazelWorkspace = bazelWorkspace;
        this.os = os;
        this.externalJarRuleManager = externalJarRuleManager;
        this.additionalJarLocations = additionalJarLocations;
    }
    

    /**
     * Computes the JVM classpath for the associated Bazel workspace
     */
    public BazelJvmClasspathResponse getClasspathEntries(WorkProgressMonitor progressMonitor) {
        if (cacheResponse != null) {
            return cacheResponse;
        }
        JvmCodeIndex index = new JvmCodeIndex();
        List<File> locations = new ArrayList<>();
        
        // for each jar downloading rule type in the workspace, add the appropriate local directories of the downloaded jars
        List<BazelExternalJarRuleType> ruleTypes = externalJarRuleManager.findInUseExternalJarRuleTypes(this.bazelWorkspace);
        for (BazelExternalJarRuleType ruleType : ruleTypes) {
            System.out.println("");
            List<File> ruleSpecificLocations = ruleType.getDownloadedJarLocations(bazelWorkspace);
            locations.addAll(ruleSpecificLocations);
        }
        
        // add internal location (jars built by the bazel workspace
        addInternalLocations(locations);
        
        // add the additional directories the user wants to search
        if (additionalJarLocations != null) {
            locations.addAll(additionalJarLocations);
        }
        
        // now do the searching
        for (File location : locations) {
            getClasspathEntriesInternal(location, index, progressMonitor);
        }
        
        cacheResponse = convertIndexIntoResponse(index);
        return cacheResponse;
    }
    
    public void clearCache() {
        cacheResponse = null;
    }
    
    /* visible for testing */
    void getClasspathEntriesInternal(File location, JvmCodeIndex index, WorkProgressMonitor progressMonitor) {        
        if (location != null && location.exists()) {
            JarIdentiferResolver jarResolver = new JarIdentiferResolver();
            JavaJarCrawler jarCrawler = new JavaJarCrawler(index, jarResolver);
            jarCrawler.index(location, false);
        }
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
    
    protected void addInternalLocations(List<File> locations) {
        // TODO INTERNAL (jars produced by Bazel)
    }
}