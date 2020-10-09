package com.salesforce.bazel.eclipse.preferences;

import java.util.HashMap;
import java.util.Map;

/**
 * Names of the preference keys. Preferences 
 */
public class BazelPreferenceKeys {
    public static Map<String, String> defaultValues = new HashMap<>();

    // ADDING A PREF?
    // If you want to support a new pref, add it in the appropriate section below. Then,
    // you should also add it to one of the arrays at the bottom for automated support of your pref
    // in the global pref file (~/.bazel/eclipse.properties)

    // *********************************************************************
    // USER FACING PREFS (visible on Prefs page)

    // path the the bazel executable
    public static final String BAZEL_PATH_PREF_NAME = "BAZEL_PATH";
    {
        defaultValues.put(BAZEL_PATH_PREF_NAME, "/usr/local/bin/bazel");
    }

    // Global classpath search allows BEF to index all jars associated with a Bazel Workspace which makes them
    // available for Open Type searches. These prefs enabled it, and override the default location(s) of where 
    // to look for the local cache of downloaded jars.
    public static final String GLOBALCLASSPATH_SEARCH_PREF_NAME = "GLOBALCLASSPATH_SEARCH_ENABLED";
    public static final String EXTERNAL_JAR_CACHE_PATH_PREF_NAME = "EXTERNAL_JAR_CACHE_PATH";
    {
        defaultValues.put(GLOBALCLASSPATH_SEARCH_PREF_NAME, "false");
    }

    // *********************************************************************
    // BREAK GLASS PREFS (emergency feature flags to disable certain features in case of issues)
    // Naming convention: these should all started with the token DISABLE_

    // We support Bazel workspaces in which the WORKSPACE file in the root is actually a soft link to the actual
    // file in a subdirectory. Due to the way the system Open dialog works, we have to do some sad logic to figure
    // out this is the case. This flag disables this feature, in case that logic causes problems for some users. 
    // https://github.com/salesforce/bazel-eclipse/issues/164
    public static final String DISABLE_UNRESOLVE_WORKSPACEFILE_SOFTLINK = "DISABLE_UNRESOLVE_WORKSPACEFILE_SOFTLINK";
    {
        defaultValues.put(DISABLE_UNRESOLVE_WORKSPACEFILE_SOFTLINK, "false");
    }

    // *********************************************************************
    // BEF DEVELOPER PREFS (for efficient repetitive testing of BEF)

    // The import wizard will be populated by this path if set, which saves time during repetitive testing of imports
    public static final String BAZEL_DEFAULT_WORKSPACE_PATH_PREF_NAME = "BAZEL_DEFAULT_WORKSPACE_PATH";

    // *********************************************************************
    // ARRAYS
    // Be sure to add your new pref name here, as that is how the global pref file gets loaded into Eclipse prefs

    // prefs that have string values
    public static final String[] ALL_STRING_PREFS = new String[] { 
            BAZEL_PATH_PREF_NAME,
            EXTERNAL_JAR_CACHE_PATH_PREF_NAME, 
            BAZEL_DEFAULT_WORKSPACE_PATH_PREF_NAME 
    };

    // prefs that have boolean values
    public static final String[] ALL_BOOLEAN_PREFS = new String[] { 
            GLOBALCLASSPATH_SEARCH_PREF_NAME, 
            DISABLE_UNRESOLVE_WORKSPACEFILE_SOFTLINK 
    };

}
