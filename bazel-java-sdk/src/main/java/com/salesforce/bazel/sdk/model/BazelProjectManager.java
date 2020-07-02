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
}
