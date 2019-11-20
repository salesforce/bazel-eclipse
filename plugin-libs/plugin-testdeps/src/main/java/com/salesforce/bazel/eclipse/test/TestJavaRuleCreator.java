package com.salesforce.bazel.eclipse.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class TestJavaRuleCreator {
    public static void createJavaBuildFile(File buildFile, String projectName, int projectIndex) throws Exception {
        try (PrintStream out = new PrintStream(new FileOutputStream(buildFile))) {
            
            String build_java_library =  createJavaLibraryRule(projectName);
            String build_java_test = createJavaTestRule(projectName);
                        
            out.print(build_java_library+"\n\n"+build_java_test);
        } 
    }
    
    @SuppressWarnings("unused")
    private static String createJavaBinaryRule(String projectName, int projectIndex) {
        StringBuffer sb = new StringBuffer();
        sb.append("java_binary(\n   name=\"");
        sb.append(projectName);
        sb.append("\",\n");
        sb.append("   srcs = glob([\"src/main/java/**/*.java\"]),\n");
        sb.append("   create_executable = True,\n");
        sb.append("   main_class = \"com.salesforce.fruit"+projectIndex+".Apple\",\n");
        sb.append(")");
        return sb.toString();
    }

    private static String createJavaLibraryRule(String projectName) {
        StringBuffer sb = new StringBuffer();
        sb.append("java_library(\n   name=\"");
        sb.append(projectName);
        sb.append("\",\n");
        sb.append("   srcs = glob([\"src/main/java/**/*.java\"]),\n");
        sb.append("   visibility = [\"//visibility:public\"],\n");
        sb.append(")");
        return sb.toString();
    }
    
    private static String createJavaTestRule(String projectName) {
        StringBuffer sb = new StringBuffer();
        sb.append("java_test(\n   name=\"");
        sb.append(projectName);
        sb.append("Test\",\n");
        sb.append("   srcs = glob([\"src/main/java/**/*.java\"]),\n");
        sb.append("   visibility = [\"//visibility:public\"],");
        sb.append(")");
        return sb.toString();
    }

    @SuppressWarnings("unused")
    private static String createSpringBootRule(String projectName, int projectIndex) {
        StringBuffer sb = new StringBuffer();
        sb.append("springboot(\n   name=\"");
        sb.append(projectName);
        sb.append("\",\n");
        sb.append("   java_library = \":base_lib\",\n");
        sb.append("   boot_app_class = \"com.salesforce.fruit"+projectIndex+".Apple\",\n");
        sb.append(")");
        return sb.toString();
    }

    @SuppressWarnings("unused")
    private static String createSpringBootTestRule(String projectName) {
        StringBuffer sb = new StringBuffer();
        sb.append("springboot_test(\n   name=\"");
        sb.append(projectName);
        sb.append("\",\n");
        sb.append("   deps = [],\n");
        sb.append("   srcs = glob([\"src/**/*.java\"]),\n");
        sb.append(")");
        return sb.toString();
    }

}
