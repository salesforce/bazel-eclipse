package com.salesforce.bazel.sdk.util;

import java.io.File;
import java.io.IOException;

/**
 * Static utilities.
 */
public class BazelPathHelper {

    public static boolean isUnix = true;

    static {
        if (System.getProperty("os.name").contains("Windows")) {
            isUnix = false;
        }
    }

    /**
     * Resolve softlinks and other abstractions in the workspace paths.
     */
    public static File getCanonicalFileSafely(File directory) {
        if (directory == null) {
            return null;
        }
        try {
            directory = directory.getCanonicalFile();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return directory;
    }

    /**
     * Resolve softlinks and other abstractions in the workspace paths.
     */
    public static String getCanonicalPathStringSafely(File directory) {
        String path = null;
        if (directory == null) {
            return null;
        }
        try {
            path = directory.getCanonicalPath();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        if (path == null) {
            // fallback to absolute path in case canonical path fails
            path = directory.getAbsolutePath();
        }
        return path;
    }

    /**
     * Resolve softlinks and other abstractions in the workspace paths.
     */
    public static String getCanonicalPathStringSafely(String path) {
        if (path == null) {
            return null;
        }
        try {
            path = new File(path).getCanonicalPath();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return path;
    }

    /**
     * Convert a slash style relative path to Windows backslash, if running on Windows
     */
    public static String osSeps(String unixStylePath) {
        String path = unixStylePath;
        if (!isUnix) {
            path = unixStylePath.replace("/", "\\");
        }
        return path;
    }

    /**
     * Convert a slash style relative path to Windows backslash, if running on Windows. Replace with two back slashes if
     * so, as the consumer needs escaped backslashes.
     */
    public static String osSepsEscaped(String unixStylePath) {
        String path = unixStylePath;
        if (!isUnix) {
            path = unixStylePath.replace("/", "\\\\");
        }
        return path;
    }
}
