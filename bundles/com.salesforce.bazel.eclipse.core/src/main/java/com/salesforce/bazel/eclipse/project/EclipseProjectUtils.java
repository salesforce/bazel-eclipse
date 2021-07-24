package com.salesforce.bazel.eclipse.project;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;

/**
 * Useful utils for Eclipse+Bazel projects
 */
public class EclipseProjectUtils {

    public static Set<IProject> getDownstreamProjectsOf(IProject project, IJavaProject[] allImportedProjects) {
        Set<IProject> downstreamProjects = new LinkedHashSet<>(); // cannot be a TreeSet because Project doesn't implement Comparable
        collectDownstreamProjects(project, downstreamProjects, allImportedProjects);
        return downstreamProjects;
    }

    // determines all downstream projects, including transitives, of the specified "upstream" project, by looking at the
    // specified "allImportedProjects", and adds them to the specified "downstreams" Set.
    private static void collectDownstreamProjects(IProject upstream, Set<IProject> downstreams,
            IJavaProject[] allImportedProjects) {
        for (IJavaProject project : allImportedProjects) {
            try {
                for (String requiredProjectName : project.getRequiredProjectNames()) {
                    String upstreamProjectName = upstream.getName();
                    if (upstreamProjectName.equals(requiredProjectName)) {
                        IProject downstream = project.getProject();
                        if (!downstreams.contains(downstream)) {
                            downstreams.add(downstream);
                            collectDownstreamProjects(downstream, downstreams, allImportedProjects);
                        }
                    }
                }
            } catch (JavaModelException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    /**
     * Uses the last token in the Bazel package token (e.g. apple-api for //projects/libs/apple-api) for the name. But
     * if another project has already been imported with the same name, start appending a number to the name until it
     * becomes unique.
     */
    public static String computeEclipseProjectNameForBazelPackage(BazelPackageLocation packageInfo,
            List<IProject> previouslyImportedProjects, List<IProject> currentlyImportedProjectsList) {
        String packageName = packageInfo.getBazelPackageNameLastSegment();
        String finalPackageName = packageName;
        int index = 2;

        boolean foundUniqueName = false;
        while (!foundUniqueName) {
            foundUniqueName = true;
            if (doesProjectNameConflict(previouslyImportedProjects, finalPackageName)
                    || doesProjectNameConflict(currentlyImportedProjectsList, finalPackageName)) {
                finalPackageName = packageName + index;
                index++;
                foundUniqueName = false;
            }
        }
        return finalPackageName;
    }

    /**
     * Determines if candidate projectName conflicts with an existing project
     */
    public static boolean doesProjectNameConflict(List<IProject> existingProjectsList, String projectName) {
        for (IProject otherProject : existingProjectsList) {
            String otherProjectName = otherProject.getName();
            if (projectName.equals(otherProjectName)) {
                return true;
            }
        }
        return false;
    }

    public static void addNatureToEclipseProject(IProject eclipseProject, String nature) throws CoreException {
        if (!eclipseProject.hasNature(nature)) {
            ResourceHelper resourceHelper = BazelPluginActivator.getResourceHelper();

            IProjectDescription eclipseProjectDescription = resourceHelper.getProjectDescription(eclipseProject);
            String[] prevNatures = eclipseProjectDescription.getNatureIds();
            String[] newNatures = new String[prevNatures.length + 1];
            System.arraycopy(prevNatures, 0, newNatures, 0, prevNatures.length);
            newNatures[prevNatures.length] = nature;
            eclipseProjectDescription.setNatureIds(newNatures);

            resourceHelper.setProjectDescription(eclipseProject, eclipseProjectDescription);
        }
    }
}
