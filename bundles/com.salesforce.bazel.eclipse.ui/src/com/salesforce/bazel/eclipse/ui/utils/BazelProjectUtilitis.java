package com.salesforce.bazel.eclipse.ui.utils;

import static com.salesforce.bazel.eclipse.core.BazelCorePluginSharedContstants.BAZEL_NATURE_ID;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.internal.ide.actions.BuildUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("restriction")
public class BazelProjectUtilitis {

    private static Logger LOG = LoggerFactory.getLogger(BazelProjectUtilitis.class);

    /**
     * @param window
     *            the workbench window
     * @return a list of selected Bazel projects
     */
    public static List<IProject> findSelectedProjects(final IWorkbenchWindow window) {
        final List<IProject> result = new ArrayList<>();
        final var projects = BuildUtilities.findSelectedProjects(window);
        for (final IProject project : projects) {
            if (isBazelProject(project)) {
                result.add(project);
            }
        }
        return result;
    }

    public static boolean isBazelProject(final IProject project) {
        try {
            return project.isOpen() && project.hasNature(BAZEL_NATURE_ID);
        } catch (CoreException e) {
            LOG.warn("Unable to check project '{}' for Bazel project nature.", project, e);
            return false;
        }
    }

}
