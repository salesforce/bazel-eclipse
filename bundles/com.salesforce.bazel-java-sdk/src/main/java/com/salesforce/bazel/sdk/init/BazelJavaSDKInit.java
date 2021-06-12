package com.salesforce.bazel.sdk.init;

public class BazelJavaSDKInit {
    
    private static String toolName = "bazel-java-sdk";
    private static String toolFilenamePrefix = "bzljavasdk";
    
    /**
     * 
     * @param toolName Human friendly tool name, may appear in log messages for example.
     * @param toolFilenamePrefix Filename friendly prefix for this tool, may be used in log files and Aspect files.
     */
    public static void initialize(String toolName, String toolFilenamePrefix) {
        BazelJavaSDKInit.toolName = toolName;
        BazelJavaSDKInit.toolFilenamePrefix = toolFilenamePrefix;
    }
    
    
    /**
     * Human friendly tool name, may appear in log messages for example.
     */
    public static String getToolName() {
        return toolName;
    }
    
    /**
     * Human friendly tool name, may appear in log messages for example.
     */
    public static void setToolName(String toolName) {
        BazelJavaSDKInit.toolName = toolName;
    }
    
    /**
     * Filename friendly prefix for this tool, may be used in log files and Aspect files.
     */
    public static String getToolFilenamePrefix() {
        return toolFilenamePrefix;
    }

    /**
     * Filename friendly prefix for this tool, may be used in log files and Aspect files.
     */
    public static void setToolFilenamePrefix(String toolFilenamePrefix) {
        BazelJavaSDKInit.toolFilenamePrefix = toolFilenamePrefix;
    }

    
}
