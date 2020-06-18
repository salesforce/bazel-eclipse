package com.salesforce.bazel.eclipse.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Map;

public class TestJavaRuleCreator {
    public static void createJavaBuildFile(TestBazelWorkspaceDescriptor workspaceDescriptor, File buildFile, TestBazelPackageDescriptor packageDescriptor) throws Exception {
        try (PrintStream out = new PrintStream(new FileOutputStream(buildFile))) {
            
            String build_java_library =  createJavaLibraryRule(packageDescriptor.packageName);
            new TestBazelTargetDescriptor(packageDescriptor, packageDescriptor.packageName, "java_library");
            
            String build_java_test = createJavaTestRule(packageDescriptor.packageName, workspaceDescriptor.testOptions);
            new TestBazelTargetDescriptor(packageDescriptor, packageDescriptor.packageName+"Test", "java_test");
                        
            out.print(build_java_library+"\n\n"+build_java_test);
        } 
    }
    
    @SuppressWarnings("unused")
    private static String createJavaBinaryRule(String packageName, int packageIndex) {
        StringBuffer sb = new StringBuffer();
        sb.append("java_binary(\n   name=\"");
        sb.append(packageName);
        sb.append("\",\n");
        sb.append("   srcs = glob([\"src/main/java/**/*.java\"]),\n");
        sb.append("   resources = [\"src/main/resources/main.properties\"],\n"); // don't glob, to make sure the file exists in the right location
        sb.append("   create_executable = True,\n");
        sb.append("   main_class = \"com.salesforce.fruit"+packageIndex+".Apple\",\n");
        sb.append(")");
        return sb.toString();
    }

    private static String createJavaLibraryRule(String packageName) {
        StringBuffer sb = new StringBuffer();
        sb.append("java_library(\n   name=\"");
        sb.append(packageName);
        sb.append("\",\n");
        sb.append("   srcs = glob([\"src/main/java/**/*.java\"]),\n");
        sb.append("   visibility = [\"//visibility:public\"],\n");
        sb.append(")");
        return sb.toString();
    }
    
    private static String createJavaTestRule(String packageName, Map<String, String> commandOptions) {
        boolean explicitJavaTestDeps = "true".equals(commandOptions.get("explicit_java_test_deps"));
        
        StringBuffer sb = new StringBuffer();
        sb.append("java_test(\n   name=\"");
        sb.append(packageName);
        sb.append("Test\",\n");
        sb.append("   srcs = glob([\"src/test/java/**/*.java\"]),\n");
        sb.append("   resources = [\"src/test/resources/test.properties\"],\n"); // don't glob, to make sure the file exists in the right location
        sb.append("   visibility = [\"//visibility:public\"],\n");
        if (explicitJavaTestDeps) {
            // see ImplicitDependencyHelper.java for more details about this block
            sb.append("   deps = [ \"@junit_junit//jar\", \"@org_hamcrest_hamcrest_core//jar\", ],\n");
        }
        sb.append(")");
        return sb.toString();
    }

    @SuppressWarnings("unused")
    private static String createSpringBootRule(String packageName, int projectIndex) {
        StringBuffer sb = new StringBuffer();
        sb.append("springboot(\n   name=\"");
        sb.append(packageName);
        sb.append("\",\n");
        sb.append("   java_library = \":base_lib\",\n");
        sb.append("   boot_app_class = \"com.salesforce.fruit"+projectIndex+".Apple\",\n");
        sb.append(")");
        return sb.toString();
    }

    @SuppressWarnings("unused")
    private static String createSpringBootTestRule(String packageName) {
        StringBuffer sb = new StringBuffer();
        sb.append("springboot_test(\n   name=\"");
        sb.append(packageName);
        sb.append("\",\n");
        sb.append("   deps = [],\n");
        sb.append("   srcs = glob([\"src/**/*.java\"]),\n");
        sb.append(")");
        return sb.toString();
    }

}
