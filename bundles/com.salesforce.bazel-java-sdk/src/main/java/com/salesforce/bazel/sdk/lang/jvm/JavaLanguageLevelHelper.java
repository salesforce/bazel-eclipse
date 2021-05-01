package com.salesforce.bazel.sdk.lang.jvm;

/**
 * Utility methods for the Java language levels
 * 
 * @author plaird
 *
 */
public class JavaLanguageLevelHelper {

    /**
     * Parses a string like this: -source 8 -target 8
     * 
     * @return the Source level, e.g. 8 or 11
     */
    public static int getSourceLevelAsInt(String javacoptString) {
        int level = 11;
        String levelStr = getSourceLevel(javacoptString);
        try {
            level = Integer.parseInt(levelStr);
        } catch (Exception anyE) {}
        return level;
    }

    /**
     * Parses a string like this: -source 8 -target 8
     * 
     * @return the Source level, e.g. "8" or "11"
     */
    public static String getSourceLevel(String javacoptString) {
        String level = "11";

        if (javacoptString != null) {
            int sourceLocation = javacoptString.indexOf("-source");
            if (sourceLocation >= 0) {
                level = javacoptString.substring(sourceLocation + 8);
                int nextParam = level.indexOf("-");
                if (nextParam > 0) {
                    level = level.substring(0, nextParam);
                }
                level = level.replace("\"", "");
                level = level.trim();
            }
        }

        return level;
    }

    /**
     * Parses a string like this: -source 8 -target 8
     * 
     * @return the Target level, e.g. 8 or 11
     */
    public static int getTargetLevelAsInt(String javacoptString) {
        int level = 11;
        String levelStr = getTargetLevel(javacoptString);
        try {
            level = Integer.parseInt(levelStr);
        } catch (Exception anyE) {}
        return level;
    }

    /**
     * Parses a string like this: -source 8 -target 8
     * 
     * @return the Target level, e.g. "8" or "11"
     */
    public static String getTargetLevel(String javacoptString) {
        String level = "11";

        if (javacoptString != null) {
            int sourceLocation = javacoptString.indexOf("-target");
            if (sourceLocation >= 0) {
                level = javacoptString.substring(sourceLocation + 8);
                int nextParam = level.indexOf("-");
                if (nextParam > 0) {
                    level = level.substring(0, nextParam);
                }
                level = level.replace("\"", "");
                level = level.trim();
            }
        }

        return level;
    }
}
