/*-
 *
 */
package com.salesforce.bazel.eclipse.core.classpath;

import static java.lang.String.format;
import static java.nio.file.Files.isRegularFile;
import static java.util.Arrays.stream;

import java.util.Collection;
import java.util.LinkedHashSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.launching.RuntimeClasspathEntry;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.IRuntimeClasspathEntryResolver;
import org.eclipse.jdt.launching.IRuntimeClasspathEntryResolver2;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.BazelCorePlugin;
import com.salesforce.bazel.eclipse.core.model.BazelProject;

@SuppressWarnings("restriction")
public class BazelClasspathContainerRuntimeResolver
        implements IRuntimeClasspathEntryResolver, IRuntimeClasspathEntryResolver2 {

    private static final Logger LOG = LoggerFactory.getLogger(BazelClasspathContainerRuntimeResolver.class);

    private static String extractRealJarName(String jarName) {
        // copied (and adapted) from BlazeJavaWorkspaceImporter
        if (jarName.endsWith("-hjar.jar")) {
            return jarName.substring(0, jarName.length() - "-hjar.jar".length()) + ".jar";
        }
        if (jarName.endsWith("-ijar.jar")) {
            return jarName.substring(0, jarName.length() - "-ijar.jar".length()) + ".jar";
        }
        return jarName;
    }

    ISchedulingRule getBuildRule() {
        return ResourcesPlugin.getWorkspace().getRuleFactory().buildRule();
    }

    BazelClasspathManager getClasspathManager() {
        return BazelCorePlugin.getInstance().getBazelModelManager().getClasspathManager();
    }

    @Override
    public boolean isVMInstallReference(IClasspathEntry entry) {
        return false;
    }

    private void populateWithRealJar(Collection<IRuntimeClasspathEntry> resolvedClasspath, IClasspathEntry e) {
        var jarPath = e.getPath();
        var jarName = extractRealJarName(jarPath.lastSegment());
        if (!jarName.equals(jarPath.lastSegment())) {
            var realJarPath = jarPath.removeLastSegments(1).append(jarName);

            // ensure it exists
            if (!isRegularFile(jarPath.toPath())) {
                LOG.warn("Dropped ijar from runtime classpath: {}", jarPath);
                return;
            }

            // replace entry with new jar
            LOG.debug("Replacing ijar '{}' on classpath with real jar '{}", jarPath.lastSegment(), realJarPath);
            e = JavaCore.newProjectEntry(realJarPath);
        }

        resolvedClasspath.add(new RuntimeClasspathEntry(e));
    }

    /**
     * Resolves a project classpath reference into all possible output folders and transitives and adds it to the
     * resolved classpath.
     *
     * @param resolvedClasspath
     *            the resolved classpath
     * @param sourceProject
     *            the project reference
     * @throws CoreException
     *             in case of problems
     */
    private void populateWithResolvedProject(Collection<IRuntimeClasspathEntry> resolvedClasspath,
            IProject sourceProject) throws CoreException {
        var javaProject = JavaCore.create(sourceProject);

        // never exclude test code because we use it for runtime dependencies as well
        final var excludeTestCode = false;

        // get the full transitive closure of the project
        var unresolvedRuntimeClasspath = JavaRuntime.computeUnresolvedRuntimeClasspath(javaProject, excludeTestCode);
        for (IRuntimeClasspathEntry unresolvedEntry : unresolvedRuntimeClasspath) {
            // resolve and add
            stream(JavaRuntime.resolveRuntimeClasspathEntry(unresolvedEntry, javaProject, excludeTestCode))
                    .forEach(resolvedClasspath::add);
        }
    }

    private void populateWithSavedContainer(IJavaProject project, Collection<IRuntimeClasspathEntry> resolvedClasspath)
            throws CoreException {
        var bazelContainer = getClasspathManager().getSavedContainer(project.getProject());
        if (bazelContainer != null) {
            var workspaceRoot = project.getResource().getWorkspace().getRoot();
            var entries = bazelContainer.getClasspathEntries();
            for (IClasspathEntry e : entries) {
                switch (e.getEntryKind()) {
                    case IClasspathEntry.CPE_PROJECT: {
                        // projects need to be resolved properly so we have all the output folders and exported jars on the classpath
                        var sourceProject = workspaceRoot.getProject(e.getPath().segment(0));
                        populateWithResolvedProject(resolvedClasspath, sourceProject);
                        break;
                    }
                    case IClasspathEntry.CPE_LIBRARY: {
                        // we can rely on the assumption that this is an absolute path pointing into Bazel's execroot
                        // but we have to exclude ijars from runtime
                        populateWithRealJar(resolvedClasspath, e);
                        break;
                    }
                    default:
                        throw new CoreException(
                                Status.error(
                                    format(
                                        "Unexpected classpath entry in the persisted Bazel container. Try refreshing the classpath or report as bug. %s",
                                        e)));
                }
            }
        }
    }

    @Override
    public IRuntimeClasspathEntry[] resolveRuntimeClasspathEntry(IRuntimeClasspathEntry entry, IJavaProject project)
            throws CoreException {
        if ((entry == null) || (entry.getJavaProject() == null)) {
            return new IRuntimeClasspathEntry[0];
        }

        if ((entry.getType() != IRuntimeClasspathEntry.CONTAINER)
                || !BazelClasspathHelpers.isBazelClasspathContainer(entry.getPath())) {
            return new IRuntimeClasspathEntry[0];
        }

        Collection<IRuntimeClasspathEntry> result = new LinkedHashSet<>(); // insertion order is important but avoid duplicates

        // try the saved container
        // this is usually ok because we no longer use the ijars on project classpaths
        // the saved container also contains all runtime dependencies by default
        populateWithSavedContainer(project, result);

        var bazelProject = BazelCore.create(project.getProject());
        if (bazelProject.isWorkspaceProject()) {
            // when debugging the workspace project we include all target/package projects automatically
            // this is odd because the projects should cause cyclic dependencies
            // however it is convenient with source code lookups for missing dependencies
            var bazelProjects = bazelProject.getBazelWorkspace().getBazelProjects();
            for (BazelProject sourceProject : bazelProjects) {
                if (!sourceProject.isWorkspaceProject()) {
                    populateWithResolvedProject(result, sourceProject.getProject());
                }
            }
        }

        return result.toArray(new IRuntimeClasspathEntry[result.size()]);
    }

    @Override
    public IRuntimeClasspathEntry[] resolveRuntimeClasspathEntry(IRuntimeClasspathEntry entry,
            ILaunchConfiguration configuration) throws CoreException {
        if ((entry == null) || (entry.getJavaProject() == null)) {
            return new IRuntimeClasspathEntry[0];
        }

        return resolveRuntimeClasspathEntry(entry, entry.getJavaProject());
    }

    @Override
    public IVMInstall resolveVMInstall(IClasspathEntry entry) throws CoreException {
        return null;
    }

}
