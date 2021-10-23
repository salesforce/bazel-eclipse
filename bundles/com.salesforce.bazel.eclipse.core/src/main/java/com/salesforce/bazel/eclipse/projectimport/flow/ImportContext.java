/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
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
 */
package com.salesforce.bazel.eclipse.projectimport.flow;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.resources.IProject;

import com.salesforce.bazel.eclipse.project.EclipseFileLinker;
import com.salesforce.bazel.eclipse.project.EclipseProjectCreator;
import com.salesforce.bazel.eclipse.project.EclipseProjectStructureInspector;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfos;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.project.structure.ProjectStructure;
import com.salesforce.bazel.sdk.workspace.ProjectOrderResolver;

/**
 * Keeps track of state passed between the various Flow implementations.
 */
public class ImportContext {

    private final BazelPackageLocation bazelWorkspaceRootPackageInfo;
    private final List<BazelPackageLocation> selectedBazelPackages;
    private final ProjectOrderResolver projectOrderResolver;
    private final Map<String, ProjectStructure> ProjectSourceStructureCache = new HashMap<>();

    private final List<IProject> importedProjects = new ArrayList<>();
    private final Map<IProject, BazelPackageLocation> projectToPackageLocation = new HashMap<>();
    private IProject rootProject;

    private File bazelWorkspaceRootDirectory;
    private Integer javaLanguageLevel;
    private AspectTargetInfos aspectTargetInfos;
    private Map<BazelPackageLocation, List<BazelLabel>> packageLocationToTargets;
    private Iterable<BazelPackageLocation> orderedModules;

    private EclipseProjectCreator eclipseProjectCreator;
    private EclipseFileLinker eclipseFileLinker;

    ImportContext(BazelPackageLocation bazelWorkspaceRootPackageInfo, List<BazelPackageLocation> selectedBazelPackages,
            ProjectOrderResolver projectOrderResolver) {
        this.bazelWorkspaceRootPackageInfo = Objects.requireNonNull(bazelWorkspaceRootPackageInfo);
        this.selectedBazelPackages = Objects.requireNonNull(selectedBazelPackages);
        this.projectOrderResolver = Objects.requireNonNull(projectOrderResolver);
    }

    public void init(File bazelWorkspaceRootDirectory) {
        this.bazelWorkspaceRootDirectory = Objects.requireNonNull(bazelWorkspaceRootDirectory);
        eclipseProjectCreator = new EclipseProjectCreator(bazelWorkspaceRootDirectory);
        eclipseFileLinker = new EclipseFileLinker(bazelWorkspaceRootDirectory);
    }

    public BazelPackageLocation getBazelWorkspaceRootPackageInfo() {
        return bazelWorkspaceRootPackageInfo;
    }

    public List<BazelPackageLocation> getSelectedBazelPackages() {
        return selectedBazelPackages;
    }

    public Map<BazelPackageLocation, List<BazelLabel>> getPackageLocationToTargets() {
        return packageLocationToTargets;
    }

    public void setPackageLocationToTargets(Map<BazelPackageLocation, List<BazelLabel>> packageLocationToTargets) {
        this.packageLocationToTargets = packageLocationToTargets;
    }

    public ProjectOrderResolver getProjectOrderResolver() {
        return projectOrderResolver;
    }

    public File getBazelWorkspaceRootDirectory() {
        return bazelWorkspaceRootDirectory;
    }

    public Integer getJavaLanguageLevel() {
        return javaLanguageLevel;
    }

    public void setJavaLanguageLevel(int javaLanguageLevel) {
        this.javaLanguageLevel = javaLanguageLevel;
    }

    public void setRootProject(IProject rootProject) {
        this.rootProject = rootProject;
    }

    /**
     * Returns the special root (WORKSPACE level) project.
     */
    public IProject getRootProject() {
        return rootProject;
    }

    public void addImportedProject(IProject project, BazelPackageLocation bazelPackageLocation) {
        importedProjects.add(project);
        projectToPackageLocation.put(project, bazelPackageLocation);
    }

    /**
     * Returns all imported projects EXCEPT the special root (WORKSPACE level) project.
     */
    public List<IProject> getImportedProjects() {
        return importedProjects;
    }

    /**
     * Returns all imported projects including the special root (WORKSPACE level) project.
     */
    public List<IProject> getAllImportedProjects() {
        List<IProject> projects = new ArrayList<>(importedProjects.size() + 1);
        projects.addAll(importedProjects);
        if (rootProject != null) {
            projects.add(rootProject);
        }
        return projects;
    }

    public BazelPackageLocation getPackageLocationForProject(IProject project) {
        return projectToPackageLocation.get(project);
    }

    public AspectTargetInfos getAspectTargetInfos() {
        return aspectTargetInfos;
    }

    public void setAspectTargetInfos(AspectTargetInfos aspectTargetInfos) {
        this.aspectTargetInfos = aspectTargetInfos;
    }

    public Iterable<BazelPackageLocation> getOrderedModules() {
        return orderedModules;
    }

    public void setOrderedModules(Iterable<BazelPackageLocation> orderedModules) {
        this.orderedModules = orderedModules;
    }

    public EclipseProjectCreator getEclipseProjectCreator() {
        return eclipseProjectCreator;
    }

    public EclipseFileLinker getEclipseFileLinker() {
        return eclipseFileLinker;
    }

    /**
     * The importer flows will look up the structure of each Eclipse project multiple times during import. They are
     * expensive to compute, and so caching them for the duration of import is a big benefit.
     */
    public ProjectStructure getProjectStructure(BazelPackageLocation packageNode) {
        String cacheKey = packageNode.getBazelPackageFSRelativePath();
        ProjectStructure structure = ProjectSourceStructureCache.get(cacheKey);

        if (structure == null) {
            structure = EclipseProjectStructureInspector.computePackageSourceCodePaths(packageNode);
            ProjectSourceStructureCache.put(cacheKey, structure);
        }

        return structure;
    }
}
