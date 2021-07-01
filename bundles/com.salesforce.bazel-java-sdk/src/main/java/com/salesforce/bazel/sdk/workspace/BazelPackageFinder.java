package com.salesforce.bazel.sdk.workspace;

import java.io.File;
import java.util.Set;

import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.util.BazelConstants;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;

/**
 * Scans the workspace looking for packages that contains rules that are registered with the SDK.
 */
public class BazelPackageFinder {
    LogHelper logger;

    public BazelPackageFinder() {
        logger = LogHelper.log(this.getClass());
    }

    /**
     * Navigates the workspace reading Bazel BUILD files, looking for packages that contains rules types that are
     * registered with the SDK.
     * 
     * @param dir
     *            the starting directory (usually the Bazel workspace root)
     * @param monitor
     *            a progress monitor that is updated
     * @param buildFileLocations
     *            the output, the list of found BUILD files with interesting rules
     * @param depth
     *            the maximum depth to descend
     */
    public void findBuildFileLocations(File dir, WorkProgressMonitor monitor, Set<File> buildFileLocations, int depth) {
        if (!dir.isDirectory()) {
            return;
        }

        try {
            File[] dirFiles = dir.listFiles();
            for (File dirFile : dirFiles) {

                if (shouldIgnore(dirFile, depth)) {
                    continue;
                }

                if (isBuildFile(dirFile)) {

                    // great, this dir is a Bazel package (but this may be a non-Java package)
                    // scan the BUILD file looking for java rules, only add if this is a java project
                    if (BuildFileSupport.hasRegisteredRules(dirFile)) {
                        buildFileLocations.add(FSPathHelper.getCanonicalFileSafely(dir));
                    }
                } else if (dirFile.isDirectory()) {
                    findBuildFileLocations(dirFile, monitor, buildFileLocations, depth + 1);
                }
            }
        } catch (Exception anyE) {
            logger.error("ERROR scanning for Bazel packages: {}", anyE.getMessage());
        }
    }

    private static boolean shouldIgnore(File f, int depth) {
        if (depth == 0 && f.isDirectory() && f.getName().startsWith("bazel-")) {
            // this is a Bazel internal directory at the root of the project dir, ignore
            // TODO should this use one of the ignore directory facilities at the bottom of this class?
            return true;
        }
        return false;
    }

    private static boolean isBuildFile(File candidate) {
        return BazelConstants.BUILD_FILE_NAMES.contains(candidate.getName());
    }
}
