package com.salesforce.bazel.sdk.lang.jvm.external;

import java.io.File;
import java.util.List;

import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

/**
 * An instance of one of the supported types of Maven jar integration (maven_install, etc)
 */
public class BazelExternalJarRuleType {
    public final String ruleName;
    protected final OperatingEnvironmentDetectionStrategy os;
    
    protected List<File> downloadedJarLocations;
    protected boolean isUsedInWorkspace = false;

    /**
     * This ctor should only be used by subclasses.
     */
    public BazelExternalJarRuleType(String ruleName, OperatingEnvironmentDetectionStrategy os) {
        this.ruleName = ruleName;
        this.os = os;
        this.downloadedJarLocations = null;
    }

    /**
     * Use this method only if there is a new rule type, and you don't want to implement a specialized subclass.
     * Normally this is only used for tests, but if someone implemented a rule that used ~/.m2/repository this
     * would be the way to implement it. 
     */
    public BazelExternalJarRuleType(String ruleName, OperatingEnvironmentDetectionStrategy os, List<File> downloadedJarLocations, 
            boolean isUsedInWorkspace) {
        this.ruleName = ruleName;
        this.os = os;
        this.downloadedJarLocations = downloadedJarLocations;
        this.isUsedInWorkspace = isUsedInWorkspace;
    }
    
    /**
     * This rule type is known to the Bazel SDK, but is it being used by the workspace? Specialized implementations of this
     * method will likely look into the WORKSPACE file to determine this.
     */
    public boolean isUsedInWorkspace(BazelWorkspace bazelWorkspace) {
        return isUsedInWorkspace;
    }

    /**
     * Get the locations of the local jars downloaded from the remote repo. These are the root directories,
     * and the jars can be nested at arbitrary depths below each of these locations.
     */
    public List<File> getDownloadedJarLocations(BazelWorkspace bazelWorkspace) {
        return downloadedJarLocations;
    }
    
    /**
     * Something about the workspace changed. Discard computed work for the passed workspace.
     * If the parameter is null, discard the work for all workspaces.
     */
    public void discardComputedWork(BazelWorkspace bazelWorkspace) {
        
    }
}
