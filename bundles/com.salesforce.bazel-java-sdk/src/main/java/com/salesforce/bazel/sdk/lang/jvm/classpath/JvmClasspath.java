package com.salesforce.bazel.sdk.lang.jvm.classpath;

import com.salesforce.bazel.sdk.util.WorkProgressMonitor;

/**
 * Interface for generating the classpath for a Bazel package. An instance of this interface
 * represents a single package.
 */
public interface JvmClasspath {

    /**
     * Computes the JVM classpath for the associated Bazel package
     */
    JvmClasspathData getClasspathEntries(WorkProgressMonitor progressMonitor);

    /**
     * Requests the classpath to clear all cached state such that the next call to getClasspathEntries()
     * will rebuild internal state. 
     */
    public void clean();

}
