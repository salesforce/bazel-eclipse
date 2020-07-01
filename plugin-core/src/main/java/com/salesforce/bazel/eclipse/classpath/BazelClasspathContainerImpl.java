package com.salesforce.bazel.eclipse.classpath;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.osgi.service.prefs.BackingStoreException;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.config.BazelProjectPreferences;
import com.salesforce.bazel.eclipse.config.EclipseProjectBazelTargets;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseWorkProgressMonitor;
import com.salesforce.bazel.sdk.abstractions.WorkProgressMonitor;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.model.AspectOutputJarSet;
import com.salesforce.bazel.sdk.model.AspectPackageInfo;
import com.salesforce.bazel.sdk.model.BazelBuildFile;
import com.salesforce.bazel.sdk.model.BazelProblem;
import com.salesforce.bazel.sdk.model.BazelProject;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.model.SimplePerfRecorder;
import com.salesforce.bazel.sdk.util.BazelPathHelper;

public class BazelClasspathContainerImpl {
    // TODO make classpath cache timeout configurable
    private static final long CLASSPATH_CACHE_TIMEOUT_MS = 300000; 

    private final BazelWorkspace bazelWorkspace;
    private final BazelProject bazelProject;
    private final boolean eclipseProjectIsRoot;
    private final ResourceHelper resourceHelper;
    
    private IClasspathEntry[] cachedEntries;
    private long cachePutTimeMillis = 0;
    
    private EclipseImplicitClasspathHelper implicitDependencyHelper = new EclipseImplicitClasspathHelper();

    public BazelClasspathContainerImpl(BazelWorkspace bazelWorkspace, BazelProject bazelProject,
			boolean eclipseProjectIsRoot, ResourceHelper resourceHelper, EclipseImplicitClasspathHelper implicitDependencyHelper) {
    	this.bazelWorkspace = bazelWorkspace;
		this.bazelProject = bazelProject;
		this.eclipseProjectIsRoot = eclipseProjectIsRoot;
		this.resourceHelper = resourceHelper;
		this.implicitDependencyHelper = implicitDependencyHelper;
	}

    public void clean() {
        cachedEntries = null;
        cachePutTimeMillis = 0;
    }
    
	public IClasspathEntry[] getClasspathEntries() {
        // sanity check
        if (!BazelPluginActivator.hasBazelWorkspaceRootDirectory()) {
            throw new IllegalStateException("Attempt to retrieve the classpath of a Bazel Java project prior to setting up the Bazel workspace.");
        }
        long startTimeMS = System.currentTimeMillis();

        boolean foundCachedEntries = false;
        boolean isImport = false;
        
        /**
         * Observed behavior of Eclipse is that this method can get called multiple times before the first invocation completes, therefore 
         * the cache is not as effective as it could be. Synchronize on this instance such that the first invocation completes and populates
         * the cache before the subsequent calls are allowed to proceed.
         */
        synchronized (this) {
        
            if (this.cachedEntries != null) {
                long now = System.currentTimeMillis();
                if ((now - this.cachePutTimeMillis) > CLASSPATH_CACHE_TIMEOUT_MS) {
                    BazelPluginActivator.info("Evicted classpath from cache for project "+bazelProject.name);
                    this.cachedEntries = null;
                } else {
                    BazelPluginActivator.debug("  Using cached classpath for project "+bazelProject.name);
                    return this.cachedEntries;
                }
            }
    
            // TODO figure out a way to get access to an Eclipse progress monitor here
            WorkProgressMonitor progressMonitor = new EclipseWorkProgressMonitor(null);
    
            if (this.eclipseProjectIsRoot) {
                // this project is the artificial container to hold Bazel workspace scoped assets (e.g. the WORKSPACE file)
                return new IClasspathEntry[] {};
            }
    
            BazelPluginActivator.info("Computing classpath for project "+bazelProject.name+" (cached entries: "+foundCachedEntries+", is import: "+isImport+")");
            BazelCommandManager commandFacade = BazelPluginActivator.getBazelCommandManager();
            BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = commandFacade.getWorkspaceCommandRunner(bazelWorkspace);

            Set<IPath> projectsAddedToClasspath = new HashSet<>();
            Map<String, IClasspathEntry> mainClasspathEntryMap = new TreeMap<>();
            Map<String, IClasspathEntry> testClasspathEntryMap = new TreeMap<>();

            
            try {
                IProject eclipseIProject = (IProject)bazelProject.getProjectImpl();
                EclipseProjectBazelTargets configuredTargetsForProject = BazelProjectPreferences.getConfiguredBazelTargets(eclipseIProject, false);
                
                // get the model of the BUILD file for this package, which will tell us the type of each target and the list
                // of all targets if configured with the wildcard target
                BazelBuildFile bazelBuildFileModel = null; 
                try {
                    bazelBuildFileModel = bazelWorkspaceCmdRunner.queryBazelTargetsInBuildFile(progressMonitor, 
                        BazelProjectPreferences.getBazelLabelForEclipseProject(eclipseIProject));
                } catch (Exception anyE) {
                    BazelPluginActivator.error("Unable to compute classpath containers entries for project "+bazelProject.name, anyE);
                    return returnEmptyClasspathOrThrow(anyE);
                }
                // now get the actual list of activated targets, with wildcard resolved using the BUILD file model if necessary
                Set<String> actualActivatedTargets = configuredTargetsForProject.getActualTargets(bazelBuildFileModel);
                
                List<IProject> updatedProjectReferences = new ArrayList<>(); 
                for (String targetLabel : actualActivatedTargets) {
                    String targetType = bazelBuildFileModel.getRuleTypeForTarget(targetLabel);
                    boolean isTestTarget = "java_test".equals(targetType);

                    Set<AspectPackageInfo> packageInfos = bazelWorkspaceCmdRunner.getAspectPackageInfos(
                    		bazelProject.name, targetLabel, progressMonitor, "getClasspathEntries");

                    for (AspectPackageInfo packageInfo : packageInfos) {
                        IJavaProject otherProject = getSourceProjectForSourcePaths(bazelWorkspaceCmdRunner, packageInfo.getSources());
                        
                        if (otherProject == null) {
                            // no project found that houses the sources of this bazel target, add the jars to the classpath
                            // this means that this is an external jar, or a jar produced by a bazel target that was not imported
                            for (AspectOutputJarSet jarSet : packageInfo.getGeneratedJars()) {
                                IClasspathEntry cpEntry = jarsToClasspathEntry(bazelWorkspace, progressMonitor, jarSet, isTestTarget); 
                                if (cpEntry != null) {
                                    addOrUpdateClasspathEntry(bazelWorkspaceCmdRunner, targetLabel, cpEntry, isTestTarget, 
                                        mainClasspathEntryMap, testClasspathEntryMap);
                                } else {
                                    // there was a problem with the aspect computation, this might resolve itself if we recompute it
                                    bazelWorkspaceCmdRunner.flushAspectInfoCache(configuredTargetsForProject.getConfiguredTargets());
                                }
                            }
                            for (AspectOutputJarSet jarSet : packageInfo.getJars()) {
                                IClasspathEntry cpEntry = jarsToClasspathEntry(bazelWorkspace, progressMonitor, jarSet, isTestTarget);
                                if (cpEntry != null) {
                                    addOrUpdateClasspathEntry(bazelWorkspaceCmdRunner, targetLabel, cpEntry, isTestTarget, 
                                        mainClasspathEntryMap, testClasspathEntryMap);
                                } else {
                                    // there was a problem with the aspect computation, this might resolve itself if we recompute it
                                    bazelWorkspaceCmdRunner.flushAspectInfoCache(configuredTargetsForProject.getConfiguredTargets());
                                }
                            }
                        } else { // otherProject != null 
                            String thisFullPath = eclipseIProject.getFullPath().toOSString();
                            String otherFullPath = otherProject.getProject().getFullPath().toOSString(); 
                            if (thisFullPath.equals(otherFullPath)) {
                                // the project referenced is actually the the current project that this classpath container is for
                                
                                // some rule types have hidden dependencies that we need to add
                                // if our Eclipse project has any of those rules, we need to add in the dependencies to our classpath
                                Set<IClasspathEntry> implicitDeps = implicitDependencyHelper.computeImplicitDependencies(eclipseIProject, bazelWorkspace, packageInfo);
                                for (IClasspathEntry implicitDep : implicitDeps) {
                                    addOrUpdateClasspathEntry(bazelWorkspaceCmdRunner, targetLabel, implicitDep, isTestTarget, 
                                        mainClasspathEntryMap, testClasspathEntryMap);
                                }
                                
                            } else {
                                
                                // add the referenced project to the classpath, directly as a project classpath entry
                                IPath projectFullPath = otherProject.getProject().getFullPath();
                                if (!projectsAddedToClasspath.contains(projectFullPath)) {
                                    IClasspathEntry cpEntry = BazelPluginActivator.getJavaCoreHelper().newProjectEntry(projectFullPath);
                                    addOrUpdateClasspathEntry(bazelWorkspaceCmdRunner, targetLabel, cpEntry, isTestTarget, 
                                        mainClasspathEntryMap, testClasspathEntryMap);
                                }
                                projectsAddedToClasspath.add(projectFullPath);
                                
                                // now make a project reference between this project and the other project; this allows for features like
                                // code refactoring across projects to work correctly
                                //addProjectReference(eclipseIProject, otherProject.getProject());
                                updatedProjectReferences.add(otherProject.getProject());
                                //System.out.println("Project ["+eclipseProjectName+"] now refers to project ["+otherProject.getProject().getName()+"]");
                            }
                        }
                    }
                } // for loop
                
                // now update project refs, which includes adding new ones and removing any that may now be obsolete 
                // (e.g. dep was removed, project removed from Eclipse workspace)
                setProjectReferences(eclipseIProject, updatedProjectReferences);
                
            } catch (IOException | InterruptedException e) {
                BazelPluginActivator.error("Unable to compute classpath containers entries for project "+bazelProject.name, e);
                return returnEmptyClasspathOrThrow(e);
            } catch (BazelCommandLineToolConfigurationException e) {
                BazelPluginActivator.error("Bazel not found: " + e.getMessage());
                return returnEmptyClasspathOrThrow(e);
            }
    
            // cache the entries
            this.cachePutTimeMillis = System.currentTimeMillis();
            this.cachedEntries = assembleClasspathEntries(mainClasspathEntryMap, testClasspathEntryMap);
            BazelPluginActivator.debug("Cached the classpath for project "+bazelProject.name);
        }
        
        SimplePerfRecorder.addTime("classpath", startTimeMS);
        
        return cachedEntries;
    }

    /**
     * Runs a build with the passed targets and returns true if no errors are returned.
     */
    public boolean isValid() throws BackingStoreException, IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        if (bazelWorkspace == null) {
            return false;
        }
        File bazelWorkspaceRootDirectory = bazelWorkspace.getBazelWorkspaceRootDirectory();
        if (bazelWorkspaceRootDirectory == null) {
            return false;
        }
        BazelCommandManager bazelCommandManager = BazelPluginActivator.getBazelCommandManager();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);
        
        if (bazelWorkspaceCmdRunner != null) {
            if (this.eclipseProjectIsRoot) {
                return true;
            }
            IProject eclipseIProject = (IProject)bazelProject.getProjectImpl();
            EclipseProjectBazelTargets targets = BazelProjectPreferences.getConfiguredBazelTargets(eclipseIProject.getProject(), false);
            List<BazelProblem> details = bazelWorkspaceCmdRunner.runBazelBuild(targets.getConfiguredTargets(), null, Collections.emptyList(), null, null);
            for (BazelProblem detail : details) {
                BazelPluginActivator.error(detail.toString());
            }
            return details.isEmpty();

        }
        return false;
    }

    // INTERNAL

    private void addOrUpdateClasspathEntry(BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner, String targetLabel, 
            IClasspathEntry cpEntry, boolean isTestTarget,  Map<String, IClasspathEntry> mainClasspathEntryMap, 
            Map<String, IClasspathEntry> testClasspathEntryMap) {
        if (cpEntry == null) {
            // something was wrong with the Aspect that described the entry, flush the cache
            bazelWorkspaceCmdRunner.flushAspectInfoCache(targetLabel);
            return;
        }

        String pathStr = cpEntry.getPath().toPortableString();
        //System.out.println("Adding cp entry ["+pathStr+"] to target ["+targetLabel+"]");
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

    private IClasspathEntry[] assembleClasspathEntries(Map<String, IClasspathEntry> mainClasspathEntryMap, 
            Map<String, IClasspathEntry> testClasspathEntryMap) {
        List<IClasspathEntry> classpathEntries = new ArrayList<>();
        classpathEntries.addAll(mainClasspathEntryMap.values());
        classpathEntries.addAll(testClasspathEntryMap.values());

        return classpathEntries.toArray(new IClasspathEntry[] {});
    }
    /**
     * Returns the IJavaProject in the current workspace that contains at least one of the specified sources.
     */
    private IJavaProject getSourceProjectForSourcePaths(BazelWorkspaceCommandRunner bazelCommandRunner, List<String> sources) {
        for (String candidate : sources) {
            IJavaProject project = getSourceProjectForSourcePath(bazelCommandRunner, candidate);
            if (project != null) {
                return project;
            }
        }
        return null;
    }

    private IJavaProject getSourceProjectForSourcePath(BazelWorkspaceCommandRunner bazelCommandRunner, String sourcePath) {

        IWorkspaceRoot eclipseWorkspaceRoot = this.resourceHelper.getEclipseWorkspaceRoot();
        Collection<BazelProject> bazelProjects = bazelProject.bazelProjectManager.getAllProjects();

        String canonicalSourcePathString = BazelPathHelper.getCanonicalPathStringSafely(bazelWorkspace.getBazelWorkspaceRootDirectory()) + File.separator + sourcePath;
        Path canonicalSourcePath = new File(canonicalSourcePathString).toPath();

        for (BazelProject project : bazelProjects) {
        	IProject iProject = (IProject)project.getProjectImpl();
            IJavaProject jProject = BazelPluginActivator.getJavaCoreHelper().getJavaProjectForProject(iProject);
            IClasspathEntry[] classpathEntries = BazelPluginActivator.getJavaCoreHelper().getRawClasspath(jProject);
            if (classpathEntries == null) {
                BazelPluginActivator.error("No classpath entries found for project ["+jProject.getElementName()+"]");
                continue;
            }
            for (IClasspathEntry entry : classpathEntries) {
                if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
                    continue;
                }
                IResource res = this.resourceHelper.findMemberInWorkspace(eclipseWorkspaceRoot, entry.getPath());
                if (res == null) {
                    continue;
                }
                IPath projectLocation = res.getLocation();
                if (projectLocation != null && !projectLocation.isEmpty()) {
                    String canonicalProjectRoot = BazelPathHelper.getCanonicalPathStringSafely(projectLocation.toOSString());
                    if (canonicalSourcePathString.startsWith(canonicalProjectRoot)) {
                        IPath[] inclusionPatterns = entry.getInclusionPatterns();
                        IPath[] exclusionPatterns = entry.getExclusionPatterns();
                        if (!matchPatterns(canonicalSourcePath, exclusionPatterns)) {
                            if (inclusionPatterns == null || inclusionPatterns.length == 0 || matchPatterns(canonicalSourcePath, inclusionPatterns)) {
                                return jProject;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Globby match of file system patterns for a given path. If the path matches any of the patterns, this method
     * returns true.
     */
    private boolean matchPatterns(Path path, IPath[] patterns) {
        if (patterns != null) {
            for (IPath p : patterns) {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + p.toOSString());
                if (matcher.matches(path)) {
                    return true;
                }
            }
        }
        return false;
    }

    private IClasspathEntry jarsToClasspathEntry(BazelWorkspace bazelWorkspace, WorkProgressMonitor progressMonitor, 
            AspectOutputJarSet jarSet, boolean isTestLib) {
        IClasspathEntry cpEntry = null;
        File bazelOutputBase = bazelWorkspace.getBazelOutputBaseDirectory();
        File bazelExecRoot = bazelWorkspace.getBazelExecRootDirectory();
        IPath jarPath = getJarPathOnDisk(bazelOutputBase, bazelExecRoot, jarSet.getJar());
        if (jarPath != null) {
            IPath srcJarPath = getJarPathOnDisk(bazelOutputBase, bazelExecRoot, jarSet.getSrcJar());
            IPath srcJarRootPath = null;
            cpEntry = BazelPluginActivator.getJavaCoreHelper().newLibraryEntry(jarPath, srcJarPath, srcJarRootPath, isTestLib);
        }
        return cpEntry;
    }

    @SuppressWarnings("unused")
    private IClasspathEntry[] jarsToClasspathEntries(BazelWorkspace bazelWorkspace, WorkProgressMonitor progressMonitor, 
            Set<AspectOutputJarSet> jars, boolean isTestLib) {
        IClasspathEntry[] entries = new IClasspathEntry[jars.size()];
        int i = 0;
        File bazelOutputBase = bazelWorkspace.getBazelOutputBaseDirectory();
        File bazelExecRoot = bazelWorkspace.getBazelExecRootDirectory();
        for (AspectOutputJarSet j : jars) {
            IPath jarPath = getJarPathOnDisk(bazelOutputBase, bazelExecRoot, j.getJar());
            if (jarPath != null) {
                IPath srcJarPath = getJarPathOnDisk(bazelOutputBase, bazelExecRoot, j.getSrcJar());
                IPath srcJarRootPath = null;
                entries[i] = BazelPluginActivator.getJavaCoreHelper().newLibraryEntry(jarPath, srcJarPath, srcJarRootPath, isTestLib);
                i++;
            }
        }
        return entries;
    }

    private IPath getJarPathOnDisk(File bazelOutputBase, File bazelExecRoot, String file) {
        if (file == null) {
            return null;
        }
        Path path = null;
        if (file.startsWith("external")) {
            path = Paths.get(bazelOutputBase.toString(), file);
        } else {
            path = Paths.get(bazelExecRoot.toString(), file);
        }

        // We have had issues with Eclipse complaining about symlinks in the Bazel output directories not being real,
        // so we resolve them before handing them back to Eclipse.
        if (Files.isSymbolicLink(path)) {
            try {
                // resolving the link will fail if the symlink does not a point to a real file
                path = Files.readSymbolicLink(path);
            } catch (IOException ex) {
                // TODO this can happen if someone does a 'bazel clean' using the command line #113
                // https://github.com/salesforce/bazel-eclipse/issues/113
                BazelPluginActivator.error("Problem adding jar to project ["+bazelProject.name+"] because it does not exist on the filesystem: "+path);
                printDirectoryDiagnostics(path.toFile().getParentFile().getParentFile(), " ");
                continueOrThrow(ex);
            }
        } else {
            // it is a normal path, check for existence
            if (!Files.exists(path)) {
                // TODO this can happen if someone does a 'bazel clean' using the command line #113
                // https://github.com/salesforce/bazel-eclipse/issues/113
                BazelPluginActivator.error("Problem adding jar to project ["+bazelProject.name+"] because it does not exist on the filesystem: "+path);
                printDirectoryDiagnostics(path.toFile().getParentFile().getParentFile(), " ");
            }
        }
        return org.eclipse.core.runtime.Path.fromOSString(path.toString());
    }

    /**
     * Creates a project reference between this project and that project.
     * The direction of reference goes from this->that
     * References are used by Eclipse code refactoring among other things.
     * This is now unused, prefer the setProjectReferences because it can handle removal of
     * references not just adds. 
     */
    @SuppressWarnings("unused")
    private void addProjectReference(IProject thisProject, IProject thatProject) {
        IProjectDescription projectDescription = this.resourceHelper.getProjectDescription(thisProject);
        IProject[] existingRefsArray = projectDescription.getReferencedProjects();
        boolean hasRef = false;
        String otherProjectName = thatProject.getName();
        for (IProject candidateRef : existingRefsArray) {
            if (candidateRef.getName().equals(otherProjectName)) {
                hasRef = true;
                break;
            }
        }
        if (!hasRef) {
            // this project does not already reference the other project, we need to add the project reference
            // as this make code refactoring across Eclipse projects work correctly (among other things)
            List<IProject> updatedRefList = new ArrayList<>(Arrays.asList(existingRefsArray));
            updatedRefList.add(thatProject.getProject());
            
            // The next two lines are wrapped in an exception handler because the first time
            // called on a new workspace, a RuntimeException is thrown which causes the BazelClasspathContainer
            // to get into an incorrect state that it can't recover from unless eclipse is restarted.
            // The error is thrown by at org.eclipse.jface.viewers.ColumnViewer.checkBusy(ColumnViewer.java:764)
            // asyncExec might help here, but need a display widget to call on
            // Ignoring the error allows the classpath container to recover and the user does not know
            // there ever was a problem.
            try {
                projectDescription.setReferencedProjects(updatedRefList.toArray(new IProject[] {}));
                resourceHelper.setProjectDescription(thisProject, projectDescription);
            } catch(RuntimeException ex) {
                // potential cause: org.eclipse.core.internal.resources.ResourceException: The resource tree is locked for modifications.
                // if that is happening in your code path, see ResourceHelper.applyDeferredProjectDescriptionUpdates()
                System.err.println("Caught RuntimeException updating project: " + thisProject.toString());
                ex.printStackTrace();
                continueOrThrow(ex);
            }
        }
    }

    /**
     * Creates a project reference between this project and a set of other projects.
     * References are used by Eclipse code refactoring among other things. 
     * The direction of reference goes from this->updatedRefList
     * If this project no longer uses another project, removing it from the list will eliminate the project reference.
     */
    private void setProjectReferences(IProject thisProject, List<IProject> updatedRefList) {
        IProjectDescription projectDescription = this.resourceHelper.getProjectDescription(thisProject);
        // The next two lines are wrapped in an exception handler because the first time
        // called on a new workspace, a RuntimeException is thrown which causes the BazelClasspathContainer
        // to get into an incorrect state that it can't recover from unless eclipse is restarted.
        // The error is thrown by at org.eclipse.jface.viewers.ColumnViewer.checkBusy(ColumnViewer.java:764)
        // asyncExec might help here, but need a display widget to call on
        // Ignoring the error allows the classpath container to recover and the user does not know
        // there ever was a problem.
        try {
            projectDescription.setReferencedProjects(updatedRefList.toArray(new IProject[] {}));
            resourceHelper.setProjectDescription(thisProject, projectDescription);
        } catch(RuntimeException ex) {
            // potential cause: org.eclipse.core.internal.resources.ResourceException: The resource tree is locked for modifications.
            // if that is happening in your code path, see ResourceHelper.applyDeferredProjectDescriptionUpdates()
            System.err.println("Caught RuntimeException updating project: " + thisProject.toString());
            ex.printStackTrace();
            continueOrThrow(ex);
        }
    }

    
    private static int diagnosticsCount = 0;
    private static void printDirectoryDiagnostics(File path, String indent) {
        if (diagnosticsCount > 3) {
            // only do this a few times so we know there are problems but then be silent, 
            //   otherwise a shutdown with lots of projects open can take forever
            return;
        }
        diagnosticsCount++;
        
        File[] children = path.listFiles();
        System.out.println(indent+BazelPathHelper.getCanonicalPathStringSafely(path));
        if (children != null) {
            for (File child : children) {
                System.out.println(indent+child.getName());
                if (child.isDirectory()) {
                    printDirectoryDiagnostics(child, "   "+indent);
                }
            }
        } 
        
    }
    
    private static void continueOrThrow(Throwable th) {
        // under real usage, we suppress fatal exceptions because sometimes there are IDE timing issues that can
        // be corrected if the classpath is computed again.
        // But under tests, we want to fail fatally otherwise tests could pass when they shouldn't
        if (BazelPluginActivator.getInstance().getOperatingEnvironmentDetectionStrategy().isTestRuntime()) {
            throw new IllegalStateException("The classpath could not be computed by the BazelClasspathContainer", th);
        }
    }
    
    private static IClasspathEntry[] returnEmptyClasspathOrThrow(Throwable th) {
        continueOrThrow(th);
        return new IClasspathEntry[] {};  
    }

}
