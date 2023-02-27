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
package com.salesforce.bazel.sdk.lang.jvm.classpath.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspath;
import com.salesforce.bazel.sdk.lang.jvm.classpath.JvmClasspathData;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;

/**
 * Basic implementation of JvmClasspath that uses a stored object for the classpath state. It does not have
 * innate ability to compute classpath data; the caller that creates it must provide the classpath data at
 * initialization.
 * <p>
 * It supports caching. If the cache timeout expires, the classpath data will be erased and this implementation
 * returns null;
 * <p>
 * As a side gig, it is also expected to be the base class of most computational JvmClasspath implementations.
 */
public class JvmInMemoryClasspath implements JvmClasspath {

    private static Logger LOG = LoggerFactory.getLogger(JvmInMemoryClasspath.class);

    /**
     * A logical name for the classpath instance, like the Bazel package name or project name.
     */
    protected String classpathName;

    protected JvmClasspathData cachedClasspath;
    protected long cacheTimeoutMillis = 300000;
    protected long cachePutTimeMillis = 0;

    /**
     * Ctor to be used when the classpath data is already computed.
     *
     * @param classpathName
     *            a logical name for the classpath instance, like the Bazel package name or project name.
     * @param cacheTimeoutMillis
     *            the timeout in milliseconds; -1 never expires
     */
    public JvmInMemoryClasspath(String classpathName, JvmClasspathData classpath) {
        this.classpathName = classpathName;
        this.cacheTimeoutMillis = -1;
        this.cachePutTimeMillis = System.currentTimeMillis();
        this.cachedClasspath = classpath;
    }

    /**
     * Ctor to be called by subclasses.
     *
     * @param classpathName
     *            a logical name for the classpath instance, like the Bazel package name or project name.
     * @param cacheTimeoutMillis
     *            the timeout in milliseconds; -1 never expires
     */
    protected JvmInMemoryClasspath(String classpathName, long cacheTimeoutMillis) {
        this.classpathName = classpathName;
        this.cacheTimeoutMillis = cacheTimeoutMillis;
    }

    // API

    /**
     * Provides the JVM classpath for the associated BazelProject
     * @throws Exception
     */
    @Override
    public JvmClasspathData getClasspathEntries(WorkProgressMonitor progressMonitor) throws Exception {
        if (cachedClasspath != null && cacheTimeoutMillis == -1) {
            return cachedClasspath;
        }

        // where the magic happens
        // in most cases, this class will be subclassed and the computeClasspath method will be overridden
        // with code that can compute the classpath using something tangible (build metadata, source files, etc).
        cachedClasspath = computeClasspath(progressMonitor);

        return cachedClasspath;
    }

    /**
     * Updates the cached classpath
     * @param newClasspath
     */
    public void updateClasspath(JvmClasspathData newClasspath) {
        cachedClasspath = newClasspath;
        cachePutTimeMillis = System.currentTimeMillis();
    }

    @Override
    public void clean() {
        cachedClasspath = null;
        cachePutTimeMillis = 0;
    }

    // SUBCLASS API

    /**
     * If the classpath is not cached, this method will recompute it.
     * <p>
     * For subclasses, this is the main method to implement.
     * @throws Exception
     */
    protected JvmClasspathData computeClasspath(WorkProgressMonitor progressMonitor) throws Exception {
        // if this class is not subclassed, and we get here, the captive JvmClasspathData has expired
        // and we just return null. this isn't a good situation. so if you are using this class without
        // subclassing, be sure to set the timeout to -1
        return null;
    }

    // INTERNAL

    protected JvmClasspathData getCachedEntries() {
        if (cachedClasspath != null) {
            long now = System.currentTimeMillis();
            if ((now - cachePutTimeMillis) <= cacheTimeoutMillis) {
                LOG.debug("  Using cached classpath for project " + classpathName);
                return cachedClasspath;
            }
            LOG.info("Evicted classpath from cache for project " + classpathName);
            cachedClasspath = null;
        }
        return null;
    }

}
