package com.salesforce.bazel.eclipse.config;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelProject;
import com.salesforce.bazel.sdk.model.BazelProjectManager;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.util.BazelPathHelper;

public class EclipseBazelProjectManager extends BazelProjectManager {
	private ResourceHelper resourceHelper;
	private JavaCoreHelper javaCoreHelper;
	private LogHelper logger;
	
	public EclipseBazelProjectManager(ResourceHelper resourceHelper, JavaCoreHelper javaCoreHelper) {
		this.resourceHelper = resourceHelper;
		this.javaCoreHelper = javaCoreHelper;
		this.logger = LogHelper.log(this.getClass());
	}

	@Override
	public BazelProject getSourceProjectForSourcePath(BazelWorkspace bazelWorkspace, String sourcePath) {
        Collection<BazelProject> bazelProjects = this.getAllProjects();

        String canonicalSourcePathString = BazelPathHelper.getCanonicalPathStringSafely(bazelWorkspace.getBazelWorkspaceRootDirectory()) + File.separator + sourcePath;
        Path canonicalSourcePath = new File(canonicalSourcePathString).toPath();

        for (BazelProject candidateProject : bazelProjects) {
        	IProject iProject = (IProject)candidateProject.getProjectImpl();
            IJavaProject jProject = javaCoreHelper.getJavaProjectForProject(iProject);
            IClasspathEntry[] classpathEntries = javaCoreHelper.getRawClasspath(jProject);
            if (classpathEntries == null) {
            	logger.error("No classpath entries found for project ["+jProject.getElementName()+"]");
                continue;
            }
            for (IClasspathEntry entry : classpathEntries) {
                if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
                    continue;
                }
                IResource res = this.resourceHelper.findMemberInWorkspace(entry.getPath());
                if (res == null) {
                    continue;
                }
                IPath projectLocation = res.getLocation();
                if (projectLocation != null && !projectLocation.isEmpty()) {
                    String canonicalProjectRoot = BazelPathHelper.getCanonicalPathStringSafely(projectLocation.toOSString());
                    if (canonicalSourcePathString.startsWith(canonicalProjectRoot)) {
                        IPath[] inclusionPatterns = entry.getInclusionPatterns();
                        IPath[] exclusionPatterns = entry.getExclusionPatterns();
                        if (!matchPatterns(canonicalSourcePath, exclusionPatterns)) {
                            if (inclusionPatterns == null || inclusionPatterns.length == 0 || matchPatterns(canonicalSourcePath, inclusionPatterns)) {
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

	@Override
	public void setProjectReferences(BazelProject thisProject, List<BazelProject> updatedRefList) {
    	IProject thisEclipseProject = (IProject)thisProject.getProjectImpl();
    	IProject[] updatedEclipseRefList = new IProject[updatedRefList.size()];
    	int i = 0;
    	for (BazelProject ref : updatedRefList) {
    		updatedEclipseRefList[i] = (IProject)ref.getProjectImpl();
    		i++;
    	}    	
        IProjectDescription projectDescription = this.resourceHelper.getProjectDescription(thisEclipseProject);
        
        projectDescription.setReferencedProjects(updatedEclipseRefList);
        resourceHelper.setProjectDescription(thisEclipseProject, projectDescription);
	}
}
