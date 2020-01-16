package com.salesforce.bazel.eclipse.classpath;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.model.AspectPackageInfo;
import com.salesforce.bazel.eclipse.model.BazelWorkspace;

/**
 * Bazel generally requires BUILD file authors to list all dependencies explicitly.
 * However, there are a few legacy cases in which dependencies are implied.
 * For example, java_test implicitly brings in junit and hamcrest libraries.
 * <p>
 * This is unfortunate because external tools that need to construct the dependency
 * graph (ahem, that's us, for JDT) we need to know to append the implicit dependencies
 * to the explicit ones identified by the Aspect.
 * <p>
 * This is a helper class for computing implicit dependencies.
 * See https://github.com/salesforce/bazel-eclipse/issues/43 for details and design
 * considerations for this class.
 * <p>
 * This code is isolated from the classpath container code because this is somewhat of a
 * hack and it is nice to have it isolated.
 */
public class ImplicitDependencyHelper {

    Set<IClasspathEntry> computeImplicitDependencies(IProject eclipseIProject, BazelWorkspace bazelWorkspace, 
            AspectPackageInfo packageInfo) {
        Set<IClasspathEntry> deps = null;
        
        String ruleKind = packageInfo.getKind();        
        if ("java_test".equals(ruleKind)) {
            deps = computeImplicitJavaTestDependencies(eclipseIProject, bazelWorkspace, packageInfo);
        } else {
            deps = new TreeSet<>();
        }
        return deps;
    }
    
    private Set<IClasspathEntry> computeImplicitJavaTestDependencies(IProject eclipseIProject, BazelWorkspace bazelWorkspace,
            AspectPackageInfo packageInfo) {
        Set<IClasspathEntry> deps = new HashSet<>();
        
        // TODO ideally we would only do this if --explicit_java_test_deps=false, but how to compute that?
        
        // HAMCREST and JUNIT
        // These implicit deps come from the test runner.
        // Ultimately we need to get this jar onto the classpath:
        //     bazel-bin/external/bazel_tools/tools/jdk/_ijar/TestRunner/external/remote_java_tools_darwin/java_tools/Runner_deploy-ijar.jar
        // which comes in from the transitive graph (not sure how the toolchain points to the TestRunner though):
        // java_test => @bazel_tools//tools/jdk:current_java_toolchain => @remote_java_tools_darwin//:toolchain  ?=> TestRunner 
        String filePathForRunnerJar = computeFilePathForRunnerJar(bazelWorkspace, packageInfo);
        if (filePathForRunnerJar != null) {
            // now manufacture the classpath entry
            IPath runnerJarPath = org.eclipse.core.runtime.Path.fromOSString(filePathForRunnerJar);
            IClasspathEntry runnerJarEntry = BazelPluginActivator.getJavaCoreHelper().newLibraryEntry(runnerJarPath, null, null);
            deps.add(runnerJarEntry);
        }        
        return deps;
    }
    
    String computeFilePathForRunnerJar(BazelWorkspace bazelWorkspace, AspectPackageInfo packageInfo) {
        // The IJ plugin gets this path somehow from query/aspect but we are going to wedge it in via path here since we need to
        // overhaul our query/aspect in the near future TODO stop using file system hacking for implicit deps

        File bazelBinDir = bazelWorkspace.getBazelBinDirectory();
        File testRunnerDir = new File(bazelBinDir, "external/bazel_tools/tools/jdk/_ijar/TestRunner");
        if (!testRunnerDir.exists()) {
            BazelPluginActivator.error("Could not add implicit test deps to target ["+packageInfo.getLabel()+
                "], directory ["+testRunnerDir.getAbsolutePath()+"] does not exist.");
            return null;
        }
        File javaToolsDir = new File(testRunnerDir, "external/remote_java_tools_"+bazelWorkspace.getOperatingSystemFoldername()+"/java_tools");
        if (!javaToolsDir.exists()) {
            BazelPluginActivator.error("Could not add implicit test deps to target ["+packageInfo.getLabel()+
                "], directory ["+javaToolsDir.getAbsolutePath()+"] does not exist.");
            return null;
        }
        File runnerJar = new File(javaToolsDir, "Runner_deploy-ijar.jar");
        if (!runnerJar.exists()) {
            BazelPluginActivator.error("Could not add implicit test deps to target ["+packageInfo.getLabel()+
                "], test runner jar ["+runnerJar.getAbsolutePath()+"] does not exist.");
            return null;
        }
        return runnerJar.getAbsolutePath();
    }
}
