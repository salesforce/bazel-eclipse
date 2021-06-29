package com.salesforce.bazel.sdk.path;

import java.io.File;
import java.io.IOException;

import com.salesforce.bazel.sdk.logging.LogHelper;

/**
 * Static utilities to help with file system paths, especially cross platform.
 */
public class BazelPathHelper {
    private static final LogHelper LOG = LogHelper.log(BazelPathHelper.class);

    // BAZEL PATH ELEMENTS

    // Double slash characters for root of Bazel paths
    public static final String BAZEL_ROOT_SLASHES = "//";

    // All packages wildcard 
    public static final String BAZEL_ALL_REPO_PACKAGES = "//...";

    // Slash character for Bazel paths
    public static final String BAZEL_SLASH = "/";

    // Colon character for Bazel paths that delimits the target
    public static final String BAZEL_COLON = ":";

    // Wildcard used as a package, that identifies all packages at the current level or below
    public static final String BAZEL_WILDCARD_ALLPACKAGES = "...";

    // Wildcard used as a target, that identifies all targets 
    public static final String BAZEL_WILDCARD_ALLTARGETS = "all";

    // Wildcard used as a target, that identifies all targets including implicit targets (_deploy.jar etc) 
    public static final String BAZEL_WILDCARD_ALLTARGETS_STAR = "*";

    // FILE SYSTEM PATH ELEMENTS

    // Slash character for unix file paths
    public static final String UNIX_SLASH = "/";

    // Backslash character; this is provided as a constant to help code searches
    // for Windows specific code. There are two backslash characters because Java 
    // requires a leading backslash to encode a backslash
    public static final String WINDOWS_BACKSLASH = "\\";

    // Regex pattern to use to look for a single backslash character in a path
    // why 4? 
    // Regex needs a double \ to escape backslash in the matcher (1+1=2)
    // Java requires a backslash to encode a backslash in the String (2x2=4)
    public static final String WINDOWS_BACKSLASH_REGEX = "\\\\";

    // Slash character for file paths in jar files
    public static final String JAR_SLASH = "/";

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
            return UNIX_SLASH;
        }
        return WINDOWS_BACKSLASH_REGEX;
    }

    /**
     * Convert a slash style relative path to Windows backslash, if running on Windows
     */
    public static String osSeps(String unixStylePath) {
        String path = unixStylePath;
        if (!isUnix) {
            path = unixStylePath.replace(UNIX_SLASH, WINDOWS_BACKSLASH);
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
            path = unixStylePath.replace(UNIX_SLASH, WINDOWS_BACKSLASH);
        }
        return path;
    }

    /**
     * Convert a slash style relative path to Windows backslash, if running on Windows
     */
    public static String bazelLabelSeps(String fsPath) {
        String path = fsPath;
        if (!isUnix) {
            path = fsPath.replace(WINDOWS_BACKSLASH, UNIX_SLASH);
        }
        return path;
    }
}
