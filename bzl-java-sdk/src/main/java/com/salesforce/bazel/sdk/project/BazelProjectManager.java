package com.salesforce.bazel.sdk.project;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelProblem;
import com.salesforce.bazel.sdk.model.BazelWorkspace;

/**
 * Central manager for managing BazelProject instances 
 */
public abstract class BazelProjectManager {

	private Map<String, BazelProject> projectMap = new TreeMap<>();
	private LogHelper logger;
	
	public BazelProjectManager() {
		logger = LogHelper.log(this.getClass());
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
	
    /**
     * Runs a build with the passed targets and returns true if no errors are returned.
     */
    public boolean isValid(BazelWorkspace bazelWorkspace, BazelCommandManager bazelCommandManager, 
    		BazelProject bazelProject)  {
        if (bazelWorkspace == null) {
            return false;
        }
        
        
        File bazelWorkspaceRootDirectory = bazelWorkspace.getBazelWorkspaceRootDirectory();
        if (bazelWorkspaceRootDirectory == null) {
            return false;
        }
        
        try {
	        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);
	        
	        if (bazelWorkspaceCmdRunner != null) {
	            BazelProjectTargets targets = getConfiguredBazelTargets(bazelProject, false);
	            List<BazelProblem> details = bazelWorkspaceCmdRunner.runBazelBuild(targets.getConfiguredTargets(), 
	            		Collections.emptyList(), null);
	            for (BazelProblem detail : details) {
	            	logger.error(detail.toString());
	            }
	            return details.isEmpty();
	
	        }
        } catch (Exception anyE) {
        	logger.error("Caught exception validating project ["+bazelProject.name+"]", anyE);
        	// just return false below
        }
        return false;
    }

	
	public abstract BazelProject getSourceProjectForSourcePath(BazelWorkspace bazelWorkspace, String sourcePath);
	
    /**
     * Creates a project reference between this project and a set of other projects.
     * References are used by IDE code refactoring among other things. 
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
	 * List the Bazel targets the user has chosen to activate for this project. Each project configured 
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
	 * List of Bazel build flags for this project, taken from the project configuration
	 */
	public abstract List<String> getBazelBuildFlagsForProject(BazelProject bazelProject);

	/**
	 * Persists preferences for the given project
	 */
	public abstract void addSettingsToProject(BazelProject bazelProject, String bazelWorkspaceRoot, String bazelProjectLabel,
			List<String> bazelTargets, List<String> bazelBuildFlags);

}
