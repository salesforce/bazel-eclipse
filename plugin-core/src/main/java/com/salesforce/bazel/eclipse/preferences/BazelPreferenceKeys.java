package com.salesforce.bazel.eclipse.preferences;

/**
 * Names of the preference keys. Look for usages of these variables to see how BEF uses prefs.
 */
public class BazelPreferenceKeys {
    
    // *********************************************************************
    // USER FACING PREFS (visible on Prefs page)
    
    // path the the bazel executable
    public static final String BAZEL_PATH_PREF_NAME = "BAZEL_PATH"; 
    
    // Global classpath search allows BEF to index all jars associated with a Bazel Workspace which makes them
    // available for Open Type searches. These prefs enabled it, and tell BEF where to look for the local cache
    // of downloaded jars.
    public static final String GLOBALCLASSPATH_SEARCH_PREF_NAME = "GLOBALCLASSPATH_SEARCH_ENABLED";
    public static final String EXTERNAL_JAR_CACHE_PATH_PREF_NAME = "EXTERNAL_JAR_CACHE_PATH";

    
    // *********************************************************************
    // BREAK GLASS PREFS (emergency feature flags to disable certain features in case of issues)
    // Naming convention: these should all started with the token DISABLE_

    // We support Bazel workspaces in which the WORKSPACE file in the root is actually a soft link to the actual
    // file in a subdirectory. Due to the way the system Open dialog works, we have to do some sad logic to figure
    // out this is the case. This flag disables this feature, in case that logic causes problems for some users. 
    // https://github.com/salesforce/bazel-eclipse/issues/164
    public static final String DISABLE_UNRESOLVE_WORKSPACEFILE_SOFTLINK = "DISABLE_UNRESOLVE_WORKSPACEFILE_SOFTLINK";
    
    
    // *********************************************************************
    // BEF DEVELOPER PREFS (for efficient repetitive testing of BEF)
    
    // The import wizard will be populated by this path if set, which saves time during repetitive testing of imports
    public static final String BAZEL_DEFAULT_WORKSPACE_PATH_PREF_NAME = "BAZEL_DEFAULT_WORKSPACE_PATH";
    
}
