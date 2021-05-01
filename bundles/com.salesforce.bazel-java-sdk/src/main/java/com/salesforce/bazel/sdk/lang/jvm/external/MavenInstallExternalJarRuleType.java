package com.salesforce.bazel.sdk.lang.jvm.external;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

/**
 * Specialization of BazelExternalJarRuleType for the maven_install rule.
 * <p>
 * The maven_install rule is a bit tricky, in that it is not reliable in where to find the 
 * downloaded jars. Also, the source jars may not be there. This will be an evolving solution
 * as we better understand how to make this more reliable.
 */
public class MavenInstallExternalJarRuleType extends BazelExternalJarRuleType {
    // these options are expected to be driven by tool preferences
    public static boolean cachedJars_supplyCoursierCacheLocation = false;
    public static boolean cachedJars_supplyWorkspaceBazelBinLocations = true;

    // derived from the WORKSPACE file (or .bzl files included from the WORKSPACE)
    // each maven_install rule invocation must have a unique namespace, the default value is "maven"
    private static Map<String, List<String>> mavenInstallNamespaces;
    
    // maven_install can sometimes use coursier to download jars
    // delegate to a dedicated util to worry about that
    private CoursierUtil coursierUtil = new CoursierUtil();

    public MavenInstallExternalJarRuleType(OperatingEnvironmentDetectionStrategy os) {
        super(BazelExternalJarRuleManager.MAVEN_INSTALL, os);
        init();
    }
    
    private void init() {
        mavenInstallNamespaces = new HashMap<>();
    }

    /**
     * This rule type is known to the Bazel SDK, but is it being used by the workspace? Specialized implementations of this
     * method will likely look into the WORKSPACE file to determine this.
     */
    public boolean isUsedInWorkspace(BazelWorkspace bazelWorkspace) {
        // TODO BazelWorkspace should have some parsing functions for retrieving rule data.
        // until we have that, we are going to do a cheat and just look for the default maven namespace directory in bazel-bin/external
        File externalDir = new File(bazelWorkspace.getBazelBinDirectory(), "external");
        if (externalDir.exists()) {
            File[] markerFiles = externalDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return "maven".equals(name);
                }
            });
            isUsedInWorkspace = markerFiles.length > 0;
        }
        
        return isUsedInWorkspace;
    }
    
    /**
     * Get the locations of the local jars downloaded from the remote repo. These are the root directories,
     * and the jars can be nested at arbitrary depths below each of these locations.
     */
    @Override
    public List<File> getDownloadedJarLocations(BazelWorkspace bazelWorkspace) {
        // workspace name is the key to our cached data
        String workspaceName = bazelWorkspace.getName();
        List<String> namespaces = mavenInstallNamespaces.get(workspaceName);

        // namespaces are cached, as they will change almost never
        if (namespaces == null) {
            namespaces = loadNamespaces(bazelWorkspace);
            mavenInstallNamespaces.put(workspaceName, namespaces);
        }

        // locations are computed each time, as they can change based bazel clean activities
        List<File> localJarLocationsNew = new ArrayList<>();
        if (cachedJars_supplyCoursierCacheLocation) {
            File coursierCacheLocation = coursierUtil.addCoursierCacheLocation(bazelWorkspace, os);
            if (coursierCacheLocation != null) {
                localJarLocationsNew.add(coursierCacheLocation);
            }
        }
        if (cachedJars_supplyWorkspaceBazelBinLocations) {
            addBazelBinLocations(bazelWorkspace, namespaces, localJarLocationsNew);
        }

        // for thread safety, we build the list in a local var, and then switch at the end here
        downloadedJarLocations = localJarLocationsNew;
        return downloadedJarLocations;
    }
    
    /**
     * Something about the workspace changed. Discard computed work for the passed workspace.
     * If the parameter is null, discard the work for all workspaces.
     */
    public void discardComputedWork(BazelWorkspace bazelWorkspace) {
        if (bazelWorkspace == null) {
            init();
            return;
        }
        String workspaceName = bazelWorkspace.getName();
        mavenInstallNamespaces.remove(workspaceName);
        
        coursierUtil.discardComputedWork(bazelWorkspace);
    }

    protected List<String> loadNamespaces(BazelWorkspace bazelWorkspace) {
        List<String> namespaces = new ArrayList<>();
        
        // TODO 'maven' is the default namespace, but there may be others.
        // for each invocation of maven_install rule, there is a distinct namespace identified by the name attribute:
        //        maven_install(name = "deprecated", ...
        // TODO BazelWorkspace should have some parsing functions for retrieving rule data.
        // in this case, we need to find maven_install rule invocations, and pluck the list of name attributes.
        // for our primary repo, this is complicated by the fact that the maven_install rules are actually in
        // .bzl files brought in by load() statements in the WORKSPACE
        namespaces.add("maven");
        
        return namespaces;
    }

    /**
     * maven_install will download jars (and sometimes source jars) into directories such as:
     *    ROOT/bazel-bin/external/maven
     *    ROOT/bazel-bin/external/webtest
     * if you have two maven_install rules with names 'maven' and 'webtest'
     */
    protected void addBazelBinLocations(BazelWorkspace bazelWorkspace, List<String> namespaces, List<File> localJarLocationsNew) {
        File bazelbinDir = bazelWorkspace.getBazelBinDirectory();
        File externalDir = new File(bazelbinDir, "external");

        for (String namespace : namespaces) {
            File rootMavenInstallDir = new File(externalDir, namespace);
            localJarLocationsNew.add(rootMavenInstallDir);
        }
    }
}
