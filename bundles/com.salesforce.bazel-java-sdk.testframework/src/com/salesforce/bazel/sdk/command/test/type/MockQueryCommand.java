package com.salesforce.bazel.sdk.command.test.type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.salesforce.bazel.sdk.command.test.MockCommand;
import com.salesforce.bazel.sdk.workspace.test.TestBazelPackageDescriptor;
import com.salesforce.bazel.sdk.workspace.test.TestBazelTargetDescriptor;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceFactory;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

/**
 * Simulates an invocation of 'bazel query xyz'
 */
public class MockQueryCommand extends MockCommand {

    public MockQueryCommand(List<String> commandTokens, TestOptions testOptions,
            TestBazelWorkspaceFactory testWorkspaceFactory) {
        super(commandTokens, testOptions, testWorkspaceFactory);

        if (commandTokens.size() < 3) {
            // this is just 'bazel build' without a target, which is not valid, blow up here as there is something wrong in the calling code
            throw new IllegalArgumentException(
                    "The plugin issued the command 'bazel query' without a third arg. This is not a valid bazel command.");
        }

        // determine the type of query
        String queryArg = commandTokens.get(2);

        if (queryArg.startsWith("kind(rule, set(//")) {
            // QUERY:
            //    kind(rule, set(//projects/libs/javalib0:*))
            // RESPONSE: for each target in the package, a line is written to stdout such as:
            //    java_library rule //projects/libs/javalib0:javalib0
            //    java_test rule //projects/libs/javalib0:javalib0Test

            // strip target to be just '//projects/libs/javalib0'
            String queryPackage = queryArg.substring(17, queryArg.length() - 4);

            if (!isValidBazelTarget(queryPackage)) {
                // by default, isValidBazelTarget() will throw an exception if the package is missing, but the test may configure it to return false instead
                errorLines = Arrays.asList("ERROR: no such package '" + queryPackage
                        + "': BUILD file not found in any of the following directories. Add a BUILD file to a directory to mark it as a package.",
                    "- /fake/abs/path/" + queryPackage); // $SLASH_OK: bazel path
                return;
            }

            TestBazelPackageDescriptor queryPackageDescriptor =
                    testWorkspaceFactory.workspaceDescriptor.createdPackages.get(queryPackage);
            if (queryPackageDescriptor == null) {
                throw new IllegalStateException("The mock package descriptor is missing for package [" + queryPackage
                        + "]. This is a bug in the mock testing framework.");
            }

            // the query is for :* which means all targets, so iterate through the package's targets and write a line per target to stdout
            List<String> outputLines = new ArrayList<>();
            for (TestBazelTargetDescriptor target : queryPackageDescriptor.targets.values()) {
                String outputString = target.targetType + " rule //" + target.targetPath;
                outputLines.add(outputString);
            }
            addSimulatedOutputToCommandStdOut(outputLines);
        } else if (queryArg.startsWith("kind('source file', deps")) {
            // QUERY:
            //    kind('source file', deps(//a/b/c:*))
            // RESPONSE:
            //    //projects/libs/apple/apple-api:src/test/resources/test.properties
            //    //projects/libs/apple/apple-api:src/test/java/demo/apple/api/AppleTest2.java
            //    //projects/libs/apple/apple-api:src/test/java/demo/apple/api/AppleTest.java
            //    //projects/libs/apple/apple-api:src/main/resources/apple.properties
            //    //projects/libs/apple/apple-api:src/main/java/demo/apple/api/AppleOrchard.java
            //    //projects/libs/apple/apple-api:src/main/java/demo/apple/api/Apple.java
            //    //projects/libs/apple/apple-api:BUILD

            int wildcard = queryArg.indexOf("*");
            String queryPackage = queryArg.substring(27, wildcard - 1);
            List<String> outputLines = new ArrayList<>();

            List<String> mainSourceFiles =
                    testWorkspaceFactory.workspaceDescriptor.createdMainSourceFilesForPackages.get(queryPackage);
            if (mainSourceFiles != null) {
                for (String mainSourceFile : mainSourceFiles) {
                    String sourcePath = convertSourceFilePath(queryPackage, mainSourceFile);
                    outputLines.add(sourcePath);
                }
            }
            List<String> testSourceFiles =
                    testWorkspaceFactory.workspaceDescriptor.createdTestSourceFilesForPackages.get(queryPackage);
            if (testSourceFiles != null) {
                for (String testSourceFile : testSourceFiles) {
                    String sourcePath = convertSourceFilePath(queryPackage, testSourceFile);
                    outputLines.add(sourcePath);
                }
            }

            // we have to filter out a bunch of internal source file paths (jdk source files, etc) so simulate those here
            addExtraneousSourceFileLines(queryPackage, outputLines);

            addSimulatedOutputToCommandStdOut(outputLines);
        } else {
            throw new IllegalArgumentException(
                    "The plugin issued the command 'bazel query' with an unknown type of query. "
                            + "The mocking layer (MockQueryCommand) does not know how to simulate a response.");
        }
    }

    private String convertSourceFilePath(String queryPackage, String rawSourceFilePath) {
        // convert: projects/libs/javalib0/source/dev/java/com/salesforce/fruit0/Apple0.java
        // to:    //projects/libs/javalib0:source/dev/java/com/salesforce/fruit0/Apple0.java

        String sourcePath = rawSourceFilePath.substring(queryPackage.length() + 1);
        return "//" + queryPackage + ":" + sourcePath;
    }

    // we have to filter out a bunch of non-source file paths (jdk source files, etc) so simulate those here
    private void addExtraneousSourceFileLines(String queryPackage, List<String> outputLines) {
        outputLines.add("//" + queryPackage + ":BUILD");
        outputLines.add("@bazel_tools//src/tools/launcher:java_launcher.h");
        outputLines.add("@bazel_tools//src/tools/launcher:java_launcher.cc");
        outputLines.add("@local_config_cc//:builtin_include_directory_paths");
        outputLines.add("@local_config_cc//:wrapped_clang");
        outputLines.add("@local_jdk//:bin/jinfo");
        outputLines.add("@local_jdk//:bin/keytool");
        outputLines.add(
            "@maven//:v1/https/repo1.maven.org/maven2/ch/qos/logback/logback-core/1.2.3/logback-core-1.2.3-sources.jar");
        addSimulatedOutputToCommandStdOut("@remote_java_tools_darwin//:java_tools/ijar/zip.cc");
        outputLines.add("@remote_java_tools_linux//java_tools/zlib:crc32.c");
    }

}
