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
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import com.salesforce.bazel.sdk.index.CodeIndex;
import com.salesforce.bazel.sdk.index.CodeIndexEntry;
import com.salesforce.bazel.sdk.index.jvm.jar.JarIdentiferResolver;
import com.salesforce.bazel.sdk.index.jvm.jar.JavaJarCrawler;
import com.salesforce.bazel.sdk.lang.jvm.external.BazelExternalJarRuleManager;
import com.salesforce.bazel.sdk.lang.jvm.external.BazelExternalJarRuleType;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;

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
    private static final LogHelper LOG = LogHelper.log(MethodHandles.lookup().lookupClass());

    /**
     * Global collection of indices for each workspace
     */
    protected static Map<String, JvmCodeIndex> workspaceIndices = new ConcurrentHashMap<>();

    // See superclass for the collections
    //public Map<String, CodeIndexEntry> artifactDictionary = new TreeMap<>();
    //public Map<String, CodeIndexEntry> fileDictionary = new TreeMap<>();
    //public Map<String, CodeIndexEntry> typeDictionary = new TreeMap<>();

    public static JvmCodeIndex getWorkspaceIndex(BazelWorkspace bazelWorkspace) {
        return workspaceIndices.get(bazelWorkspace.getName());
    }

    public static JvmCodeIndex clearWorkspaceIndex(BazelWorkspace bazelWorkspace) {
        return workspaceIndices.remove(bazelWorkspace.getName());
    }

    /**
     * Builds an index for an entire workspace, which can be a very expensive operation.
     */
    public static synchronized JvmCodeIndex buildWorkspaceIndex(BazelWorkspace bazelWorkspace,
            BazelExternalJarRuleManager externalJarRuleManager, List<File> additionalJarLocations,
            WorkProgressMonitor progressMonitor) {
        JvmCodeIndex index = getWorkspaceIndex(bazelWorkspace);
        if (index != null) {
            return index;
        }
        LOG.info("Building the type index for workspace {}, this may take some time...", bazelWorkspace.getName());

        index = new JvmCodeIndex();
        List<File> locations = new ArrayList<>();

        // for each jar downloading rule type in the workspace, add the appropriate local directories of the downloaded jars
        List<BazelExternalJarRuleType> ruleTypes = externalJarRuleManager.findInUseExternalJarRuleTypes(bazelWorkspace);
        for (BazelExternalJarRuleType ruleType : ruleTypes) {
            List<File> ruleSpecificLocations = ruleType.getDownloadedJarLocations(bazelWorkspace);
            locations.addAll(ruleSpecificLocations);
        }

        // add internal location (jars built by the bazel workspace
        addInternalLocations(index, locations);

        // add the additional directories the user wants to search
        if (additionalJarLocations != null) {
            locations.addAll(additionalJarLocations);
        }

        // now build the index
        for (File location : locations) {
            processLocation(bazelWorkspace, externalJarRuleManager, index, location, progressMonitor);
        }

        workspaceIndices.put(bazelWorkspace.getName(), index);

        LOG.info("Finished building the type index for workspace {}", bazelWorkspace.getName());
        return index;

    }

    static void processLocation(BazelWorkspace bazelWorkspace, BazelExternalJarRuleManager externalJarRuleManager,
            JvmCodeIndex index, File location, WorkProgressMonitor progressMonitor) {
        if ((location != null) && location.exists()) {
            JarIdentiferResolver jarResolver = new JarIdentiferResolver();
            JavaJarCrawler jarCrawler = new JavaJarCrawler(bazelWorkspace, index, jarResolver, externalJarRuleManager);
            jarCrawler.index(location, false);
        }
    }

    protected static void addInternalLocations(JvmCodeIndex index, List<File> locations) {
        // TODO INTERNAL (jars produced by Bazel)
        // this needs to be implemented to make the Dynamic classpath work
    }

}
