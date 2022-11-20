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
 */
package com.salesforce.bazel.sdk.index.jvm;

import com.salesforce.bazel.sdk.index.CodeIndexerOptions;

public class JvmCodeIndexerOptions extends CodeIndexerOptions {
    /**
     * Enable if you would like the Jar indexer to try to determine age by looking at file timestamps of files 
     * inside the jar. This is a reasonably cheap operation so it is enabled by default.
     */
    protected boolean doJvmComputeJarAgeUsingInternalFiles = false;

    /**
     * Some build systems (Bazel!) intentionally suppress this information from being written
     * into the jar file for hermeticity reasons. Normally this is a really old date so it is
     * not mistaken for a real date. Different build systems have different fake dates. Generally
     * you will not be working with anything with a real date older than Jan 1, 2000 so that is what
     * we are using. Anything before this date is consider fake.
     */
    // default value is midnight Jan 1 2000 GMT
    protected long jvmComputeJarAgeUsingInternalFiles_earliestTimestamp = 946684800000L; 
    
    /**
     * If jvmComputeJarAgeUsingInternalFiles == true, how many times should the indexer try to find
     * a file with a valid timestamp before giving up for that jar?
     */
    protected int jvmComputeJarAgeUsingInternalFiles_tries = 5;
    
    /**
     * Maven repositories (Nexus, Artifactory) sometimes have jar age information. Enable that by setting
     * it to true. This is very expensive to use because it makes a remote call.
     */
    protected boolean doJvmComputeJarAgeUsingRemoteMavenRepo = false;
    

    // CTORS
    
    public JvmCodeIndexerOptions() {
    }
    
    /**
     * Factory pattern to create the options used for Global Type Search use cases. This is a common use case
     * where the user has imported only some of the Bazel packages into an IDE, but they want type search
     * to cover all jars in the Bazel workspace.
     */
    public static JvmCodeIndexerOptions buildJvmGlobalSearchOptions() {
        
        // currently we just use the defaults, but this would be the central place to change that
        
        return new JvmCodeIndexerOptions();
    }

    
    // SETTERS
    
    public void setDoComputeJarAgeUsingInternalFiles(boolean doJvmComputeJarAgeUsingInternalFiles) {
        if (isLocked) {
            return;
        }
        this.doJvmComputeJarAgeUsingInternalFiles = doJvmComputeJarAgeUsingInternalFiles;
    }

    public void setEarliestTimestampForComputeJarAgeUsingInternalFiles(
            long jvmComputeJarAgeUsingInternalFiles_earliestTimestamp) {
        if (isLocked) {
            return;
        }
        this.jvmComputeJarAgeUsingInternalFiles_earliestTimestamp = jvmComputeJarAgeUsingInternalFiles_earliestTimestamp;
    }

    public void setTriesForComputeJarAgeUsingInternalFiles(int jvmComputeJarAgeUsingInternalFiles_tries) {
        if (isLocked) {
            return;
        }
        this.jvmComputeJarAgeUsingInternalFiles_tries = jvmComputeJarAgeUsingInternalFiles_tries;
    }

    public void setDoComputeJarAgeUsingRemoteMavenRepo(boolean doJvmComputeJarAgeUsingRemoteMavenRepo) {
        if (isLocked) {
            return;
        }
        this.doJvmComputeJarAgeUsingRemoteMavenRepo = doJvmComputeJarAgeUsingRemoteMavenRepo;
    }

    
    // GETTERS

    public boolean doComputeJarAgeUsingInternalFiles() {
        return doJvmComputeJarAgeUsingInternalFiles;
    }

    public long getEarliestTimestampForComputeJarAgeUsingInternalFiles() {
        return jvmComputeJarAgeUsingInternalFiles_earliestTimestamp;
    }

    public int getTriesForComputeJarAgeUsingInternalFiles() {
        return jvmComputeJarAgeUsingInternalFiles_tries;
    }


    public boolean doComputeJarAgeUsingRemoteMavenRepo() {
        return doJvmComputeJarAgeUsingRemoteMavenRepo;
    }

    /**
     * Is any form of age computation enabled for JVM Jars?
     */
    @Override
    public boolean doComputeArtifactAges() {
        return doJvmComputeJarAgeUsingInternalFiles || doJvmComputeJarAgeUsingRemoteMavenRepo;
    }
}
