package com.salesforce.bazel.sdk.lang.jvm.external;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

/**
 * coursier is a Maven repo integration tool that is used in some cases by maven_install.
 * This class encapsulates the behaviors of coursier.
 */
public class CoursierUtil {

    // TODO windows

    private static final String COURSIER_CACHE_LOCATION_LINUX = "/.cache/coursier/v1";
    private static final String COURSIER_CACHE_LOCATION_MACOS = "/Library/Caches/Coursier/v1";
    private static final String COURSIER_CACHE_LOCATION_WINDOWS = "/Coursier/cache/v1";
    private final Map<String, File> coursierCacheLocations = new HashMap<>();


    /**
     * If the user ran this:  bazel run @unpinned_maven//:pin
     * This invoked Coursier (a jar downloader) which populated the Coursier cache on the machine.
     * The cache location is platform dependent, and global per user. So if you have multiple Bazel workspaces
     * this cache location will have the union of all jars used by the workspaces.
     */
    public File addCoursierCacheLocation(BazelWorkspace bazelWorkspace, OperatingEnvironmentDetectionStrategy os) {

        // workspace name is the key to our cached data
        String workspaceName = bazelWorkspace.getName();
        File coursierCacheLocation = coursierCacheLocations.get(workspaceName);
        if (coursierCacheLocation == null) {
            // TODO we are just computing the default cache location, but there are ways to force coursier to use alternate locations.
            // we should also factor that in, but that will be HARD. Details:
            //     https://get-coursier.io/docs/2.0.0-RC5-3/cache.html#default-location

            String defaultLocation = null;
            String homedir = System.getProperty("user.home");
            String osName = os.getOperatingSystemName();
            if ("linux".equals(osName)) {
                defaultLocation = homedir + COURSIER_CACHE_LOCATION_LINUX;
            } else if ("darwin".equals(osName)) {
                defaultLocation = homedir + COURSIER_CACHE_LOCATION_MACOS;
            } else if ("windows".equals(osName)) {
                defaultLocation = homedir + COURSIER_CACHE_LOCATION_WINDOWS;
            } else {
                // or is there a better default?
                defaultLocation = homedir + COURSIER_CACHE_LOCATION_LINUX;
            }

            coursierCacheLocation = new File(defaultLocation);
            coursierCacheLocations.put(workspaceName, coursierCacheLocation);
        }
        return coursierCacheLocation;
    }

    public void discardComputedWork(BazelWorkspace bazelWorkspace) {
        String workspaceName = bazelWorkspace.getName();
        coursierCacheLocations.remove(workspaceName);
    }
}
