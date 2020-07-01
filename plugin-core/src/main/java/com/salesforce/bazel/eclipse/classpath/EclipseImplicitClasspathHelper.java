package com.salesforce.bazel.eclipse.classpath;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.sdk.lang.jvm.ImplicitClasspathHelper;
import com.salesforce.bazel.sdk.lang.jvm.JvmClasspathEntry;
import com.salesforce.bazel.sdk.model.AspectPackageInfo;
import com.salesforce.bazel.sdk.model.BazelWorkspace;

/**
 * Bazel generally requires BUILD file authors to list all dependencies explicitly.
 * However, there are a few legacy cases in which dependencies are implied.
 * For example, java_test implicitly brings in junit, hamcrest, javax.annotation libraries.
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
public class EclipseImplicitClasspathHelper extends ImplicitClasspathHelper {

    Set<IClasspathEntry> computeImplicitDependencies(IProject eclipseIProject, BazelWorkspace bazelWorkspace, 
            AspectPackageInfo packageInfo) throws IOException {
        Set<IClasspathEntry> deps = new HashSet<>();
        Set<JvmClasspathEntry> generic_deps = new HashSet<>();
        
        generic_deps = this.computeImplicitDependencies(bazelWorkspace, packageInfo);
        if (generic_deps.size() > 0) {
        	// convert the generic classpath entries into Eclipse classpath entries
        	for (JvmClasspathEntry generic_dep : generic_deps) {
                // now manufacture the classpath entry
                IPath runnerJarPath = org.eclipse.core.runtime.Path.fromOSString(generic_dep.pathToJar);
                IPath sourceAttachmentPath = null;
                IPath sourceAttachmentRootPath = null;
                boolean isTestLib = generic_dep.isTestJar;
                IClasspathEntry runnerJarEntry = BazelPluginActivator.getJavaCoreHelper().newLibraryEntry(runnerJarPath, sourceAttachmentPath, 
                    sourceAttachmentRootPath, isTestLib);
                deps.add(runnerJarEntry);
        	}
        }
        return deps;
    }
}
