package com.salesforce.bazel.sdk.lang.jvm;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.salesforce.bazel.sdk.aspect.AspectOutputJarSet;
import com.salesforce.bazel.sdk.aspect.AspectPackageInfo;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelBuildFile;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.BazelProjectTargets;
import com.salesforce.bazel.sdk.util.SimplePerfRecorder;
import com.salesforce.bazel.sdk.util.WorkProgressMonitor;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

/**
 * Computes a JVM classpath for a BazelProject based on the dependencies defined in 
 * Bazel, including resolving file paths to jar files and accounting for references
 * to BazelProjects.
 */
public class BazelJvmClasspath {
    // TODO make classpath cache timeout configurable
    private static final long CLASSPATH_CACHE_TIMEOUT_MS = 300000; 

    private final BazelWorkspace bazelWorkspace;
    private final BazelProjectManager bazelProjectManager;
    private final BazelProject bazelProject;
    private final ImplicitClasspathHelper implicitDependencyHelper;
    private final OperatingEnvironmentDetectionStrategy osDetector;
    private final BazelCommandManager bazelCommandManager;
    private final LogHelper logger;
    
    private BazelJvmClasspathResponse cachedEntries;
    private long cachePutTimeMillis = 0;
    

    public BazelJvmClasspath(BazelWorkspace bazelWorkspace, BazelProjectManager bazelProjectManager, BazelProject bazelProject,
			ImplicitClasspathHelper implicitDependencyHelper, OperatingEnvironmentDetectionStrategy osDetector, 
			BazelCommandManager bazelCommandManager) {
    	this.bazelWorkspace = bazelWorkspace;
    	this.bazelProjectManager = bazelProjectManager;
		this.bazelProject = bazelProject;
		this.implicitDependencyHelper = implicitDependencyHelper;
		this.osDetector = osDetector;
		this.bazelCommandManager = bazelCommandManager;
		
		logger = LogHelper.log(this.getClass());
	}

    public void clean() {
        cachedEntries = null;
        cachePutTimeMillis = 0;
    }
    
    public static class BazelJvmClasspathResponse {
    	/**
    	 * The jvm classpath entries (e.g. jar files)
    	 */
    	public JvmClasspathEntry[] jvmClasspathEntries = new JvmClasspathEntry[] {};
    	
    	/**
    	 * The list of projects that should be added to the classpath, if this environment is using
    	 * project support. The caller is expected to invoke the following:
    	 *   bazelProjectManager.setProjectReferences(bazelProject, computedClasspath.classpathProjectReferences);
    	 * But due to locking in some environments, this may need to be delayed.
    	 */
    	public List<BazelProject> classpathProjectReferences = new ArrayList<>();
    }
    
    /**
     * Computes the JVM classpath for the associated BazelProject
     */
	public BazelJvmClasspathResponse getClasspathEntries(WorkProgressMonitor progressMonitor) {
        // sanity check
        if (bazelWorkspace == null) {
        	// not sure how we could get here, but just check
            throw new IllegalStateException("Attempt to retrieve the classpath of a Bazel Java project prior to setting up the Bazel workspace.");
        }
        long startTimeMS = System.currentTimeMillis();

        boolean foundCachedEntries = false;
        boolean isImport = false;
        BazelJvmClasspathResponse response = new BazelJvmClasspathResponse();
        
        
        if (this.cachedEntries != null) {
            long now = System.currentTimeMillis();
            if ((now - this.cachePutTimeMillis) > CLASSPATH_CACHE_TIMEOUT_MS) {
            	logger.info("Evicted classpath from cache for project "+bazelProject.name);
                this.cachedEntries = null;
            } else {
            	logger.debug("  Using cached classpath for project "+bazelProject.name);
                return this.cachedEntries;
            }
        }
    
        logger.info("Computing classpath for project "+bazelProject.name+" (cached entries: "+foundCachedEntries+", is import: "+isImport+")");
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);

        Set<String> projectsAddedToClasspath = new HashSet<>();
        Map<String, JvmClasspathEntry> mainClasspathEntryMap = new TreeMap<>();
        Map<String, JvmClasspathEntry> testClasspathEntryMap = new TreeMap<>();

        try {
            BazelProjectTargets configuredTargetsForProject = this.bazelProjectManager.getConfiguredBazelTargets(bazelProject, false);
            
            // get the model of the BUILD file for this package, which will tell us the type of each target and the list
            // of all targets if configured with the wildcard target
            BazelBuildFile bazelBuildFileModel = null; 
            try {
                bazelBuildFileModel = bazelWorkspaceCmdRunner.queryBazelTargetsInBuildFile(progressMonitor, 
                		this.bazelProjectManager.getBazelLabelForProject(bazelProject));
            } catch (Exception anyE) {
                logger.error("Unable to compute classpath containers entries for project "+bazelProject.name, anyE);
                return returnEmptyClasspathOrThrow(anyE);
            }
            // now get the actual list of activated targets, with wildcard resolved using the BUILD file model if necessary
            Set<String> actualActivatedTargets = configuredTargetsForProject.getActualTargets(bazelBuildFileModel);
            
            for (String targetLabel : actualActivatedTargets) {
                String targetType = bazelBuildFileModel.getRuleTypeForTarget(targetLabel);
                boolean isTestTarget = "java_test".equals(targetType);

                Set<AspectPackageInfo> packageInfos = bazelWorkspaceCmdRunner.getAspectPackageInfos(
                		targetLabel, progressMonitor, "getClasspathEntries");

                for (AspectPackageInfo packageInfo : packageInfos) {
                    BazelProject otherProject = getSourceProjectForSourcePaths(packageInfo.getSources());
                    
                    if (otherProject == null) {
                        // no project found that houses the sources of this bazel target, add the jars to the classpath
                        // this means that this is an external jar, or a jar produced by a bazel target that was not imported
                        for (AspectOutputJarSet jarSet : packageInfo.getGeneratedJars()) {
                        	JvmClasspathEntry cpEntry = jarsToClasspathEntry(jarSet, isTestTarget); 
                            if (cpEntry != null) {
                                addOrUpdateClasspathEntry(bazelWorkspaceCmdRunner, targetLabel, cpEntry, isTestTarget, 
                                    mainClasspathEntryMap, testClasspathEntryMap);
                            } else {
                                // there was a problem with the aspect computation, this might resolve itself if we recompute it
                                bazelWorkspaceCmdRunner.flushAspectInfoCache(configuredTargetsForProject.getConfiguredTargets());
                            }
                        }
                        for (AspectOutputJarSet jarSet : packageInfo.getJars()) {
                        	JvmClasspathEntry cpEntry = jarsToClasspathEntry(jarSet, isTestTarget);
                            if (cpEntry != null) {
                                addOrUpdateClasspathEntry(bazelWorkspaceCmdRunner, targetLabel, cpEntry, isTestTarget, 
                                    mainClasspathEntryMap, testClasspathEntryMap);
                            } else {
                                // there was a problem with the aspect computation, this might resolve itself if we recompute it
                                bazelWorkspaceCmdRunner.flushAspectInfoCache(configuredTargetsForProject.getConfiguredTargets());
                            }
                        }
                    } else { // otherProject != null 
                        String otherBazelProjectName = otherProject.name;
                        if (bazelProject.name.equals(otherBazelProjectName)) {
                            // the project referenced is actually the the current project that this classpath container is for
                            
                            // some rule types have hidden dependencies that we need to add
                            // if our project has any of those rules, we need to add in the dependencies to our classpath
                            Set<JvmClasspathEntry> implicitDeps = implicitDependencyHelper.computeImplicitDependencies(bazelWorkspace, packageInfo);
                            for (JvmClasspathEntry implicitDep : implicitDeps) {
                                addOrUpdateClasspathEntry(bazelWorkspaceCmdRunner, targetLabel, implicitDep, isTestTarget, 
                                    mainClasspathEntryMap, testClasspathEntryMap);
                            }
                            
                        } else {
                            // add the referenced project to the classpath, directly as a project classpath entry
                            if (!projectsAddedToClasspath.contains(otherBazelProjectName)) {
                            	BazelProject otherBazelProject = bazelProjectManager.getProject(otherBazelProjectName);
                            	if (otherBazelProject == null) {
                            		otherBazelProject = new BazelProject(otherBazelProjectName);
                            	}
                                JvmClasspathEntry cpEntry = new JvmClasspathEntry(otherBazelProject);
                                addOrUpdateClasspathEntry(bazelWorkspaceCmdRunner, targetLabel, cpEntry, isTestTarget, 
                                    mainClasspathEntryMap, testClasspathEntryMap);
                            }
                            projectsAddedToClasspath.add(otherBazelProjectName);
                            
                            // now make a project reference between this project and the other project; this allows for features like
                            // code refactoring across projects to work correctly
                            addProjectReference(response.classpathProjectReferences, otherProject);
                        }
                    }
                }
            } // for loop
            
            
        } catch (IOException | InterruptedException e) {
        	logger.error("Unable to compute classpath containers entries for project "+bazelProject.name, e);
            return returnEmptyClasspathOrThrow(e);
        } catch (BazelCommandLineToolConfigurationException e) {
        	logger.error("Bazel not found: " + e.getMessage());
            return returnEmptyClasspathOrThrow(e);
        } catch (RuntimeException re) {
        	logger.error("Bazel not found: " + re.getMessage());
            return returnEmptyClasspathOrThrow(re);
        }

        // cache the entries
        this.cachePutTimeMillis = System.currentTimeMillis();
        response.jvmClasspathEntries = assembleClasspathEntries(mainClasspathEntryMap, testClasspathEntryMap);
        this.cachedEntries = response;
        logger.debug("Cached the classpath for project "+bazelProject.name);
        
        SimplePerfRecorder.addTime("classpath", startTimeMS);
        
        return cachedEntries;
    }


    // INTERNAL

    private void addOrUpdateClasspathEntry(BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner, String targetLabel, 
    		JvmClasspathEntry cpEntry, boolean isTestTarget,  Map<String, JvmClasspathEntry> mainClasspathEntryMap, 
            Map<String, JvmClasspathEntry> testClasspathEntryMap) {
        if (cpEntry == null) {
            // something was wrong with the Aspect that described the entry, flush the cache
            bazelWorkspaceCmdRunner.flushAspectInfoCache(targetLabel);
            return;
        }

        String pathStr = cpEntry.pathToJar;
        if (pathStr == null) {
        	pathStr = cpEntry.bazelProject.name;
        }
        //logger.info("Adding cp entry ["+pathStr+"] to target ["+targetLabel+"]");
        if (!isTestTarget) {
            // add to the main classpath?
            // if this was previously a test CP entry, we need to remove it since this is now a main cp entry
            testClasspathEntryMap.remove(pathStr);

            // make it main cp
            mainClasspathEntryMap.put(pathStr, cpEntry);
        } else {
            // add to the test classpath?
            // if it already exists in the main classpath, do not also add to the test classpath
            if (!mainClasspathEntryMap.containsKey(pathStr)) {
                testClasspathEntryMap.put(pathStr, cpEntry);
            }
        }
    }

    private JvmClasspathEntry[] assembleClasspathEntries(Map<String, JvmClasspathEntry> mainClasspathEntryMap, 
            Map<String, JvmClasspathEntry> testClasspathEntryMap) {
        List<JvmClasspathEntry> classpathEntries = new ArrayList<>();
        classpathEntries.addAll(mainClasspathEntryMap.values());
        classpathEntries.addAll(testClasspathEntryMap.values());

        return classpathEntries.toArray(new JvmClasspathEntry[] {});
    }
    /**
     * Returns the IJavaProject in the current workspace that contains at least one of the specified sources.
     */
    private BazelProject getSourceProjectForSourcePaths(List<String> sources) {
        for (String candidate : sources) {
        	BazelProject project = this.bazelProjectManager.getSourceProjectForSourcePath(bazelWorkspace, candidate);
            if (project != null) {
                return project;
            }
        }
        return null;
    }

    private JvmClasspathEntry jarsToClasspathEntry(AspectOutputJarSet jarSet, boolean isTestLib) {
    	JvmClasspathEntry cpEntry = null;
        cpEntry = new JvmClasspathEntry(jarSet.getJar(), jarSet.getSrcJar(), isTestLib);
        return cpEntry;
    }

    @SuppressWarnings("unused")
    private JvmClasspathEntry[] jarsToClasspathEntries(BazelWorkspace bazelWorkspace, WorkProgressMonitor progressMonitor, 
            Set<AspectOutputJarSet> jars, boolean isTestLib) {
    	JvmClasspathEntry[] entries = new JvmClasspathEntry[jars.size()];
        int i = 0;
        File bazelOutputBase = bazelWorkspace.getBazelOutputBaseDirectory();
        File bazelExecRoot = bazelWorkspace.getBazelExecRootDirectory();
        for (AspectOutputJarSet jar : jars) {
            entries[i] = jarsToClasspathEntry(jar, isTestLib);
            i++;
        }
        return entries;
    }
    
    private void addProjectReference(List<BazelProject> projectList, BazelProject addThis) {
    	for (BazelProject existingProject : projectList) {
    		if (existingProject.name.equals(addThis.name)) {
    			// we already have a reference to this project
    			return;
    		}
    	}
    	projectList.add(addThis);
    }

    private void continueOrThrow(Throwable th) {
        // under real usage, we suppress fatal exceptions because sometimes there are IDE timing issues that can
        // be corrected if the classpath is computed again.
        // But under tests, we want to fail fatally otherwise tests could pass when they shouldn't
        if (osDetector.isTestRuntime()) {
            throw new IllegalStateException("The classpath could not be computed by the BazelClasspathContainer", th);
        }
    }
    
    private BazelJvmClasspathResponse returnEmptyClasspathOrThrow(Throwable th) {
        continueOrThrow(th);
        return new BazelJvmClasspathResponse();  
    }

}
