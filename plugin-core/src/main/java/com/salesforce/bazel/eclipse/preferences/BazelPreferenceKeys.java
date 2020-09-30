package com.salesforce.bazel.eclipse.preferences;

/**
 * Names of the preference keys. Look for usages of these variables to see how BEF uses prefs.
 */
public class BazelPreferenceKeys {
    
    // USER FACING PREFS (visible on Prefs page)
    
    // path the the bazel executable
    public static final String BAZEL_PATH_PREF_NAME = "BAZEL_PATH"; 
    
    // Global classpath search allows BEF to index all jars associated with a Bazel Workspace which makes them
    // available for Open Type searches. These prefs enabled it, and tell BEF where to look for the local cache
    // of downloaded jars.
    public static final String GLOBALCLASSPATH_SEARCH_PREF_NAME = "GLOBALCLASSPATH_SEARCH_ENABLED";
    public static final String EXTERNAL_JAR_CACHE_PATH_PREF_NAME = "EXTERNAL_JAR_CACHE_PATH";

    
    // BEF DEVELOPER PREFS (for efficient repetitive testing of BEF)
    
    // The import wizard will be populated by this path if set, which saves time during repetitive testing of imports
    public static final String BAZEL_DEFAULT_WORKSPACE_PATH_PREF_NAME = "BAZEL_DEFAULT_WORKSPACE_PATH";
    
}
