package com.salesforce.bazel.eclipse.ui.utils;

import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;

import com.salesforce.bazel.eclipse.core.model.BazelProject;

public class JavaSearchUtil {

    public static IJavaSearchScope createScopeIncludingAllWorkspaceProjectsButSelected(
            BazelProject selectedBazelProject) throws CoreException {
        var bazelProjects = selectedBazelProject.getBazelWorkspace().getBazelProjects();
        Set<IJavaProject> javaProjects = new LinkedHashSet<>(bazelProjects.size());

        for (BazelProject bazelProject : bazelProjects) {
            var javaProject = JavaCore.create(bazelProject.getProject());
            if (javaProject.exists()) {
                javaProjects.add(javaProject);
            }
        }

        javaProjects.remove(JavaCore.create(selectedBazelProject.getProject())); // no need to search in current project itself

        return SearchEngine.createJavaSearchScope(javaProjects.toArray(new IJavaElement[javaProjects.size()]));
    }

}
