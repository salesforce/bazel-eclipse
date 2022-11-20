/**
 * Copyright (c) 2022, Salesforce.com, Inc. All rights reserved.
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
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.salesforce.bazel.sdk.index.jvm;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.sdk.index.CodeIndexer;
import com.salesforce.bazel.sdk.index.jvm.jar.JarIdentiferResolver;
import com.salesforce.bazel.sdk.index.jvm.jar.JavaJarCrawler;
import com.salesforce.bazel.sdk.lang.jvm.external.BazelExternalJarRuleManager;
import com.salesforce.bazel.sdk.lang.jvm.external.BazelExternalJarRuleType;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;

/**
 * Indexes the code and dependencies in a Bazel workspace. It uses a combination of 
 * underlying crawlers to collect information.
 */
public class JvmCodeIndexer extends CodeIndexer {
    private static final LogHelper LOG = LogHelper.log(JvmCodeIndexer.class);
    
    /**
     * Builds an index for an entire workspace, which can be a very expensive operation.
     */
    public synchronized JvmCodeIndex buildWorkspaceIndex(BazelWorkspace bazelWorkspace,
            BazelExternalJarRuleManager externalJarRuleManager, JvmCodeIndexerOptions indexerOptions,
            List<File> additionalJarLocations, WorkProgressMonitor progressMonitor) {

        // TODO we should not return a cache index if the indexOptions don't match
        JvmCodeIndex index = JvmCodeIndex.getWorkspaceIndex(bazelWorkspace);
        if (index != null) {
            return index;
        }
        
        LOG.info("Building the type index for workspace {}, this may take some time...", bazelWorkspace.getName());
        List<File> locations = new ArrayList<>();
        index = new JvmCodeIndex(indexerOptions);
        
        // lock the options, as we don't want the caller to change them while we are indexing
        indexerOptions.setLock();

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

        JvmCodeIndex.addWorkspaceIndex(bazelWorkspace, index);

        LOG.info("Finished building the type index for workspace {}", bazelWorkspace.getName());
        return index;

    }

    void processLocation(BazelWorkspace bazelWorkspace, BazelExternalJarRuleManager externalJarRuleManager,
            JvmCodeIndex index, File location, WorkProgressMonitor progressMonitor) {
        if ((location != null) && location.exists()) {
            JarIdentiferResolver jarResolver = new JarIdentiferResolver();
            JavaJarCrawler jarCrawler = new JavaJarCrawler(bazelWorkspace, index, jarResolver, externalJarRuleManager);
            jarCrawler.index(location);
        }
    }

    protected void addInternalLocations(JvmCodeIndex index, List<File> locations) {
        // TODO INTERNAL (jars produced by Bazel)
        // this needs to be implemented to make the Dynamic classpath work
    }
}
