/*-
 *
 */
package com.salesforce.bazel.eclipse.core.model.discovery.classpath.util;

import static com.salesforce.bazel.eclipse.core.model.BazelProject.isBazelProject;
import static com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry.newLibraryEntry;
import static com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry.newProjectEntry;
import static com.salesforce.bazel.eclipse.core.util.jar.SourceJarFinder.findSourceJar;
import static java.lang.String.format;

import java.io.IOException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;

import com.google.idea.blaze.base.model.primitives.Label;
import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.ClasspathEntry;
import com.salesforce.bazel.eclipse.core.model.discovery.classpath.libs.LibrariesDiscoveryUtil;
import com.salesforce.bazel.eclipse.core.util.jar.BazelJarFile;

/**
 * A utility for locating the origin of a Java type or package within a Bazel workspace.
 * <p>
 * This utility operates on the resolved classpath of a Bazel workspace and tries to locate an {@link ClasspathInfo
 * origin}. It's useful when locating missing dependencies. However, there are limitations with this approach. Relying
 * on the resolved class path is not consistent. It depends on the workspace being fully built, which is not always the
 * case. Thus, when jars are missing this locator is out of luck.
 * </p>
 */
public class TypeLocator extends LibrariesDiscoveryUtil {

    /**
     * Return value of {@link TypeLocator#findBazelInfo(IType)}.
     */
    public static record ClasspathInfo(Label originLabel, ClasspathEntry classpathEntry) {
    }

    public TypeLocator(BazelWorkspace bazelWorkspace) throws CoreException {
        super(bazelWorkspace);
    }

    /**
     * Given any {@link IPackageFragment} try to find the originating Bazel label as well as a {@link ClasspathEntry}.
     *
     * @param packageFragment
     *            the package fragment to check
     * @return the {@link ClasspathInfo} (maybe <code>null</code>)
     * @throws CoreException
     */
    public ClasspathInfo findBazelInfo(IPackageFragment packageFragment) throws CoreException {
        if (!packageFragment.exists()) {
            return null;
        }

        var root = (IPackageFragmentRoot) packageFragment.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
        if (root == null) {
            return null;
        }

        return findBazelInfo(root);
    }

    /**
     * Given any {@link IPackageFragmentRoot} try to find the originating Bazel label as well as a
     * {@link ClasspathEntry}.
     *
     * @param packageFragmentRoot
     *            the package fragment root to check
     * @return the {@link ClasspathInfo} (maybe <code>null</code>)
     * @throws CoreException
     */
    public ClasspathInfo findBazelInfo(IPackageFragmentRoot packageFragmentRoot) throws CoreException {
        if (!packageFragmentRoot.exists()) {
            return null;
        }

        if (packageFragmentRoot.isArchive()) {
            // extract the target label from the jar
            var jarPath = packageFragmentRoot.getPath();
            return getBazelInfoFromJarFile(jarPath);
        }
        // extract from project
        var javaProject = packageFragmentRoot.getJavaProject();
        if (javaProject != null) {
            return getBazelInfoFromSourceProject(javaProject);
        }

        return null;
    }

    /**
     * Given any {@link IType} try to find the originating Bazel label as well as a {@link ClasspathEntry}.
     *
     * @param type
     *            the type to check
     * @return the {@link ClasspathInfo} (maybe <code>null</code>)
     * @throws CoreException
     */
    public ClasspathInfo findBazelInfo(IType type) throws CoreException {
        if (type.isBinary()) {
            // extract the target label from the jar
            var root = (IPackageFragmentRoot) type.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
            if (root != null) {
                return findBazelInfo(root);
            }
        } else {
            // extract from project
            var javaProject = type.getJavaProject();
            if (javaProject != null) {
                return getBazelInfoFromSourceProject(javaProject);
            }
        }

        return null;
    }

    ClasspathInfo getBazelInfoFromJarFile(IPath jarPath) throws CoreException {
        try (var jarFile = new BazelJarFile(jarPath.toPath())) {
            var targetLabel = jarFile.getTargetLabel();
            if (targetLabel == null) {
                targetLabel = guessJarLabelFromLocation(jarPath.toPath());
            }
            if (targetLabel != null) {
                return new ClasspathInfo(
                        targetLabel,
                        newLibraryEntry(jarPath, findSourceJar(jarPath.toPath()), null, false /* test only */));
            }
        } catch (IOException e) {
            throw new CoreException(Status.error(format("Error reading jar '%s'. %s", jarPath, e.getMessage()), e));
        }
        return null;
    }

    ClasspathInfo getBazelInfoFromSourceProject(IJavaProject javaProject) throws CoreException {
        if (!javaProject.exists() || !isBazelProject(javaProject.getProject())) {
            return null;
        }
        var sourceProject = BazelCore.create(javaProject.getProject());
        try {
            var projectLabel = sourceProject.getOwnerLabel();
            if (projectLabel != null) {
                return new ClasspathInfo(projectLabel.toPrimitive(), newProjectEntry(sourceProject.getProject()));
            }
        } catch (CoreException e) {
            throw new CoreException(
                    Status.error(format("Error reading label of project '%s'. %s", sourceProject, e.getMessage()), e));
        }

        return null;
    }
}
