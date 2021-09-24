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
package com.salesforce.bazel.eclipse.config;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.BazelProjectTargets;

public class EclipseBazelProjectManager extends BazelProjectManager {

    private final ResourceHelper resourceHelper;
    private final JavaCoreHelper javaCoreHelper;
    private final LogHelper logger;

    public EclipseBazelProjectManager(ResourceHelper resourceHelper, JavaCoreHelper javaCoreHelper) {
        this.resourceHelper = resourceHelper;
        this.javaCoreHelper = javaCoreHelper;
        logger = LogHelper.log(this.getClass());
    }

    @Override
    public BazelProject getOwningProjectForSourcePath(BazelWorkspace bazelWorkspace, String sourcePath) {
        Collection<BazelProject> bazelProjects = getAllProjects();

        String canonicalSourcePathString =
                FSPathHelper.getCanonicalPathStringSafely(bazelWorkspace.getBazelWorkspaceRootDirectory())
                + File.separator + sourcePath;
        Path canonicalSourcePath = new File(canonicalSourcePathString).toPath();

        for (BazelProject candidateProject : bazelProjects) {
            IProject iProject = (IProject) candidateProject.getProjectImpl();
            IJavaProject jProject = javaCoreHelper.getJavaProjectForProject(iProject);
            IClasspathEntry[] classpathEntries = javaCoreHelper.getRawClasspath(jProject);
            if (classpathEntries == null) {
                logger.error("No classpath entries found for project [" + jProject.getElementName() + "]");
                continue;
            }
            for (IClasspathEntry entry : classpathEntries) {
                if (entry.getEntryKind() != IClasspathEntry.CPE_SOURCE) {
                    continue;
                }
                IResource res = resourceHelper.findMemberInWorkspace(entry.getPath());
                if (res == null) {
                    continue;
                }
                IPath projectLocation = res.getLocation();
                if ((projectLocation != null) && !projectLocation.isEmpty()) {
                    String canonicalProjectRoot =
                            FSPathHelper.getCanonicalPathStringSafely(projectLocation.toOSString());
                    if (canonicalSourcePathString.startsWith(canonicalProjectRoot)) {
                        IPath[] inclusionPatterns = entry.getInclusionPatterns();
                        IPath[] exclusionPatterns = entry.getExclusionPatterns();
                        if (!matchPatterns(canonicalSourcePath, exclusionPatterns) && ((inclusionPatterns == null) || (inclusionPatterns.length == 0)
                                || matchPatterns(canonicalSourcePath, inclusionPatterns))) {
                            return candidateProject;
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

    @Override
    public void setProjectReferences(BazelProject thisProject, List<BazelProject> updatedRefList) {
        IProject thisEclipseProject = (IProject) thisProject.getProjectImpl();
        IProjectDescription projectDescription = resourceHelper.getProjectDescription(thisEclipseProject);

        IProject[] existingEclipseRefList = projectDescription.getReferencedProjects();
        IProject[] updatedEclipseRefList = new IProject[updatedRefList.size()];
        int i = 0;
        for (BazelProject ref : updatedRefList) {
            updatedEclipseRefList[i] = (IProject) ref.getProjectImpl();
            i++;
        }

        // setProjectDescription requires a lock and should cause a rebuild on the project so only do it if necessary
        if (!areDifferent(existingEclipseRefList, updatedEclipseRefList)) {
            return;
        }

        projectDescription.setReferencedProjects(updatedEclipseRefList);
        resourceHelper.setProjectDescription(thisEclipseProject, projectDescription);
    }

    /**
     * Returns true if the arrays of projects contain different projects
     */
    private boolean areDifferent(IProject[] list1, IProject[] list2) {
        if (list1.length != list2.length) {
            return true;
        }
        for (IProject p1 : list1) {
            boolean found = false;
            for (IProject p2 : list2) {
                if (p1.getName().equals(p2.getName())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return true;
            }
        }
        return false;
    }

    /**
     * The label that identifies the Bazel package that represents this Eclipse project. This will be the 'module' label
     * when we start supporting multiple BUILD files in a single 'module'. Example: //projects/libs/foo See
     * https://github.com/salesforce/bazel-eclipse/issues/24
     */
    @Override
    public String getBazelLabelForProject(BazelProject bazelProject) {
        IProject eclipseProject = (IProject) bazelProject.getProjectImpl();
        Preferences eclipseProjectBazelPrefs = resourceHelper.getProjectBazelPreferences(eclipseProject);
        return eclipseProjectBazelPrefs.get(IEclipseBazelProjectSettings.PROJECT_PACKAGE_LABEL, null);
    }

    /**
     * Returns a map that maps Bazel labels to their Eclipse projects
     */
    @Override
    public Map<BazelLabel, BazelProject> getBazelLabelToProjectMap(Collection<BazelProject> bazelProjects) {
        Map<BazelLabel, BazelProject> labelToProject = new HashMap<>();
        for (BazelProject bazelProject : bazelProjects) {
            BazelProjectTargets activatedTargets = getConfiguredBazelTargets(bazelProject, false);
            List<BazelLabel> labels =
                    activatedTargets.getConfiguredTargets().stream().map(BazelLabel::new).collect(Collectors.toList());
            for (BazelLabel label : labels) {
                labelToProject.merge(label, bazelProject, (k1, k2) -> {
                    throw new IllegalStateException("Duplicate label: " + label + " - this is bug");
                });
            }
        }
        return labelToProject;
    }

    /**
     * List the Bazel targets the user has chosen to activate for this Eclipse project. Each project configured for
     * Bazel is configured to track certain targets and this function fetches this list from the project preferences.
     * After initial import, this will be just the wildcard target (:*) which means all targets are activated. This is
     * the safest choice as new targets that are added to the BUILD file will implicitly get picked up. But users may
     * choose to be explicit if one or more targets in a BUILD file is not needed for development.
     * <p>
     * By contract, this method will return only one target if the there is a wildcard target, even if the user does
     * funny things in their prefs file and sets multiple targets along with the wildcard target.
     */
    @Override
    public BazelProjectTargets getConfiguredBazelTargets(BazelProject bazelProject, boolean addWildcardIfNoTargets) {
        IProject eclipseProject = (IProject) bazelProject.getProjectImpl();
        Preferences eclipseProjectBazelPrefs = resourceHelper.getProjectBazelPreferences(eclipseProject);
<<<<<<< HEAD
        String projectLabel = eclipseProjectBazelPrefs.get(PROJECT_PACKAGE_LABEL, null);
        BazelProjectTargets activatedTargets = null;

        if (projectLabel == null) {
            // there are no preferences, so just pick the default target(s) for this project
            // TODO this is a huge mess; we don't have the right abstractions for mapping packages to projects
            List<BazelLabel> projectLabels = bazelProject.getProjectStructure().getBazelTargets();
            if ((projectLabels != null) && (projectLabels.size() > 0)) {
                activatedTargets =
                        new BazelProjectTargets(bazelProject, projectLabels.get(0).getPackagePath());
                activatedTargets.activateWildcardTarget(BazelLabel.BAZEL_WILDCARD_ALLTARGETS);
                return activatedTargets;
            }
            // TODO
            return null;
        }
=======
        String projectLabel = eclipseProjectBazelPrefs.get(IEclipseBazelProjectSettings.PROJECT_PACKAGE_LABEL, null);
>>>>>>> 521fa92 (1. extension point for BazelProjectManager)

        activatedTargets = new BazelProjectTargets(bazelProject, projectLabel);
        boolean addedTarget = false;
        Set<String> activeTargets = new TreeSet<>();
<<<<<<< HEAD
        String[] prefNames = getKeys(eclipseProjectBazelPrefs);
        for (String propertyName : prefNames) {
            if (propertyName.startsWith(TARGET_PROPERTY_PREFIX)) {
=======
        for (String propertyName : getKeys(eclipseProjectBazelPrefs)) {
            if (propertyName.startsWith(IEclipseBazelProjectSettings.TARGET_PROPERTY_PREFIX)) {
>>>>>>> 521fa92 (1. extension point for BazelProjectManager)
                String target = eclipseProjectBazelPrefs.get(propertyName, "");
                if (!target.isEmpty()) {
                    BazelLabel label = new BazelLabel(target);
                    if (!label.getLabelPath().startsWith(projectLabel)) {
                        // the user jammed in a label not associated with this project, ignore
                        //continue;
                    }
                    if (!label.isConcrete()) {
                        // we have a wildcard target, so discard any existing targets we gathered (if the user messed up their prefs)
                        // and just go with that.
                        activatedTargets.activateWildcardTarget(label.getTargetName());
                        return activatedTargets;
                    }
                    activeTargets.add(target);
                    addedTarget = true;
                }
            }
        }
        if (!addedTarget && addWildcardIfNoTargets) {
            activeTargets.add("//...");
        }

        activatedTargets.activateSpecificTargets(activeTargets);

        return activatedTargets;
    }

    /**
     * List of Bazel build flags for this Eclipse project, taken from the project configuration
     */
    @Override
    public List<String> getBazelBuildFlagsForProject(BazelProject bazelProject) {
        IProject eclipseProject = (IProject) bazelProject.getProjectImpl();
        Preferences eclipseProjectBazelPrefs = resourceHelper.getProjectBazelPreferences(eclipseProject);

        List<String> listBuilder = new ArrayList<>();
        for (String property : getKeys(eclipseProjectBazelPrefs)) {
            if (property.startsWith(IEclipseBazelProjectSettings.BUILDFLAG_PROPERTY_PREFIX)) {
                listBuilder.add(eclipseProjectBazelPrefs.get(property, ""));
            }
        }
        return listBuilder;
    }

    @Override
    public void addSettingsToProject(BazelProject bazelProject, String bazelWorkspaceRoot, String packageFSPath,
            List<BazelLabel> bazelTargets, List<String> bazelBuildFlags) {

        IProject eclipseProject = (IProject) bazelProject.getProjectImpl();
        Preferences eclipseProjectBazelPrefs = resourceHelper.getProjectBazelPreferences(eclipseProject);

        eclipseProjectBazelPrefs.put(IEclipseBazelProjectSettings.BAZEL_WORKSPACE_ROOT_ABSPATH_PROPERTY,
            bazelWorkspaceRoot);

        // convert file system path to bazel path; for linux/macos the slashes are already fine,
        // this is only a thing for windows
        String bazelPackagePath = packageFSPath.replace(FSPathHelper.WINDOWS_BACKSLASH, "/");

        if (!bazelPackagePath.startsWith("//")) {
            bazelPackagePath = "//" + bazelPackagePath;
        }
        eclipseProjectBazelPrefs.put(IEclipseBazelProjectSettings.PROJECT_PACKAGE_LABEL, bazelPackagePath);

        int i = 0;
        for (BazelLabel bazelTarget : bazelTargets) {
            eclipseProjectBazelPrefs.put(IEclipseBazelProjectSettings.TARGET_PROPERTY_PREFIX + i,
                bazelTarget.getLabelPath());
            i++;
        }
        i = 0;
        for (String bazelBuildFlag : bazelBuildFlags) {
            eclipseProjectBazelPrefs.put(IEclipseBazelProjectSettings.BUILDFLAG_PROPERTY_PREFIX + i, bazelBuildFlag);
            i++;
        }
        try {
            eclipseProjectBazelPrefs.flush();
        } catch (Exception anyE) {
            throw new RuntimeException(anyE);
        }
    }

    // HELPERS

    private static String[] getKeys(Preferences prefs) {
        try {
            return prefs.keys();
        } catch (BackingStoreException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
