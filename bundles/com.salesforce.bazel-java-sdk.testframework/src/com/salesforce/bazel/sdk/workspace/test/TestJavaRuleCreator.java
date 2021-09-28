package com.salesforce.bazel.sdk.workspace.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;

import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.path.FSPathHelper;

public class TestJavaRuleCreator {
    public static void createJavaBuildFile(TestBazelWorkspaceDescriptor workspaceDescriptor, File buildFile,
            TestBazelPackageDescriptor packageDescriptor) throws Exception {
        try (PrintStream out = new PrintStream(new FileOutputStream(buildFile))) {

            String build_java_import = "";
            String java_import_dep = null;
            if (workspaceDescriptor.testOptions.addJavaImport) {
                build_java_import = createJavaImportRule(packageDescriptor.packageName, "libs", "banana", "libbanana");
                new TestBazelTargetDescriptor(packageDescriptor, "banana", "java_import");
                java_import_dep = BazelLabel.BAZEL_COLON + "banana";
            }

            String build_java_library = createJavaLibraryRule(packageDescriptor.packageName, java_import_dep);
            new TestBazelTargetDescriptor(packageDescriptor, packageDescriptor.packageName, "java_library");

            String build_java_test = createJavaTestRule(packageDescriptor.packageName, workspaceDescriptor.testOptions);
            new TestBazelTargetDescriptor(packageDescriptor, packageDescriptor.packageName + "Test", "java_test");

            out.print(build_java_import + "\n\n" + build_java_library + "\n\n" + build_java_test);
        }
    }

    @SuppressWarnings("unused")
    private static String createJavaBinaryRule(String packageName, int packageIndex) {
        String main = FSPathHelper.osSeps("src/main/java/**/*.java"); // $SLASH_OK
        String mainProps = FSPathHelper.osSeps("src/main/resources/main.properties"); // $SLASH_OK

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

    private static String createJavaLibraryRule(String packageName, String dep) {
        String main = FSPathHelper.osSeps("src/main/java/**/*.java"); // $SLASH_OK
        StringBuffer sb = new StringBuffer();
        sb.append("java_library(\n   name=\""); // $SLASH_OK: escape char
        sb.append(packageName);
        sb.append("\",\n"); // $SLASH_OK: line continue
        sb.append("   srcs = glob([\"" + main + "\"]),\n");
        if (dep != null) {
            sb.append("   deps = [\"" + dep + "\"],\n");
        }
        sb.append("   visibility = [\"//visibility:public\"],\n"); // $SLASH_OK: escape char
        sb.append(")");
        return sb.toString();
    }

    private static String createJavaImportRule(String packageName, String importRelativeDir, String targetName,
            String jarNameNoSuffix) {
        String libPath = FSPathHelper.osSeps(importRelativeDir + FSPathHelper.UNIX_SLASH + jarNameNoSuffix + ".jar");
        StringBuffer sb = new StringBuffer();
        sb.append("java_import(\n   name=\""); // $SLASH_OK: escape char
        sb.append(targetName);
        sb.append("\",\n"); // $SLASH_OK: line continue
        sb.append("   jars = [\"" + libPath + "\"],\n");
        sb.append("   visibility = [\"//visibility:public\"],\n"); // $SLASH_OK: escape char
        sb.append(")");
        return sb.toString();
    }

    private static String createJavaTestRule(String packageName, TestOptions commandOptions) {
        boolean explicitJavaTestDeps = commandOptions.explicitJavaTestDeps;
        String test = FSPathHelper.osSeps("src/test/java/**/*.java"); // $SLASH_OK
        String testProps = FSPathHelper.osSeps("src/test/resources/test.properties"); // $SLASH_OK

        StringBuffer sb = new StringBuffer();
        sb.append("java_test(\n   name=\""); // $SLASH_OK: escape char
        sb.append(packageName);
        sb.append("Test\",\n"); // $SLASH_OK: escape char
        sb.append("   srcs = glob([\"" + test + "\"]),\n");
        sb.append("   resources = [\"" + testProps + "\"],\n"); // don't glob, to make sure the file exists in the right location
        sb.append("   visibility = [\"//visibility:public\"],\n"); // $SLASH_OK: escape char
        if (explicitJavaTestDeps) {
            // see ImplicitDependencyHelper.java for more details about this block
            sb.append("   deps = [ \"@maven//:junit_junit\", \"@maven//:org_hamcrest_hamcrest_core\", ],\n"); // $SLASH_OK: escape char
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
        String src = FSPathHelper.osSeps("src/**/*.java"); // $SLASH_OK

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
