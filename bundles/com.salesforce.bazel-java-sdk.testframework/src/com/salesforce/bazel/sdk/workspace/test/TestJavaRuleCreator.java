package com.salesforce.bazel.sdk.workspace.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Map;

import com.salesforce.bazel.sdk.path.BazelPathHelper;

public class TestJavaRuleCreator {
    public static void createJavaBuildFile(TestBazelWorkspaceDescriptor workspaceDescriptor, File buildFile,
            TestBazelPackageDescriptor packageDescriptor) throws Exception {
        try (PrintStream out = new PrintStream(new FileOutputStream(buildFile))) {

            String build_java_library = createJavaLibraryRule(packageDescriptor.packageName);
            new TestBazelTargetDescriptor(packageDescriptor, packageDescriptor.packageName, "java_library");

            String build_java_test = createJavaTestRule(packageDescriptor.packageName, workspaceDescriptor.testOptions);
            new TestBazelTargetDescriptor(packageDescriptor, packageDescriptor.packageName + "Test", "java_test");

            out.print(build_java_library + "\n\n" + build_java_test);
        }
    }

    @SuppressWarnings("unused")
    private static String createJavaBinaryRule(String packageName, int packageIndex) {
        String main = BazelPathHelper.osSeps("src/main/java/**/*.java"); // $SLASH_OK
        String mainProps = BazelPathHelper.osSeps("src/main/resources/main.properties"); // $SLASH_OK

        StringBuffer sb = new StringBuffer();
        sb.append("java_binary(\n   name=\""); // $SLASH_OK: escape char
        sb.append(packageName);
        sb.append("\",\n"); // $SLASH_OK: line continue
        sb.append("   srcs = glob([\"" + main + "\"]),\n");
        sb.append("   resources = [\"" + mainProps + "\"],\n"); // don't glob, to make sure the file exists in the right location
        sb.append("   create_executable = True,\n");
        sb.append("   main_class = \"com.salesforce.fruit" + packageIndex + ".Apple\",\n"); // $SLASH_OK: escape char
        sb.append(")");
        return sb.toString();
    }

    private static String createJavaLibraryRule(String packageName) {
        String main = BazelPathHelper.osSeps("src/main/java/**/*.java"); // $SLASH_OK
        StringBuffer sb = new StringBuffer();
        sb.append("java_library(\n   name=\""); // $SLASH_OK: escape char
        sb.append(packageName);
        sb.append("\",\n"); // $SLASH_OK: line continue
        sb.append("   srcs = glob([\"" + main + "\"]),\n");
        sb.append("   visibility = [\"//visibility:public\"],\n"); // $SLASH_OK: escape char
        sb.append(")");
        return sb.toString();
    }

    private static String createJavaTestRule(String packageName, Map<String, String> commandOptions) {
        boolean explicitJavaTestDeps = "true".equals(commandOptions.get("explicit_java_test_deps"));
        String test = BazelPathHelper.osSeps("src/test/java/**/*.java"); // $SLASH_OK
        String testProps = BazelPathHelper.osSeps("src/test/resources/test.properties"); // $SLASH_OK

        StringBuffer sb = new StringBuffer();
        sb.append("java_test(\n   name=\""); // $SLASH_OK: escape char
        sb.append(packageName);
        sb.append("Test\",\n"); // $SLASH_OK: escape char
        sb.append("   srcs = glob([\"" + test + "\"]),\n");
        sb.append("   resources = [\"" + testProps + "\"],\n"); // don't glob, to make sure the file exists in the right location
        sb.append("   visibility = [\"//visibility:public\"],\n"); // $SLASH_OK: escape char
        if (explicitJavaTestDeps) {
            // see ImplicitDependencyHelper.java for more details about this block
            sb.append("   deps = [ \"@junit_junit//jar\", \"@org_hamcrest_hamcrest_core//jar\", ],\n"); // $SLASH_OK: escape char
        }
        sb.append(")");
        return sb.toString();
    }

    @SuppressWarnings("unused")
    private static String createSpringBootRule(String packageName, int projectIndex) {
        StringBuffer sb = new StringBuffer();
        sb.append("springboot(\n   name=\""); // $SLASH_OK: escape char
        sb.append(packageName);
        sb.append("\",\n"); // $SLASH_OK: line continue
        sb.append("   java_library = \":base_lib\",\n"); // $SLASH_OK: escape char
        sb.append("   boot_app_class = \"com.salesforce.fruit" + projectIndex + ".Apple\",\n"); // $SLASH_OK: escape char
        sb.append(")");
        return sb.toString();
    }

    @SuppressWarnings("unused")
    private static String createSpringBootTestRule(String packageName) {
        String src = BazelPathHelper.osSeps("src/**/*.java"); // $SLASH_OK

        StringBuffer sb = new StringBuffer();
        sb.append("springboot_test(\n   name=\""); // $SLASH_OK: escape char
        sb.append(packageName);
        sb.append("\",\n"); // $SLASH_OK: line continue
        sb.append("   deps = [],\n");
        sb.append("   srcs = glob([\"" + src + "\"]),\n");
        sb.append(")");
        return sb.toString();
    }

}
