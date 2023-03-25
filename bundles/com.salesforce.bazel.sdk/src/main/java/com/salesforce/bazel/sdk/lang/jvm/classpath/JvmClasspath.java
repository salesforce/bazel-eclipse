package com.salesforce.bazel.sdk.lang.jvm.classpath;

import com.salesforce.bazel.sdk.util.WorkProgressMonitor;

/**
 * Interface for generating the classpath for a Bazel package. An instance of this interface represents a single
 * package.
 */
public interface JvmClasspath {

    /**
     * Computes the JVM classpath for the associated Bazel package
     *
     * @throws Exception
     */
    JvmClasspathData getClasspathEntries(WorkProgressMonitor progressMonitor) throws Exception;
}
