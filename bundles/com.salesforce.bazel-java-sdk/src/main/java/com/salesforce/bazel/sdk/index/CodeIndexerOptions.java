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
package com.salesforce.bazel.sdk.index;

/**
 * The caller may choose to enable various options that controls the behavior of the indexer.
 * For example, expensive computations may be suppressed by default.
 * <p>
 * It is expected for some indexers to be multithreaded. See the setLock() method for how this is implemented.
 * This is to avoid strange concurrency bugs if the indexer is changing configuration in flight
 * across threads.   
 */
public class CodeIndexerOptions {

    /**
     * Enable if you want the indexer to build the type index. This takes a lot more memory
     * and time to build, so only enable if you will be CodeIndex.typeDictionary.
     */
    protected boolean doComputeTypeDictionary = false; 
    
    /**
     * Since indexers are likely to be multithreaded, we want the options to be immutable once the indexing begins
     * to avoid weird concurrency bugs. The indexer should set to this to true before starting.
     */
    protected boolean isLocked = false;
    
    // CTOR
    
    public CodeIndexerOptions() {}
    
    // SETTERS
    
    public void setDoComputeTypeDictionary(boolean doTypes) {
        if (isLocked) {
            return;
        }
        this.doComputeTypeDictionary = doTypes;
    }
    
    // LOCK
    
    /**
     * Since indexers are likely to be multithreaded, we want the options to be immutable once the indexing begins
     * to avoid weird concurrency bugs. The indexer should set to this to true before starting.
     * <p>
     * This could be done by just making this entire class immutable, but there are possibly cases where multiple collaborators
     * will change the options prior to the start of indexing.
     */
    public void setLock() {
        this.isLocked = true;
    }

    // BEHAVIORS
    
    /**
     * Generally indexing operates on the libary level (jar file, module, etc) but in some cases the caller wants
     * to index at the Type level. For example, track all .class files in a jar file. This is expensive, so not always
     * wanted. But if enabled, this method will return true.
     */
    public boolean doComputeTypeDictionary() {
        return doComputeTypeDictionary;
    }

    /**
     * In some implementations, it may be possible to compute the age of an artifact. It may be expensive to do
     * so however. It will therefore be an optional behavior to enable on the subclass. This method is provided
     * to allow users of an index to know whether an age may be available for an artifact. For example, for a 
     * tool that generates a report on an index, this method can be used to determine whether an age field should be
     * rendered. The age is captured in the CodeLocationDescriptor class.
     */
    public boolean doComputeArtifactAges() {
        return false;
    }

}
