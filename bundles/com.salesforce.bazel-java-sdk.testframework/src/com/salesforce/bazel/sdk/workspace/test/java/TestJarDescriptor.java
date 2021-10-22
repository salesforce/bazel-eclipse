package com.salesforce.bazel.sdk.workspace.test.java;

public class TestJarDescriptor {
    public String bazelName; // org_slf4j_slf4j_api or apple

    public String jarFileName; // slf4j-api-1.7.25.jar or libapple.jar
    public String srcJarFileName; // slf4j-api-1.7.25-sources.jar or libapple-src.jar

    public String jarAbsolutePath;
    public String jarRelativePath; // execroot/bazel_demo_simplejava_mvninstall/bazel-out/darwin-fastbuild/bin/projects/services/fruit-salad-service/fruit-salad/fruit-salad.jar

    public String srcJarAbsolutePath;
    public String srcJarRelativePath; // execroot/bazel_demo_simplejava_mvninstall/bazel-out/darwin-fastbuild/bin/projects/services/fruit-salad-service/fruit-salad/fruit-salad-src.jar

    // MAVEN
    // for Maven jars, we have GAV info
    public boolean isMaven = false;
    public String groupNameWithDots; // org.slf4j
    public String artifactName; // slf4j-api
    public String version; // 1.7.25

    // INTERNAL JARS
    // for workspace built jars, Bazel will generate these extra jars
    public String interfaceJarAbsolutePath; // libapple-hjar.jar
    public String testJarAbsolutePath; // apple-test.jar
    public String srcTestJarAbsolutePath; // apple-test-src.jar
}