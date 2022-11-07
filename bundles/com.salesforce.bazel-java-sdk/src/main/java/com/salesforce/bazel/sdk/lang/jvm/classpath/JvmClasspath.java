package com.salesforce.bazel.sdk.lang.jvm.classpath;

/**
 * Interface for generating the classpath for a Bazel package.
 */
public interface JvmClasspath {

    /**
     * Computes the JVM classpath for the associated Bazel package
     */
    BazelJvmClasspathResponse getClasspathEntries();

}
