package com.salesforce.bazel.sdk.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Central manager for managing BazelProject instances 
 */
public abstract class BazelProjectManager {

	private Map<String, BazelProject> projectMap = new TreeMap<>();
	
	public BazelProjectManager() {
	}
	
	public void addProject(BazelProject project) {
		projectMap.put(project.name, project);
		project.bazelProjectManager = this;
	}
	
	public BazelProject getProject(String name) {
		return projectMap.get(name);
	}
	
	public Collection<BazelProject> getAllProjects() {
		return projectMap.values();
	}
	
	public abstract BazelProject getSourceProjectForSourcePath(BazelWorkspace bazelWorkspace, String sourcePath);
	
    /**
     * Creates a project reference between this project and a set of other projects.
     * References are used by Eclipse code refactoring among other things. 
     * The direction of reference goes from this->updatedRefList
     * If this project no longer uses another project, removing it from the list will eliminate the project reference.
     */
    public abstract void setProjectReferences(BazelProject thisProject, List<BazelProject> updatedRefList);
    
	/**
	 * The label that identifies the Bazel package that represents this project. This will
	 * be the 'module' label when we start supporting multiple BUILD files in a single 'module'.
	 * Example:  //projects/libs/foo
	 * See https://github.com/salesforce/bazel-eclipse/issues/24
	 */
    public abstract String getBazelLabelForProject(BazelProject bazelProject);

	/**
	 * Returns a map that maps Bazel labels to their projects
	 */
	public abstract Map<BazelLabel, BazelProject> getBazelLabelToProjectMap(Collection<BazelProject> bazelProjects);

	/**
	 * List the Bazel targets the user has chosen to activate for this Eclipse project. Each project configured 
	 * for Bazel is configured to track certain targets and this function fetches this list from the project preferences.
	 * After initial import, this will be just the wildcard target (:*) which means all targets are activated. This
	 * is the safest choice as new targets that are added to the BUILD file will implicitly get picked up. But users
	 * may choose to be explicit if one or more targets in a BUILD file is not needed for development.
	 * <p>
	 * By contract, this method will return only one target if the there is a wildcard target, even if the user does
	 * funny things in their prefs file and sets multiple targets along with the wildcard target.
	 */
	public abstract BazelProjectTargets getConfiguredBazelTargets(BazelProject bazelProject, boolean addWildcardIfNoTargets);

	/**
	 * List of Bazel build flags for this Eclipse project, taken from the project configuration
	 */
	public abstract List<String> getBazelBuildFlagsForProject(BazelProject bazelProject);

	/**
	 * Persists preferences for the given project
	 */
	public abstract void addSettingsToProject(BazelProject bazelProject, String bazelWorkspaceRoot, String bazelProjectLabel,
			List<String> bazelTargets, List<String> bazelBuildFlags);

}
