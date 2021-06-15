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

import com.salesforce.bazel.sdk.index.CodeIndex;

/**
 * An index of JVM types. This is the output of an indexer that knows how to traverse the file system looking for JVM
 * types. This is useful for tools that need to have a full list of available JVM types. For example, a Bazel IDE will
 * want to be able to list all types imported by the workspace.
 * <p>
 * There are two parts to the index: the artifactDictionary and the typeDictionary.
 * <p>
 * The artifactDictionary maps the Maven style artifactId to the one or more jar files found that contains that
 * artifactId. If your directories contains multiple versions of the same artifactId, this will be a list of artifacts.
 * <p>
 * The typeDictionary maps each found classname to the discovered location in jar files or raw source files.
 * <p>
 * This is intentionally a lighter indexing system than provided by the MavenIndexer project, which generates full
 * Lucene indexes of code. We found the performance of that indexing solution to be too slow for our needs.
 */
public class JvmCodeIndex extends CodeIndex {
	
	// the superclass is sufficient to represent a JVM index. This subclass remains to just host the javadoc that
	// explains the JVM specific details of this usage. 
	
}
