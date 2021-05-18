package com.salesforce.bazel.sdk.util;

import java.io.File;

/**
 * Utils for integrating with and configuring the Bazel executable (or Bazelisk).
 */
public class BazelExecutableUtil {

    /**
     * Returns the absolute path to the bazel executable from the environment.
     */
    public static String which(String name, String def) {

        // TODO how to find the bazel executable on Windows?

        for (String dirname : System.getenv("PATH").split(File.pathSeparator)) {
            File file = new File(dirname, name);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return def;
    }

}
