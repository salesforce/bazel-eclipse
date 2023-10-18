/*-
 *
 */
package com.salesforce.bazel.eclipse.core.classpath;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
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

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.BazelCorePlugin;
import com.salesforce.bazel.eclipse.core.model.BazelProject;

@SuppressWarnings("restriction")
public class BazelClasspathContainerRuntimeResolver
        implements IRuntimeClasspathEntryResolver, IRuntimeClasspathEntryResolver2 {

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

    private void populateWithSavedContainer(IJavaProject project, List<IRuntimeClasspathEntry> result)
            throws CoreException {
        var bazelContainer = getClasspathManager().getSavedContainer(project.getProject());
        if (bazelContainer != null) {
            var entries = bazelContainer.getClasspathEntries();
            for (IClasspathEntry e : entries) {
                result.add(new RuntimeClasspathEntry(e));
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

        List<IRuntimeClasspathEntry> result = new ArrayList<>();

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
                    result.add(
                        new RuntimeClasspathEntry(JavaCore.newProjectEntry(sourceProject.getProject().getFullPath())));
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
