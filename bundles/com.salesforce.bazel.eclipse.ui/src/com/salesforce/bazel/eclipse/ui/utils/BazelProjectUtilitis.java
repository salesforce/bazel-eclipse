package com.salesforce.bazel.eclipse.ui.utils;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.internal.ide.actions.BuildUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.model.BazelProject;

@SuppressWarnings("restriction")
public class BazelProjectUtilitis {

    public static Logger LOG = LoggerFactory.getLogger(BazelProjectUtilitis.class);

    /**
     * @param window
     *            the workbench window
     * @return a list of selected Bazel projects
     */
    public static List<IProject> findSelectedProjects(final IWorkbenchWindow window) {
        final List<IProject> result = new ArrayList<>();
        final var projects = BuildUtilities.findSelectedProjects(window);
        for (final IProject project : projects) {
            if (BazelProject.isBazelProject(project)) {
                result.add(project);
            }
        }
        return result;
    }

}
