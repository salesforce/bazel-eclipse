package com.salesforce.bazel.sdk.model;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Central manager for managing BazelProject instances 
 */
public class BazelProjectManager {

	private Map<String, BazelProject> projectMap = new TreeMap<>();
	private static BazelProjectManager instance;
	
	BazelProjectManager() {
	}
	
	public static BazelProjectManager getInstance() {
		if (instance == null) {
			instance = new BazelProjectManager();
		}
		return instance;
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
}
