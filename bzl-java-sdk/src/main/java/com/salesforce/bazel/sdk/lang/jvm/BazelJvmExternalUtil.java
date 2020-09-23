package com.salesforce.bazel.sdk.lang.jvm;

import java.io.File;

import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

/**
 * Helper routines for downloading jars from an external repo.
 * See also rules_jvm_external (maven_install) and bazel_tools  (jvm_maven_import_external)
 */
public class BazelJvmExternalUtil {
    private static final String COURSIER_CACHE_LOCATION_LINUX = "/.cache/coursier/v1";
    private static final String COURSIER_CACHE_LOCATION_MACOS = "/Library/Caches/Coursier/v1";
    private static final String COURSIER_CACHE_LOCATION_WINDOWS = "/Coursier/cache/v1"; 
    private static File coursierCacheLocation = null;
    
    
    public static File getDownloadedJarCacheLocation(OperatingEnvironmentDetectionStrategy os) {
        // TODO this assumes maven_install, but need to support jvm_maven_import_external too
        // but the problem is with IDEs this pref will be set before the Bazel workspace is identified
        // so we can't default this using knowledge of the download tech
        // we should create a second edition of this method that takes a BazelWorkspace as a param in addition
        // to os, and then that one should be called after the workspace is loaded
        
        if (coursierCacheLocation == null) {
            String defaultLocation = null;
            String homedir = System.getProperty("user.home");
            if ("linux".equals(os.getOperatingSystemName())) {
                defaultLocation = homedir+COURSIER_CACHE_LOCATION_LINUX;
            } else if ("darwin".equals(os.getOperatingSystemName())) {
                defaultLocation = homedir+COURSIER_CACHE_LOCATION_MACOS;
            } else if ("windows".equals(os.getOperatingSystemName())) {
                defaultLocation = homedir+COURSIER_CACHE_LOCATION_WINDOWS;
            } else {
                // or is there a better default?
                defaultLocation = homedir+COURSIER_CACHE_LOCATION_LINUX;
            }
            
            // TODO we are assuming the default cache location, but there are ways to force coursier to use alternate locations
            // we should also factor that in. 
            //
            // The problem with the default location is it might be mixing in jars from multiple Bazel workspaces.
            //
            // Another thought is to use a search strategy and accumulate a list of found
            // caches on disk, and then ask the user to choose which one(s) to use.
            // https://github.com/bazelbuild/rules_jvm_external#using-a-persistent-artifact-cache
            
            coursierCacheLocation = new File(defaultLocation);
         }
        
        return coursierCacheLocation;
    }
    
}
