package com.salesforce.bazel.sdk.util;

import java.io.File;
import java.io.IOException;

import com.salesforce.bazel.sdk.logging.LogHelper;

/**
 * Static utilities to help with file system paths, especially cross platform.
 */
public class BazelPathHelper {
    private static final LogHelper LOG = LogHelper.log(BazelPathHelper.class);

    /**
     * Primary feature toggle. isUnix is true for all platforms except Windows.
     */
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
            LOG.error("error locating path [{}] on the file system", ioe, directory.getAbsolutePath());
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
            LOG.error("error locating path [{}] on the file system", ioe, directory.getAbsolutePath());
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
            LOG.error("error locating path [{}] on the file system", ioe, path);
        }
        return path;
    }

    public static String osSepRegex() {
        if (isUnix) {
            return "/";
        }
        // why 4? Java requires 2 backslashes to encode the String, and regex needs a double \ to escape backslash in the matcher
        return "\\\\";
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

    /**
     * Convert a slash style relative path to Windows backslash, if running on Windows
     */
    public static String bazelLabelSeps(String fsPath) {
        String path = fsPath;
        if (!isUnix) {
            path = fsPath.replace("\\", "/");
        }
        return path;
    }
}
