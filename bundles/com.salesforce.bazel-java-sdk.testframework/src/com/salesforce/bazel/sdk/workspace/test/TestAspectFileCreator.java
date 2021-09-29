package com.salesforce.bazel.sdk.workspace.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.path.FSPathHelper;

/**
 * Writes json files to the Bazel output file system that mimic what is written by the Bazel aspects. Each json file
 * contains dependency information and source file information that is used by the BazelClasspathContainer to create the
 * classpath for each Eclipse project.
 */
public class TestAspectFileCreator {

    /**
     * Write a particular json file as if generated by the Bazel aspect for a package containing Java source files.
     *
     * @return the absolute File path to the file
     */
    public static String createJavaAspectFile(File bazelOutputBase, String packageRelativePath, String packageName,
            String targetName, List<String> extraDependencies, List<String> sources, boolean isJavaLibrary,
            boolean explicitJavaTestDeps) {

        String aspectJsonFilename = targetName + ".bzljavasdk-data.json";

        if (!isJavaLibrary) {
            aspectJsonFilename = targetName + "-test.bzljavasdk-data.json";
        }

        String json = null;
        if (isJavaLibrary) { // this boolean is not enough when we add java_binary, springboot
            json = createAspectJsonForJavaLibraryTarget(packageRelativePath, packageName, targetName, extraDependencies,
                sources);
        } else {
            json = createAspectJsonForJavaTestTarget(packageRelativePath, packageName, targetName, targetName + "Test",
                extraDependencies, sources, explicitJavaTestDeps);
        }

        File aspectJsonFile =
                createJavaAspectFileWithThisJson(bazelOutputBase, packageRelativePath, aspectJsonFilename, json);

        return aspectJsonFile.getAbsolutePath();
    }

    /**
     * Write a particular json file as if generated by the Bazel aspect for Maven jar file found in the Bazel
     * output_base/external directory via maven_install.
     *
     * @return the absolute File path to the file
     */
    public static String createJavaAspectFileForMavenJar(File bazelOutputBase, String bazelLabelForJar,
            String relativePathJar, String relativePathSrcJar) {
        String externalName = "external" + FSPathHelper.UNIX_SLASH + bazelLabelForJar;
        List<String> dependencies = null;
        List<String> sources = null;
        String mainClass = null;
        String label = "@maven//:" + bazelLabelForJar;
        String kind = "java_library";

        String buildFileEscaped = FSPathHelper.osSepsEscaped(externalName + "/maven/BUILD.bazel"); // $SLASH_OK
        String relativePathJarEscaped = FSPathHelper.osSepsEscaped(relativePathJar);
        String relativePathSrcJarEscaped = FSPathHelper.osSepsEscaped(relativePathSrcJar);
        String interfacejar = null;

        String json = createAspectJsonForJavaArtifact(buildFileEscaped, true, dependencies, sources, mainClass, label,
            kind, relativePathJarEscaped, interfacejar, relativePathSrcJarEscaped);
        File aspectJsonFile = createJavaAspectFileWithThisJson(bazelOutputBase,
            FSPathHelper.osSepsEscaped(externalName), "jar.bzljavasdk-data.json", json);

        return aspectJsonFile.getAbsolutePath();
    }

    /**
     * Write a particular json file as if generated by the Bazel aspect for java_import jar file found in a local
     * directory. For example, //projects/apple-api/libs/banana.jar loaded in the BUILD file like this:
     *
     * java_import( name = "libbanana", jars = ["libs/banana.jar",], )
     *
     * @return the absolute File path to the file
     */
    public static String createJavaAspectFileForImportLocalJar(File bazelOutputBase, String packageLabelRelativePath,
            String importDirFSRelativePath, String targetName, String actualJarNameNoSuffix) {
        List<String> dependencies = null;
        List<String> sources = null;
        String mainClass = null;

        String label = "//" + packageLabelRelativePath + BazelLabel.BAZEL_COLON + targetName;
        String kind = "java_import";
        String jar = FSPathHelper.osSepsEscaped(importDirFSRelativePath + "/" + actualJarNameNoSuffix + ".jar"); // $SLASH_OK
        String interfacejar = null;
        String sourcejar = null;
        String buildFile = FSPathHelper.osSepsEscaped(importDirFSRelativePath + "/BUILD");

        String json = createAspectJsonForJavaArtifact(buildFile, false, dependencies, sources, mainClass, label, kind,
            jar, interfacejar, sourcejar);
        File aspectJsonFile = createJavaAspectFileWithThisJson(bazelOutputBase,
            FSPathHelper.osSepsEscaped(importDirFSRelativePath), targetName + ".bzljavasdk-data.json", json);

        return aspectJsonFile.getAbsolutePath();
    }

    /*
     * {
     *   "build_file_artifact_location":"projects/libs/apple/apple-api/BUILD",
     *   "dependencies":["@maven//:org_slf4j_slf4j_api","@maven//:com_google_guava_guava"],
     *   "generated_jars":[],
     *   "jars":[{
     *      "interface_jar":"bazel-out/darwin-fastbuild/bin/projects/libs/apple/apple-api/libapple-api-hjar.jar",
     *      "jar":"bazel-out/darwin-fastbuild/bin/projects/libs/apple/apple-api/libapple-api.jar",
     *      "source_jar":"bazel-out/darwin-fastbuild/bin/projects/libs/apple/apple-api/libapple-api-src.jar"
     *   }],
     *   "kind":"java_library",
     *   "label":"//projects/libs/apple/apple-api:apple-api",
     *   "sources":["projects/libs/apple/apple-api/src/main/java/demo/apple/api/AppleOrchard.java",
     *   "projects/libs/apple/apple-api/src/main/java/demo/apple/api/AppleW.java"]
     * }
     */
    public static String createAspectJsonForJavaLibraryTarget(String packageRelativePath, String packageName,
            String targetName, List<String> extraDependencies, List<String> sources) {
        List<String> dependencies = new ArrayList<>();
        dependencies.add("@maven//:org_slf4j_slf4j_api");
        dependencies.add("@maven//:com_google_guava_guava");
        if (extraDependencies != null) {
            dependencies.addAll(extraDependencies);
        }
        String mainClass = null;
        String label = "//" + packageRelativePath + ":" + targetName;
        String jar = FSPathHelper
                .osSepsEscaped("bazel-out/darwin-fastbuild/bin/" + packageRelativePath + "/lib" + targetName + ".jar");
        String interfacejar = FSPathHelper.osSepsEscaped(
            "bazel-out/darwin-fastbuild/bin/" + packageRelativePath + "/lib" + targetName + "-hjar.jar");
        String sourcejar = FSPathHelper.osSepsEscaped(
            "bazel-out/darwin-fastbuild/bin/" + packageRelativePath + "/lib" + targetName + "-src.jar");
        String buildFile = FSPathHelper.osSepsEscaped(packageRelativePath + "/BUILD");

        return createAspectJsonForJavaArtifact(buildFile, false, dependencies, sources, mainClass, label,
            "java_library", jar, interfacejar, sourcejar);
    }

    /* {
     *  "build_file_artifact_location": "projects/libs/apple/apple-api/BUILD",
     *  "dependencies":["//projects/libs/apple/apple-api:apple-api","@junit_junit//jar:jar","@org_hamcrest_hamcrest_core//jar:jar"],
     *  "generated_jars":[],
     *  "jars":[{
     *    "jar":"bazel-out/darwin-fastbuild/bin/projects/libs/apple/apple-api/apple-api-test2.jar",
     *    "source_jar":"bazel-out/darwin-fastbuild/bin/projects/libs/apple/apple-api/apple-api-test2-src.jar"
     *  }],
     *  "kind":"java_test",
     *  "label":"//projects/libs/apple/apple-api:apple-api-test2",
     *  "main_class":"",
     *  "sources":["projects/libs/apple/apple-api/src/test/java/demo/apple/api/AppleTest2.java"]
     * }
     */
    public static String createAspectJsonForJavaTestTarget(String packageRelativePath, String packageName,
            String targetName,
            String testTargetName, List<String> extraDependencies, List<String> sources, boolean explicitJavaTestDeps) {
        List<String> dependencies = new ArrayList<>();
        if (explicitJavaTestDeps) {
            // See ImplicitDependencyHelper.java
            // if the workspace is configured to disallow implicit test deps, the BUILD file (and thus the aspect) needs to
            // explicitly include junit/hamcrest
            dependencies.add("@maven//:junit_junit"); // $SLASH_OK bazel label
            dependencies.add("@maven//:org_hamcrest_hamcrest_core"); // $SLASH_OK: bazel label
        }
        String targetLabel = packageRelativePath + ":" + targetName;
        dependencies.add(targetLabel);
        if (extraDependencies != null) {
            dependencies.addAll(extraDependencies);
        }
        String mainClass = null;
        String label = "//" + packageRelativePath + ":" + testTargetName;
        String jar = FSPathHelper.osSepsEscaped(
            "bazel-out/darwin-fastbuild/bin/" + packageRelativePath + "/lib" + testTargetName + ".jar");
        String interfacejar = null;
        String sourcejar = FSPathHelper.osSepsEscaped(
            "bazel-out/darwin-fastbuild/bin/" + packageRelativePath + "/lib" + testTargetName + "-src.jar");
        String buildFile = FSPathHelper.osSepsEscaped(packageRelativePath + "/BUILD");

        return createAspectJsonForJavaArtifact(buildFile, false, dependencies, sources, mainClass, label, "java_test",
            jar,
            interfacejar, sourcejar);

    }

    private static String createAspectJsonForJavaArtifact(String buildFileLocation, boolean isExternalJar,
            List<String> dependencies, List<String> sources, String mainClass, String label, String kind, String jar,
            String interfacejar, String sourcejar) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // build_file_artifact_location
        sb.append("  \"build_file_artifact_location\":\""); // $SLASH_OK: escape char
        sb.append(buildFileLocation);
        sb.append("\",\n"); // $SLASH_OK: line continue

        // is_external  (maven_install jars are external, java_import, java_library, java_test jars are not)
        sb.append("  \"is_external\":\""); // $SLASH_OK: escape char
        sb.append(isExternalJar);
        sb.append("\",\n"); // $SLASH_OK: line continue

        // root_execution_path_fragment (normally "" for external/import jars, something like "bazel-out/darwin-fastbuild/bin"
        // for internally built java_library, java_test jars
        // TODO why arent we modeling this property correctly yet?
        sb.append("  \"root_execution_path_fragment\":\"\",\n"); // $SLASH_OK: escape char

        // dependencies
        sb.append("  \"deps\":[\n    "); // $SLASH_OK: escape char
        if (dependencies != null) {
            for (String dep : dependencies) {
                sb.append("      {\n");
                sb.append("        \"target\": {\n");
                sb.append("          \"label\": \"");
                sb.append(dep);
                sb.append("\"\n");
                sb.append("        }\n");
                sb.append("      },\n");
            }
        }
        sb.append("  ],\n");

        // start ide info
        sb.append("  \"java_ide_info\": {\n");

        // generated_jars
        sb.append("    \"generated_jars\":[],\n"); // $SLASH_OK: escape char

        // jars
        sb.append("    \"jars\":[\n     {\n"); // $SLASH_OK: escape char
        {
            // jar
            if (jar != null) {
                sb.append("      \"jar\": {\n"); // $SLASH_OK: escape char
                sb.append("        \"relative_path\": \""); // $SLASH_OK: escape char
                sb.append(jar);
                sb.append("\"\n"); // $SLASH_OK: line continue
                sb.append("      },\n"); // $SLASH_OK: escape char
            }

            // interfacejar
            if (interfacejar != null) {
                sb.append("      \"interface_jar\": {\n"); // $SLASH_OK: escape char
                sb.append("        \"relative_path\": \""); // $SLASH_OK: escape char
                sb.append(interfacejar);
                sb.append("\"\n"); // $SLASH_OK: line continue
                sb.append("      },\n"); // $SLASH_OK: escape char
            }

            // source_jar
            if (sourcejar != null) {
                sb.append("      \"source_jar\": {\n"); // $SLASH_OK: escape char
                sb.append("        \"relative_path\": \""); // $SLASH_OK: escape char
                sb.append(sourcejar);
                sb.append("\"\n"); // $SLASH_OK: line continue
                sb.append("      },\n"); // $SLASH_OK: escape char
            }
        }
        sb.append("     }\n    ],\n");

        // main_class
        if (mainClass != null) {
            sb.append("    \"main_class\":\""); // $SLASH_OK: escape char
            sb.append(mainClass);
            sb.append("\",\n"); // $SLASH_OK: line continue
        }

        // sources
        sb.append("    \"sources\":[\n"); // $SLASH_OK: escape char
        if (sources != null) {
            for (String source : sources) {
                sb.append("        { \"relative_path\": \""); // $SLASH_OK: escape char
                source = FSPathHelper.osSepsEscaped(source); // Windows paths need to be json escaped
                sb.append(source);
                sb.append("\"},\n"); // $SLASH_OK: line continue
            }
        }
        sb.append("  ],\n");

        // end ide info
        sb.append("  },\n");

        // kind
        sb.append("  \"kind_string\":\""); // $SLASH_OK: escape char
        sb.append(kind);
        sb.append("\",\n"); // $SLASH_OK: line continue

        // label
        sb.append("  \"key\": {\n"); // $SLASH_OK: escape char
        sb.append("    \"label\":\""); // $SLASH_OK: escape char
        sb.append(label);
        sb.append("\"\n  },\n"); // $SLASH_OK: line continue

        sb.append("}\n");
        return sb.toString();
    }

    private static File createJavaAspectFileWithThisJson(File bazelOutputBase, String path, String aspectJsonFilename,
            String json) {
        //System.out.println(aspectJsonFilename);
        //System.out.println(json);
        File packageBinDir = new File(bazelOutputBase, path);
        packageBinDir.mkdirs();
        File aspectJsonFile = new File(packageBinDir, aspectJsonFilename);

        try (PrintStream out = new PrintStream(new FileOutputStream(aspectJsonFile))) {
            out.print(json);
        } catch (Exception anyE) {
            anyE.printStackTrace();
        }
        return aspectJsonFile;
    }

}
