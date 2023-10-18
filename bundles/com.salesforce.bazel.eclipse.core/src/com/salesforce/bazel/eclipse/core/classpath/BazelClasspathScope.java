package com.salesforce.bazel.eclipse.core.classpath;

/**
 * The different possible classpath scopes
 */
public enum BazelClasspathScope {

    /**
     * Dependency resolution scope constant indicating compile scope.
     */
    COMPILE_CLASSPATH,

    /**
     * Maven dependency resolution scope constant indicating runtime scope.
     */
    RUNTIME_CLASSPATH;

    public static final BazelClasspathScope DEFAULT_CLASSPATH = BazelClasspathScope.RUNTIME_CLASSPATH;
}
