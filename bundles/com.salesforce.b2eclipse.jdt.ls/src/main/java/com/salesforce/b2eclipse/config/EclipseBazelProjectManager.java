package com.salesforce.b2eclipse.config;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;

import org.apache.commons.lang3.SystemUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import com.salesforce.bazel.eclipse.config.BaseEclipseBazelProjectManager;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.project.BazelProject;

public class EclipseBazelProjectManager extends BaseEclipseBazelProjectManager {

    private final LogHelper logger = LogHelper.log(EclipseBazelProjectManager.class);

    @Override
    public BazelProject getOwningProjectForSourcePath(BazelWorkspace bazelWorkspace, String sourcePath) {
        Collection<BazelProject> bazelProjects = getAllProjects();

        String canonicalSourcePathString =
                FSPathHelper.getCanonicalPathStringSafely(bazelWorkspace.getBazelWorkspaceRootDirectory())
                        + File.separator + sourcePath;
        Path canonicalSourcePath = new File(canonicalSourcePathString).toPath();

        for (BazelProject candidateProject : bazelProjects) {
            IProject iProject = (IProject) candidateProject.getProjectImpl();
            IJavaProject jProject = javaCoreHelper.getJavaProjectForProject(iProject);
            IClasspathEntry[] classpathEntries = javaCoreHelper.getRawClasspath(jProject);
            if (classpathEntries == null) {
                logger.error("No classpath entries found for project [" + jProject.getElementName() + "]");
                continue;
            }
            for (IClasspathEntry entry : classpathEntries) {
                if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
                    continue;
                }
                IResource res = resourceHelper.findMemberInWorkspace(entry.getPath());
                if (res == null) {
                    continue;
                }
                IPath projectLocation = res.getLocation();
                if ((projectLocation != null) && !projectLocation.isEmpty()) {
                    String canonicalProjectRoot =
                            FSPathHelper.getCanonicalPathStringSafely(projectLocation.toOSString());

                    String osDependentProjectRoot = SystemUtils.IS_OS_WINDOWS
                            ? canonicalProjectRoot.replaceAll("\\\\", "/") : canonicalProjectRoot;
                    String osDependentSourcePath = SystemUtils.IS_OS_WINDOWS
                            ? canonicalSourcePathString.replaceAll("\\\\", "/") : canonicalSourcePathString;

                    if (osDependentSourcePath.startsWith(osDependentProjectRoot)) {
                        IPath[] inclusionPatterns = entry.getInclusionPatterns();
                        IPath[] exclusionPatterns = entry.getExclusionPatterns();
                        if (!matchPatterns(canonicalSourcePath, exclusionPatterns)) {
                            if ((inclusionPatterns == null) || (inclusionPatterns.length == 0)
                                    || matchPatterns(canonicalSourcePath, inclusionPatterns)) {
                                return candidateProject;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Globby match of file system patterns for a given path. If the path matches any of the patterns, this method
     * returns true.
     */
    private boolean matchPatterns(Path path, IPath[] patterns) {
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

}
