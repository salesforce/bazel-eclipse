package com.salesforce.bazel.eclipse.builder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;

import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelProblem;
import com.salesforce.bazel.sdk.project.BazelProject;

/**
 * Publishes BazelError instances to the Problems View.
 */
class BazelErrorPublisher {

    static final String UNKNOWN_PROJECT_ERROR_MSG_PREFIX = "ERROR IN UNKNOWN PROJECT: ";

    private static final LogHelper LOG = LogHelper.log(BazelErrorPublisher.class);

    private final IProject rootProject;
    private final Collection<IProject> projects;
    private final Map<BazelLabel, BazelProject> labelToProject;
    private BazelProblemMarkerManager markerManager;

    public BazelErrorPublisher(IProject rootProject, Collection<IProject> projects,
            Map<BazelLabel, BazelProject> labelToProject) {
        this.rootProject = rootProject;
        this.projects = projects;
        this.labelToProject = labelToProject;
        this.markerManager = new BazelProblemMarkerManager(getClass().getName());
    }

    public void publish(List<BazelProblem> errors, IProgressMonitor monitor) {
        clearProblemsView(monitor);
        publishToProblemsView(errors, monitor);
    }

    // maps the specified errors to the project instances they belong to, and returns that mapping
    static Map<IProject, List<BazelProblem>> assignErrorsToOwningProject(List<BazelProblem> errors,
            Map<BazelLabel, BazelProject> labelToProject, IProject rootProject) {
        Map<IProject, List<BazelProblem>> projectToErrors = new HashMap<>();
        List<BazelProblem> remainingErrors = new LinkedList<>(errors);
        for (BazelProblem error : errors) {
            BazelLabel owningLabel = error.getOwningLabel(labelToProject.keySet());
            if (owningLabel != null) {
                BazelProject project = labelToProject.get(owningLabel);
                IProject eclipseProject = (IProject) project.getProjectImpl();
                mapProblemToProject(error.toErrorWithRelativizedResourcePath(owningLabel), eclipseProject,
                    projectToErrors);
                remainingErrors.remove(error);
            }
        }
        if (!remainingErrors.isEmpty()) {
            if (rootProject != null) {
                for (BazelProblem error : remainingErrors) {
                    mapProblemToProject(error.toGenericWorkspaceLevelError(UNKNOWN_PROJECT_ERROR_MSG_PREFIX),
                        rootProject, projectToErrors);
                }
            } else {
                // getting here is a bug - at least log the errors we didn't assign to any project
                for (BazelProblem error : remainingErrors) {
                    LOG.error("Unhandled error: " + error);
                }
            }
        }
        return projectToErrors;
    }

    private void clearProblemsView(IProgressMonitor monitor) {
        List<IProject> allProjects = new ArrayList<>(projects.size() + 1);
        allProjects.addAll(projects);
        if (rootProject != null) {
            allProjects.add(rootProject);
        }
        markerManager.clear(allProjects, monitor);
    }

    private void publishToProblemsView(List<BazelProblem> errors, IProgressMonitor monitor) {
        Map<IProject, List<BazelProblem>> projectToErrors =
                assignErrorsToOwningProject(errors, labelToProject, rootProject);
        for (IProject project : projectToErrors.keySet()) {
            markerManager.publish(projectToErrors.get(project), project, monitor);
        }
    }

    private static void mapProblemToProject(BazelProblem problem, IProject project,
            Map<IProject, List<BazelProblem>> projectToErrors) {
        List<BazelProblem> storedErrors = projectToErrors.get(project);
        if (storedErrors == null) {
            storedErrors = new ArrayList<>();
        }
        storedErrors.add(problem);
        projectToErrors.put(project, storedErrors);
    }
}
