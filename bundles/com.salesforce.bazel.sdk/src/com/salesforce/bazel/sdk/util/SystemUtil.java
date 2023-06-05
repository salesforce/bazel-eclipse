package com.salesforce.bazel.sdk.util;

/**
 * A utility for system level items.
 */
public class SystemUtil {

    private static final SystemUtil instance = new SystemUtil();

    public static final SystemUtil getInstance() {
        return instance;
    }

    public String getOs() {
        return System.getProperty("os.name").toLowerCase();
    }

    public boolean isMac() {
        return getOs().indexOf("mac") >= 0;
    }

    public boolean isUnix() {
        var os = getOs();
        return (os.indexOf("nix") >= 0) || (os.indexOf("nux") >= 0) || (os.indexOf("aix") > 0);
    }

    public boolean isWindows() {
        return getOs().indexOf("win") >= 0;
    }

}
