package com.salesforce.bazel.sdk.lang.jvm;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import com.salesforce.bazel.sdk.aspect.AspectPackageInfo;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandOptions;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.util.BazelPathHelper;

/**
 * Bazel generally requires BUILD file authors to list all dependencies explicitly.
 * However, there are a few legacy cases in which dependencies are implied.
 * For example, java_test implicitly brings in junit, hamcrest, javax.annotation libraries.
 * <p>
 * This is unfortunate because external tools that need to construct the dependency
 * graph (ahem, that's us) we need to know to append the implicit dependencies
 * to the explicit ones identified by the Aspect.
 * <p>
 * This is a helper class for computing implicit dependencies.
 * See https://github.com/salesforce/bazel-eclipse/issues/43 for details and design
 * considerations for this class.
 * <p>
 * This code is isolated from the classpath container code because this is somewhat of a
 * hack and it is nice to have it isolated. 
 */
public class ImplicitClasspathHelper {

    public Set<JvmClasspathEntry> computeImplicitDependencies(BazelWorkspace bazelWorkspace,
            AspectPackageInfo packageInfo) {
        Set<JvmClasspathEntry> deps = new HashSet<>();

        String ruleKind = packageInfo.getKind();        
        if (!"java_test".equals(ruleKind)) {
            deps = new TreeSet<>();
        }

        // java_test targets do not have implicit deps if .bazelrc has --explicit_java_test_deps=true
        BazelWorkspaceCommandOptions commandOptions = bazelWorkspace.getBazelWorkspaceCommandOptions();
        String explicitDepsOption = commandOptions.getOption("explicit_java_test_deps");
        if ("true".equals(explicitDepsOption)) {
            // the workspace is configured to disallow implicit deps (hooray) so we can bail now 
            return deps;
        }
        
        // HAMCREST, JUNIT, JAVAX.ANNOTATION
        // These implicit deps are leaked into the classpath by the java_test test runner.
        // To faithfully declare the classpath for Eclipse JDT, we ultimately we need to get this jar onto the JDT classpath:
        //     bazel-bin/external/bazel_tools/tools/jdk/_ijar/TestRunner/external/remote_java_tools_darwin/java_tools/Runner_deploy-ijar.jar
        // which comes in from the transitive graph (not sure how the toolchain points to the TestRunner though):
        // java_test => @bazel_tools//tools/jdk:current_java_toolchain => @remote_java_tools_darwin//:toolchain  ?=> TestRunner 
        String filePathForRunnerJar = computeFilePathForRunnerJar(bazelWorkspace, packageInfo);
        if (filePathForRunnerJar != null) {
            // now manufacture the classpath entry
            boolean isTestLib = true;
            JvmClasspathEntry runnerJarEntry = new JvmClasspathEntry(filePathForRunnerJar, isTestLib);
            deps.add(runnerJarEntry);
        }        
        return deps;
    }
    
    String computeFilePathForRunnerJar(BazelWorkspace bazelWorkspace, AspectPackageInfo packageInfo)  {
        // The IJ plugin gets this path somehow from query/aspect but we are going to wedge it in via path here since we need to
        // overhaul our query/aspect in the near future TODO stop using file system hacking for implicit deps
        // Because of the way we are doing this, there is no aspect json file written on disk that we can consume.
        // Just write the path to the file directly.

        File bazelBinDir = bazelWorkspace.getBazelBinDirectory();
        File testRunnerDir = new File(bazelBinDir, "external/bazel_tools/tools/jdk/_ijar/TestRunner");
        
        LogHelper logger = LogHelper.log(this.getClass());
        if (!testRunnerDir.exists()) {
        	logger.error("Could not add implicit test deps to target ["+packageInfo.getLabel()+
                "], directory ["+BazelPathHelper.getCanonicalPathStringSafely(testRunnerDir)+"] does not exist.");
            return null;
        }
        File javaToolsDir = new File(testRunnerDir, "external/remote_java_tools_"+bazelWorkspace.getOperatingSystemFoldername()+"/java_tools");
        if (!javaToolsDir.exists()) {
        	logger.error("Could not add implicit test deps to target ["+packageInfo.getLabel()+
                "], directory ["+BazelPathHelper.getCanonicalPathStringSafely(javaToolsDir)+"] does not exist.");
            return null;
        }
        File runnerJar = new File(javaToolsDir, "Runner_deploy-ijar.jar");
        if (!runnerJar.exists()) {
        	logger.error("Could not add implicit test deps to target ["+packageInfo.getLabel()+
                "], test runner jar ["+BazelPathHelper.getCanonicalPathStringSafely(runnerJar)+"] does not exist.");
            return null;
        }
        return BazelPathHelper.getCanonicalPathStringSafely(runnerJar);
    }
}
