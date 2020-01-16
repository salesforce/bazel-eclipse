package com.salesforce.bazel.eclipse.classpath;

import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IClasspathEntry;

import com.salesforce.bazel.eclipse.model.AspectPackageInfo;

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
 *
 */
public class ImplicitDependencyHelper {

    Set<IClasspathEntry> computeImplicitDependencies(IProject eclipseIProject, AspectPackageInfo packageInfo) {
        Set<IClasspathEntry> deps = null;
        
        String ruleKind = packageInfo.getKind();        
        if ("java_test".equals(ruleKind)) {
            deps = computeImplicitJavaTestDependencies(eclipseIProject, packageInfo);
        } else {
            deps = new TreeSet<>();
        }
        return deps;
    }
    
    private Set<IClasspathEntry> computeImplicitJavaTestDependencies(IProject eclipseIProject, AspectPackageInfo packageInfo) {
        Set<IClasspathEntry> deps = new TreeSet<>();
        
        // TODO ideally we would only do this if --explicit_java_test_deps=false, but how to compute that?
        
        // Ultimately we need to get this jar onto the classpath
        // bazel-bin/external/bazel_tools/tools/jdk/_ijar/TestRunner/external/remote_java_tools_darwin/java_tools/Runner_deploy-ijar.jar
        // which comes in from the transitive graph (not sure how the toolchain points to the TestRunner though):
        // java_test => @bazel_tools//tools/jdk:current_java_toolchain => @remote_java_tools_darwin//:toolchain  ?=> TestRunner 
        
        // TODO need to implement the dependency computation HERE for issue 43
        
        return deps;
    }    
}
