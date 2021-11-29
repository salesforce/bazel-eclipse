package com.salesforce.b2eclipse.util;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import com.salesforce.b2eclipse.BazelJdtPlugin;
import com.salesforce.bazel.sdk.logging.LogHelper;

public class ClasspathUtils {
    private static final LogHelper LOG = LogHelper.log(MethodHandles.lookup().lookupClass());

    /**
     * Returns the IJavaProject in the current workspace that contains at least one of the specified sources.
     */
    public static IJavaProject getSourceProjectForSourcePaths(List<String> sources) {
        for (String candidate : sources) {
            IJavaProject project = getSourceProjectForSourcePath(candidate);
            if (project != null) {
                return project;
            }
        }
        return null;
    }

    private static IJavaProject getSourceProjectForSourcePath(String sourcePath) {

        // TODO this code is messy, why get workspace root two different ways, and is
        // there a better way to handle source paths?
        IWorkspace eclipseWorkspace = BazelJdtPlugin.getResourceHelper().getEclipseWorkspace();
        IWorkspaceRoot rootResource = eclipseWorkspace.getRoot();
        IProject[] projects = rootResource.getProjects();

        String absoluteSourcePathString = File.separator + sourcePath.replace("\"", "");
        Path absoluteSourcePath = new File(absoluteSourcePathString).toPath();

        for (IProject project : projects) {
            IJavaProject jProject = BazelJdtPlugin.getJavaCoreHelper().getJavaProjectForProject(project);
            IClasspathEntry[] classpathEntries = BazelJdtPlugin.getJavaCoreHelper().getRawClasspath(jProject);
            if (classpathEntries == null) {
                LOG.error("No classpath entries found for project [{}]", jProject.getElementName());
                continue;
            }
            for (IClasspathEntry entry : classpathEntries) {
                if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
                    continue;
                }
                IResource res = BazelJdtPlugin.getResourceHelper().findMemberInWorkspace(entry.getPath());
                if (res == null) {
                    continue;
                }
                IPath projectLocation = res.getLocation();
                String absProjectRoot = projectLocation.toOSString();
                absProjectRoot = entry.getPath().toString();
                if ((absProjectRoot != null && !absProjectRoot.isEmpty())
                        && absoluteSourcePath.startsWith(absProjectRoot)) {
                    IPath[] inclusionPatterns = entry.getInclusionPatterns();
                    IPath[] exclusionPatterns = entry.getExclusionPatterns();
                    if (!matchPatterns(absoluteSourcePath, exclusionPatterns) && (inclusionPatterns == null
                            || inclusionPatterns.length == 0 || matchPatterns(absoluteSourcePath, inclusionPatterns))) {
                        return jProject;
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
}
