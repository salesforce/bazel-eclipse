/**
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.osgi.service.prefs.BackingStoreException;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.abstractions.WorkProgressMonitor;
import com.salesforce.bazel.eclipse.command.BazelCommandFacade;
import com.salesforce.bazel.eclipse.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.eclipse.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.eclipse.config.BazelEclipseProjectSupport;
import com.salesforce.bazel.eclipse.model.AspectOutputJars;
import com.salesforce.bazel.eclipse.model.AspectPackageInfo;
import com.salesforce.bazel.eclipse.model.BazelMarkerDetails;
import com.salesforce.bazel.eclipse.runtime.EclipseWorkProgressMonitor;
import com.salesforce.bazel.eclipse.runtime.ResourceHelper;

/**
 * Computes the classpath for a Bazel package and provides it to the JDT tooling in Eclipse.
 */
public class BazelClasspathContainer implements IClasspathContainer {
    public static final String CONTAINER_NAME = "com.salesforce.bazel.eclipse.BAZEL_CONTAINER";

    private final IPath eclipseProjectPath;
    private final IProject eclipseProject;
    private final IJavaProject eclipseJavaProject;
    private final String eclipseProjectName;
    
    public BazelClasspathContainer(IProject eclipseProject, IJavaProject eclipseJavaProject)
            throws IOException, InterruptedException, BackingStoreException, JavaModelException,
            BazelCommandLineToolConfigurationException {
        this.eclipseProject = eclipseProject;
        this.eclipseJavaProject = eclipseJavaProject;
        this.eclipseProjectName = eclipseProject.getName();
        this.eclipseProjectPath = eclipseProject.getLocation();
    }

    @Override
    public IClasspathEntry[] getClasspathEntries() {
        // sanity check
        if (!BazelPluginActivator.hasBazelWorkspaceRootDirectory()) {
            throw new IllegalStateException("Attempt to retrieve the classpath of a Bazel Java project prior to setting up the Bazel workspace.");
        }

        // TODO cache results for a limited time period, this gets called a many times during import on each project
        
        // TODO figure out a way to get access to an Eclipse progress monitor here
        WorkProgressMonitor progressMonitor = new EclipseWorkProgressMonitor(null);

        if (this.eclipseJavaProject.getElementName().startsWith("Bazel Workspace")) {
            // this project is the artificial container to hold Bazel workspace scoped assets (e.g. the WORKSPACE file)
            return new IClasspathEntry[] {};
        }

        List<IClasspathEntry> classpathEntries = new ArrayList<>();
        Set<IPath> projectsAddedToClasspath = new HashSet<>();

        BazelCommandFacade commandFacade = BazelPluginActivator.getBazelCommandFacade();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = commandFacade.getWorkspaceCommandRunner(BazelPluginActivator.getBazelWorkspaceRootDirectory());
        
        try {
            IProject eclipseIProject = eclipseProject.getProject();
            List<String> bazelTargetsForProject = BazelEclipseProjectSupport.getBazelTargetsForEclipseProject(eclipseIProject, false);
            
            Map<String, AspectPackageInfo> packageInfos = bazelWorkspaceCmdRunner.getAspectPackageInfos(
                eclipseIProject.getName(), bazelTargetsForProject, progressMonitor, "getClasspathEntries");

            for (AspectPackageInfo packageInfo : packageInfos.values()) {
                IJavaProject otherProject = getSourceProjectForSourcePaths(bazelWorkspaceCmdRunner, packageInfo.getSources());
                
                if (otherProject == null) {
                    // no project found that houses the sources of this bazel target, add the jars to the classpath
                    // this means that this is an external jar, or a jar produced by a bazel target that was not imported
                    for (AspectOutputJars jarSet : packageInfo.getGeneratedJars()) {
                        classpathEntries.add(jarsToClasspathEntry(bazelWorkspaceCmdRunner, progressMonitor, jarSet));
                    }
                    for (AspectOutputJars jarSet : packageInfo.getJars()) {
                        classpathEntries.add(jarsToClasspathEntry(bazelWorkspaceCmdRunner, progressMonitor, jarSet));
                    }
                } else if (eclipseProject.getProject().getFullPath().equals(otherProject.getProject().getFullPath())) {
                    // the project referenced is actually the the current project that this classpath container is for - nothing to do
                } else {
                    // add the referenced project to the classpath, directly as a project classpath entry
                    IPath projectFullPath = otherProject.getProject().getFullPath();
                    if (!projectsAddedToClasspath.contains(projectFullPath)) {
                        classpathEntries.add(BazelPluginActivator.getJavaCoreHelper().newProjectEntry(projectFullPath));
                    }
                    projectsAddedToClasspath.add(projectFullPath);
                    
                    // now make a project reference between this project and the other project; this allows for features like
                    // code refactoring across projects to work correctly
                    addProjectReference(eclipseIProject, otherProject.getProject());
                }
            }
        } catch (IOException | InterruptedException e) {
            BazelPluginActivator.error("Unable to compute classpath containers entries for project "+eclipseProjectName, e);
            return new IClasspathEntry[] {};
        } catch (BazelCommandLineToolConfigurationException e) {
            BazelPluginActivator.error("Bazel not found: " + e.getMessage());
            return new IClasspathEntry[] {};
        }

        return classpathEntries.toArray(new IClasspathEntry[] {});
    }

    @Override
    public String getDescription() {
        return "Bazel Classpath Container";
    }

    @Override
    public int getKind() {
        return K_APPLICATION;
    }

    @Override
    public IPath getPath() {
        return eclipseProjectPath;
    }

    public boolean isValid() throws BackingStoreException, IOException, InterruptedException, BazelCommandLineToolConfigurationException {
        File bazelWorkspaceRootDirectory = BazelPluginActivator.getBazelWorkspaceRootDirectory();
        if (bazelWorkspaceRootDirectory == null) {
            return false;
        }
        BazelCommandFacade bazelCommandFacade = BazelPluginActivator.getBazelCommandFacade();
        BazelWorkspaceCommandRunner bazelWorkspaceCmdRunner = bazelCommandFacade.getWorkspaceCommandRunner(bazelWorkspaceRootDirectory);
        
        if (bazelWorkspaceCmdRunner != null) {
            if (this.eclipseProject.getName().startsWith("Bazel Workspace")) {
                return true;
            }
            List<String> targets =
                    BazelEclipseProjectSupport.getBazelTargetsForEclipseProject(this.eclipseProject.getProject(), false);
            List<BazelMarkerDetails> details = bazelWorkspaceCmdRunner.runBazelBuild(targets, null, Collections.emptyList());
            return details.isEmpty();

        }
        return false;
    }

    // INTERNAL

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

        // TODO this code is messy, why get workspace root two different ways, and is there a better way to handle source paths?
        ResourceHelper resourceHelper = BazelPluginActivator.getResourceHelper();
        IWorkspaceRoot eclipseWorkspaceRoot = resourceHelper.getEclipseWorkspaceRoot();
        IWorkspace eclipseWorkspace = resourceHelper.getEclipseWorkspace();
        IWorkspaceRoot rootResource = eclipseWorkspace.getRoot();
        IProject[] projects = rootResource.getProjects();

        String absoluteSourcePathString = BazelPluginActivator.getBazelWorkspaceRootDirectory().getAbsolutePath() + File.separator + sourcePath;
        Path absoluteSourcePath = new File(absoluteSourcePathString).toPath();

        for (IProject project : projects) {
            IJavaProject jProject = BazelPluginActivator.getJavaCoreHelper().getJavaProjectForProject(project);
            IClasspathEntry[] classpathEntries = BazelPluginActivator.getJavaCoreHelper().getRawClasspath(jProject);
            if (classpathEntries == null) {
                BazelPluginActivator.error("No classpath entries found for project ["+jProject.getElementName()+"]");
                continue;
            }
            for (IClasspathEntry entry : classpathEntries) {
                if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
                    continue;
                }
                IResource res = BazelPluginActivator.getResourceHelper().findMemberInWorkspace(eclipseWorkspaceRoot, entry.getPath());
                if (res == null) {
                    continue;
                }
                IPath projectLocation = res.getLocation();
                String absProjectRoot = projectLocation.toOSString();
                if (absProjectRoot != null && !absProjectRoot.isEmpty()) {
                    if (absoluteSourcePath.startsWith(absProjectRoot)) {
                        IPath[] inclusionPatterns = entry.getInclusionPatterns();
                        IPath[] exclusionPatterns = entry.getExclusionPatterns();
                        if (!matchPatterns(absoluteSourcePath, exclusionPatterns)) {
                            if (inclusionPatterns == null || inclusionPatterns.length == 0 || matchPatterns(absoluteSourcePath, inclusionPatterns)) {
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

    private IClasspathEntry jarsToClasspathEntry(BazelWorkspaceCommandRunner bazelCommandRunner, WorkProgressMonitor progressMonitor, AspectOutputJars jarSet) {
        File bazelExecRoot = bazelCommandRunner.getBazelWorkspaceExecRoot(progressMonitor);
        return BazelPluginActivator.getJavaCoreHelper().newLibraryEntry(getJarIPath(bazelExecRoot, jarSet.getJar()),
            getJarIPath(bazelExecRoot, jarSet.getSrcJar()), null);
    }

    @SuppressWarnings("unused")
    private IClasspathEntry[] jarsToClasspathEntries(BazelWorkspaceCommandRunner bazelCommandRunner, WorkProgressMonitor progressMonitor, Set<AspectOutputJars> jars) {
        IClasspathEntry[] entries = new IClasspathEntry[jars.size()];
        int i = 0;
        File bazelExecRoot = bazelCommandRunner.getBazelWorkspaceExecRoot(progressMonitor);
        for (AspectOutputJars j : jars) {
            entries[i] = BazelPluginActivator.getJavaCoreHelper().newLibraryEntry(getJarIPath(bazelExecRoot, j.getJar()),
                getJarIPath(bazelExecRoot, j.getSrcJar()), null);
            i++;
        }
        return entries;
    }

    private static IPath getJarIPath(File bazelExecRoot, String file) {
        if (file == null) {
            return null;
        }
        Path path = Paths.get(bazelExecRoot.toString(), file);

        // the jar file path can be a symlink, which, for an yet unknown reason, sometimes causes
        // errors, like the one right below, when multiple projects are imported that reference the same
        // jar (maven_jar)
        // java.io.IOException: Unable to read archive: /private/var/tmp/_bazel_stoens/345d6a8a19886aa7411ecaa6653241c2/execroot/__main__/external/org_apache_httpcomponents_httpcore/jar/httpcore-4.4.11.jar
        // at org.eclipse.jdt.internal.core.JavaModelManager.throwExceptionIfArchiveInvalid(JavaModelManager.java:2981)
        // at org.eclipse.jdt.internal.core.JavaModelManager.getZipFile(JavaModelManager.java:2918)
        // at org.eclipse.jdt.internal.core.JavaModelManager.getZipFile(JavaModelManager.java:2904)
        // at org.eclipse.jdt.internal.core.JarPackageFragmentRoot.getJar(JarPackageFragmentRoot.java:264)
        // at org.eclipse.jdt.internal.core.JarPackageFragmentRoot.computeChildren(JarPackageFragmentRoot.java:144)
        // at org.eclipse.jdt.internal.core.PackageFragmentRoot.buildStructure(PackageFragmentRoot.java:165)
        // ...
        // the workaround is to follow the symlink

        if (Files.isSymbolicLink(path)) {
            try {
                path = path.toRealPath();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return org.eclipse.core.runtime.Path.fromOSString(path.toString());
    }

    /**
     * Creates a project reference between this project and that project.
     * The direction of reference goes from this->that
     * References are used by Eclipse code refactoring among other things. 
     */
    private void addProjectReference(IProject thisProject, IProject thatProject) {
        ResourceHelper resourceHelper = BazelPluginActivator.getResourceHelper();
        IProjectDescription projectDescription =  resourceHelper.getProjectDescription(thisProject);
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
            projectDescription.setReferencedProjects(updatedRefList.toArray(new IProject[] {}));
            resourceHelper.setProjectDescription(thisProject, projectDescription);
        }
        
    }
    
}
