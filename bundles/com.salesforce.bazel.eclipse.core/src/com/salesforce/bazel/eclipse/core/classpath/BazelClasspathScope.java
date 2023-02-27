package com.salesforce.bazel.eclipse.core.classpath;

/**
 * The different possible classpath scopes
 */
public enum BazelClasspathScope {

    /**
     * Maven dependency resolution scope constant indicating test scope.
     */
    TEST_CLASSPATH,

    /**
     * Maven dependency resolution scope constant indicating runtime scope.
     */
    RUNTIME_CLASSPATH;

    public static final BazelClasspathScope DEFAULT_CLASSPATH = BazelClasspathScope.TEST_CLASSPATH;
}
