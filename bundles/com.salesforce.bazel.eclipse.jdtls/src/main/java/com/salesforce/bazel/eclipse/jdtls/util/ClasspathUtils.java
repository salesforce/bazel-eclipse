package com.salesforce.bazel.eclipse.jdtls.util;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.sdk.logging.LogHelper;

public class ClasspathUtils {
    private static final LogHelper LOG = LogHelper.log(ClasspathUtils.class);

    private static IJavaProject getSourceProjectForSourcePath(String sourcePath) {

        // TODO this code is messy, why get workspace root two different ways, and is
        // there a better way to handle source paths?
        var eclipseWorkspace = ComponentContext.getInstance().getResourceHelper().getEclipseWorkspace();
        var rootResource = eclipseWorkspace.getRoot();
        var projects = rootResource.getProjects();

        var absoluteSourcePathString = File.separator + sourcePath.replace("\"", "");
        var absoluteSourcePath = new File(absoluteSourcePathString).toPath();

        for (IProject project : projects) {
            var jProject = ComponentContext.getInstance().getJavaCoreHelper().getJavaProjectForProject(project);
            var classpathEntries = ComponentContext.getInstance().getJavaCoreHelper().getRawClasspath(jProject);
            if (classpathEntries == null) {
                LOG.error("No classpath entries found for project [{}]", jProject.getElementName());
                continue;
            }
            for (IClasspathEntry entry : classpathEntries) {
                if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
                    continue;
                }
                var res = ComponentContext.getInstance().getResourceHelper().findMemberInWorkspace(entry.getPath());
                if (res == null) {
                    continue;
                }
                var projectLocation = res.getLocation();
                var absProjectRoot = projectLocation.toOSString();
                absProjectRoot = entry.getPath().toString();
                if (((absProjectRoot != null) && !absProjectRoot.isEmpty())
                        && absoluteSourcePath.startsWith(absProjectRoot)) {
                    var inclusionPatterns = entry.getInclusionPatterns();
                    var exclusionPatterns = entry.getExclusionPatterns();
                    if (!matchPatterns(absoluteSourcePath, exclusionPatterns)
                            && ((inclusionPatterns == null) || (inclusionPatterns.length == 0)
                                    || matchPatterns(absoluteSourcePath, inclusionPatterns))) {
                        return jProject;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns the IJavaProject in the current workspace that contains at least one of the specified sources.
     */
    public static IJavaProject getSourceProjectForSourcePaths(List<String> sources) {
        for (String candidate : sources) {
            var project = getSourceProjectForSourcePath(candidate);
            if (project != null) {
                return project;
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
}
