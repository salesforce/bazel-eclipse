package com.salesforce.bazel.eclipse.utils;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.SystemUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.project.BazelProject;

public class EclipseProjectSettingsUtils {
    private final static LogHelper logger = LogHelper.log(EclipseProjectSettingsUtils.class);

    public static void setProjectReferences(ResourceHelper resourceHelper, BazelProject project,
            List<BazelProject> references) {
        IProject thisEclipseProject = (IProject) project.getProjectImpl();
        IProjectDescription projectDescription = resourceHelper.getProjectDescription(thisEclipseProject);

        IProject[] existingEclipseRefList = projectDescription.getReferencedProjects();
        IProject[] updatedEclipseRefList = new IProject[references.size()];
        int i = 0;
        for (BazelProject ref : references) {
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

    public static BazelProject getOwningProjectForSourcePath(BazelWorkspace bazelWorkspace, String sourcePath,
            Collection<BazelProject> bazelProjects, ResourceHelper resourceHelper, JavaCoreHelper javaCoreHelper) {
        String canonicalSourcePathString =
                FSPathHelper.getCanonicalPathStringSafely(bazelWorkspace.getBazelWorkspaceRootDirectory())
                        + File.separator + sourcePath;
        Path canonicalSourcePath = new File(canonicalSourcePathString).toPath();

        for (BazelProject candidateProject : bazelProjects) {
            if (checkProject(candidateProject, resourceHelper, javaCoreHelper, canonicalSourcePathString,
                canonicalSourcePath)) {
                return candidateProject;
            }
        }
        return null;
    }

    private static boolean checkProject(BazelProject candidateProject, ResourceHelper resourceHelper,
            JavaCoreHelper javaCoreHelper, String canonicalSourcePathString, Path canonicalSourcePath) {
        IProject iProject = (IProject) candidateProject.getProjectImpl();
        IJavaProject jProject = javaCoreHelper.getJavaProjectForProject(iProject);
        IClasspathEntry[] classpathEntries = javaCoreHelper.getRawClasspath(jProject);

        if (classpathEntries == null) {
            logger.error("No classpath entries found for project [" + jProject.getElementName() + "]");
            return false;
        }

        for (IClasspathEntry entry : classpathEntries) {
            if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
                continue;
            }
            IResource res = resourceHelper.findMemberInWorkspace(entry.getPath());
            if (res == null) {
                continue;
            }
            if (checkClasspathEntry(entry, res.getLocation(), canonicalSourcePathString, canonicalSourcePath)) {
                return true;
            }
        }

        return false;
    }

    private static boolean checkClasspathEntry(IClasspathEntry entry, IPath projectLocation,
            String canonicalSourcePathString, Path canonicalSourcePath) {
        if ((projectLocation != null) && !projectLocation.isEmpty()) {
            String canonicalProjectRoot = FSPathHelper.getCanonicalPathStringSafely(projectLocation.toOSString());

            String osDependentProjectRoot =
                    SystemUtils.IS_OS_WINDOWS ? canonicalProjectRoot.replaceAll("\\\\", "/") : canonicalProjectRoot;
            String osDependentSourcePath = SystemUtils.IS_OS_WINDOWS ? canonicalSourcePathString.replaceAll("\\\\", "/")
                    : canonicalSourcePathString;

            if (osDependentSourcePath.startsWith(osDependentProjectRoot)) {
                IPath[] inclusionPatterns = entry.getInclusionPatterns();
                IPath[] exclusionPatterns = entry.getExclusionPatterns();
                if (!matchPatterns(canonicalSourcePath, exclusionPatterns)) {
                    if ((inclusionPatterns == null) || (inclusionPatterns.length == 0)
                            || matchPatterns(canonicalSourcePath, inclusionPatterns)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Globby match of file system patterns for a given path. If the path matches any of the patterns, this method
     * returns true.
     */
    private static boolean matchPatterns(Path path, IPath[] patterns) {
        if (patterns != null) {
            for (IPath p : patterns) {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + p.toOSString());
                if (matcher.matches(path)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the arrays of projects contain different projects
     */
    private static boolean areDifferent(IProject[] list1, IProject[] list2) {
        if (list1.length != list2.length) {
            return true;
        }
        for (IProject p1 : list1) {
            boolean found = false;
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
}
