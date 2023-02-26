package com.salesforce.bazel.eclipse.utils;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.project.BazelProjectOld;

public class EclipseProjectSettingsUtils {
    private static Logger LOG = LoggerFactory.getLogger(EclipseProjectSettingsUtils.class);

    /**
     * Returns true if the arrays of projects contain different projects
     */
    private static boolean areDifferent(IProject[] list1, IProject[] list2) {
        if (list1.length != list2.length) {
            return true;
        }
        for (IProject p1 : list1) {
            var found = false;
            for (IProject p2 : list2) {
                if (p1.getName().equals(p2.getName())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkClasspathEntry(IClasspathEntry entry, IPath projectLocation,
            String canonicalSourcePathString, Path canonicalSourcePath) {
        if ((projectLocation != null) && !projectLocation.isEmpty()) {
            var canonicalProjectRoot = FSPathHelper.getCanonicalPathStringSafely(projectLocation.toOSString());

            var osDependentProjectRoot = new org.eclipse.core.runtime.Path(canonicalProjectRoot).toOSString();
            var osDependentSourcePath = new org.eclipse.core.runtime.Path(canonicalSourcePathString).toOSString();

            if (osDependentSourcePath.startsWith(osDependentProjectRoot)) {
                var inclusionPatterns = entry.getInclusionPatterns();
                var exclusionPatterns = entry.getExclusionPatterns();
                if (!matchPatterns(canonicalSourcePath, exclusionPatterns) && ((inclusionPatterns == null)
                        || (inclusionPatterns.length == 0) || matchPatterns(canonicalSourcePath, inclusionPatterns))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean checkProject(BazelProjectOld candidateProject, ResourceHelper resourceHelper,
            JavaCoreHelper javaCoreHelper, String canonicalSourcePathString, Path canonicalSourcePath) {
        var iProject = (IProject) candidateProject.getProjectImpl();
        var jProject = javaCoreHelper.getJavaProjectForProject(iProject);
        var classpathEntries = javaCoreHelper.getRawClasspath(jProject);

        if (classpathEntries == null) {
            LOG.error("No classpath entries found for project [" + jProject.getElementName() + "]");
            return false;
        }

        for (IClasspathEntry entry : classpathEntries) {
            if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
                continue;
            }
            var res = resourceHelper.findMemberInWorkspace(entry.getPath());
            if (res == null) {
                continue;
            }
            if (checkClasspathEntry(entry, res.getLocation(), canonicalSourcePathString, canonicalSourcePath)) {
                return true;
            }
        }

        return false;
    }

    public static BazelProjectOld getOwningProjectForSourcePath(BazelWorkspace bazelWorkspace, String sourcePath,
            Collection<BazelProjectOld> bazelProjects, ResourceHelper resourceHelper, JavaCoreHelper javaCoreHelper) {
        var canonicalSourcePathString =
                FSPathHelper.getCanonicalPathStringSafely(bazelWorkspace.getBazelWorkspaceRootDirectory())
                        + File.separator + sourcePath;
        var canonicalSourcePath = new File(canonicalSourcePathString).toPath();

        for (BazelProjectOld candidateProject : bazelProjects) {
            if (checkProject(candidateProject, resourceHelper, javaCoreHelper, canonicalSourcePathString,
                canonicalSourcePath)) {
                return candidateProject;
            }
        }
        return null;
    }

    /**
     * Globby match of file system patterns for a given path. If the path matches any of the patterns, this method
     * returns true.
     */
    private static boolean matchPatterns(Path path, IPath[] patterns) {
        if (patterns != null) {
            for (IPath p : patterns) {
                var matcher = FileSystems.getDefault().getPathMatcher("glob:" + p.toOSString());
                if (matcher.matches(path)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void setProjectReferences(ResourceHelper resourceHelper, BazelProjectOld project,
            List<BazelProjectOld> references) {
        var thisEclipseProject = (IProject) project.getProjectImpl();
        var projectDescription = resourceHelper.getProjectDescription(thisEclipseProject);

        var existingEclipseRefList = projectDescription.getReferencedProjects();
        var updatedEclipseRefList = new IProject[references.size()];
        var i = 0;
        for (BazelProjectOld ref : references) {
            updatedEclipseRefList[i] = (IProject) ref.getProjectImpl();
            i++;
        }

        // setProjectDescription requires a lock and should cause a rebuild on the project so only do it if necessary
        if (!areDifferent(existingEclipseRefList, updatedEclipseRefList)) {
            return;
        }

        projectDescription.setReferencedProjects(updatedEclipseRefList);
        resourceHelper.setProjectDescription(thisEclipseProject, projectDescription);
    }
}
