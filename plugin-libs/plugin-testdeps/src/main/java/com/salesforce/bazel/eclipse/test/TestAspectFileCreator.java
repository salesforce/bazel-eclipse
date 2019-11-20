package com.salesforce.bazel.eclipse.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.List;

public class TestAspectFileCreator {

    static String createJavaAspectFile(File repoBinDir, String packageRelativePath, String packageName, String targetName, String extraDependencies,
            List<String> sources, boolean isJavaLibrary) {

        String aspectJsonFilename = targetName+".bzleclipse-build.json";
        
        if (!isJavaLibrary) {
            aspectJsonFilename = targetName+"-test.bzleclipse-build.json";
        }
        
        String json = null;
        if (isJavaLibrary) { // this boolean is not enough when we add java_binary, springboot
            json = createAspectJsonForJavaLibraryTarget(packageRelativePath, packageName, targetName, extraDependencies, sources);
        } else {
            json = createAspectJsonForJavaTestTarget(packageRelativePath, packageName, targetName, targetName+"-test", extraDependencies, sources);
        }
        
        File aspectJsonFile = createJavaAspectFileWithThisJson(repoBinDir, packageRelativePath, aspectJsonFilename, json);
        
        return aspectJsonFile.getAbsolutePath();
    }
    
    static String createJavaAspectFileForMavenJar(File repoBinDir, String mavenJarName, String actualJarNameNoSuffix) {
        String externalName = "external/"+mavenJarName;
        String dependencies = null;
        List<String> sources = null;
        String mainClass = null;
        String label = "@"+mavenJarName+"//jar:jar";
        String kind = "java_import";
        String jar = externalName + "/jar/" + actualJarNameNoSuffix + ".jar";
        String interfacejar = null;
        String sourcejar = externalName + "/jar/" + actualJarNameNoSuffix + "-sources.jar";
                
        String json = createAspectJsonForJavaArtifact(externalName+"/jar/BUILD.bazel", dependencies, sources, mainClass, label, kind, jar, interfacejar, sourcejar);
        File aspectJsonFile = createJavaAspectFileWithThisJson(repoBinDir, externalName+"/jar", "jar.bzleclipse-build.json", json);
        
        return aspectJsonFile.getAbsolutePath();
    }


    private static File createJavaAspectFileWithThisJson(File repoBinDir, String path, String aspectJsonFilename, String json) {
        File packageBinDir = new File(repoBinDir, path);
        packageBinDir.mkdirs();
        File aspectJsonFile = new File(packageBinDir, aspectJsonFilename);
        
        try (PrintStream out = new PrintStream(new FileOutputStream(aspectJsonFile))) {
            out.print(json);
        } catch (Exception anyE) {
            anyE.printStackTrace();
        }
        return aspectJsonFile;
    }

    /*
     * {
     *   "build_file_artifact_location":"projects/libs/apple/apple-api/BUILD",
     *   "dependencies":["@org_slf4j_slf4j_api//jar:jar","@com_google_guava_guava//jar:jar"],
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
    static String createAspectJsonForJavaLibraryTarget(String packageRelativePath, String packageName, String targetName,
            String extraDependencies, List<String> sources) {
        String dependencies = "\"@org_slf4j_slf4j_api//jar:jar\",\n    \"@com_google_guava_guava//jar:jar\",\n";
        if (extraDependencies != null) {
            dependencies = dependencies + extraDependencies;
        }
        String mainClass = null;
        String label = packageRelativePath +":"+targetName;
        String jar = "bazel-out/darwin-fastbuild/bin/"+packageRelativePath+"/lib"+targetName+".jar";
        String interfacejar = "bazel-out/darwin-fastbuild/bin/"+packageRelativePath+"/lib"+targetName+"-hjar.jar";
        String sourcejar = "bazel-out/darwin-fastbuild/bin/"+packageRelativePath+"/lib"+targetName+"-src.jar";

        return createAspectJsonForJavaArtifact(packageRelativePath+"/BUILD", dependencies, sources, mainClass, label, "java_library", jar, interfacejar, sourcejar);
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
    static String createAspectJsonForJavaTestTarget(String packageRelativePath, String packageName, String targetName, String testTargetName, 
            String extraDependencies, List<String> sources) {
        String dependencies = "\"@junit_junit//jar:jar\",\n    \"org_hamcrest_hamcrest_core//jar:jar\",\n    " +
             "\":"+targetName+"\",\n";
        if (extraDependencies != null) {
            dependencies = dependencies + extraDependencies;
        }
        String mainClass = null;
        String label = packageRelativePath +":"+testTargetName;
        String jar = "bazel-out/darwin-fastbuild/bin/"+packageRelativePath+"/lib"+testTargetName+".jar";
        String interfacejar = null;
        String sourcejar = "bazel-out/darwin-fastbuild/bin/"+packageRelativePath+"/lib"+testTargetName+"-src.jar";
        
        return createAspectJsonForJavaArtifact(packageRelativePath+"/BUILD", dependencies, sources, mainClass, label, "java_library", jar, interfacejar, sourcejar);
        
    }   
        


    private static String createAspectJsonForJavaArtifact(String buildFileLocation, String dependencies, List<String> sources, 
            String mainClass, String label, String kind, String jar, String interfacejar, String sourcejar) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // build_file_artifact_location
        sb.append("  \"build_file_artifact_location\":\"");
        sb.append(buildFileLocation);
        sb.append("\",\n");
        
        // dependencies
        sb.append("  \"dependencies\":[\n    ");
        if (dependencies != null) {
            sb.append(dependencies);
        }
        sb.append("  ],\n");
        
        // generated_jars
        sb.append("  \"generated_jars\":[],\n");
        
        // jars
        sb.append("  \"jars\":[ {\n");
        {
            // jar
            if (jar != null) {
                sb.append("    \"jar\":\"");
                sb.append(jar);
                sb.append("\",\n");
            }

            // interfacejar
            if (interfacejar != null) {
                sb.append("    \"interface_jar\":\"");
                sb.append(interfacejar);
                sb.append("\",\n");
            }
            
            // source_jar
            if (sourcejar != null) {
                sb.append("    \"source_jar\":\"");
                sb.append(sourcejar);
                sb.append("\",\n");
            }
        }
        sb.append("  }  ],\n");
        
        // kind
        sb.append("  \"kind\":\"");
        sb.append(kind);
        sb.append("\",\n");

        // label
        sb.append("  \"label\":\"");
        sb.append(label);
        sb.append("\",\n");
        
        // main_class
        if (mainClass != null) {
            sb.append("  \"main_class\":\"");
            sb.append(mainClass);
            sb.append("\",\n");
        }

        // sources
        sb.append("  \"sources\":[\n");
        if (sources != null) {
            for (String source : sources) {
                sb.append("    \"");
                sb.append(source);
                sb.append("\",\n");
            }
        }
        sb.append("  ],\n");
        
        sb.append("}\n");
        return sb.toString();
    }

}
