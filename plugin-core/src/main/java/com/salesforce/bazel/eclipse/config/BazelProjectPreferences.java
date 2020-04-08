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
package com.salesforce.bazel.eclipse.config;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

import com.google.common.collect.ImmutableList;
import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.command.BazelCommandManager;
import com.salesforce.bazel.eclipse.preferences.BazelPreferencePage;

// TODO migrate this away from static methods
public class BazelProjectPreferences { 
    /**
     * Absolute path of the Bazel workspace root 
     */
    private static final String BAZEL_WORKSPACE_ROOT_ABSPATH_PROPERTY = "bazel.workspace.root";
    
    /**
     * The label that identifies the Bazel package that represents this Eclipse project. This will
     * be the 'module' label when we start supporting multiple BUILD files in a single 'module'.
     * Example:  //projects/libs/foo
     * See https://github.com/salesforce/bazel-eclipse/issues/24
     */
    private static final String PROJECT_PACKAGE_LABEL = "bazel.package.label";
    
    /**
     * After import, the activated target is a single line, like: 
     *   bazel.activated.target0=//projects/libs/foo:*
     * which activates all targets by use of the wildcard. But users may wish to activate a subset
     * of the targets for builds, in which the prefs lines will look like:
     *   bazel.activated.target0=//projects/libs/foo:barlib
     *   bazel.activated.target1=//projects/libs/foo:bazlib
     */
    private static final String TARGET_PROPERTY_PREFIX = "bazel.activated.target";
    
    /**
     * Property that allows a user to set project specific build flags that get
     * passed to the Bazel executable.
     */
    private static final String BUILDFLAG_PROPERTY_PREFIX = "bazel.build.flag";

    /**
     */
    public static String getBazelExecutablePath(BazelPluginActivator activator) {
        IPreferenceStore prefsStore =  BazelPluginActivator.getResourceHelper().getPreferenceStore(activator);
        return prefsStore.getString(BazelPreferencePage.BAZEL_PATH_PREF_NAME);
    }
    
    public static void setBazelExecutablePathListener(BazelPluginActivator activator, BazelCommandManager bazelCommandManager) {
        IPreferenceStore prefsStore =  BazelPluginActivator.getResourceHelper().getPreferenceStore(activator);
        prefsStore.addPropertyChangeListener(new IPropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                if (event.getProperty().equals(BazelPreferencePage.BAZEL_PATH_PREF_NAME)) {
                    bazelCommandManager.setBazelExecutablePath(event.getNewValue().toString());
                }
            }
        });
    }

    /**
     */
    public static String getBazelWorkspacePath(BazelPluginActivator activator) {
        IPreferenceStore prefsStore =  BazelPluginActivator.getResourceHelper().getPreferenceStore(activator);
        return prefsStore.getString(BazelProjectPreferences.BAZEL_WORKSPACE_ROOT_ABSPATH_PROPERTY);
    }

    /**
     */
    public static void setBazelWorkspacePath(BazelPluginActivator activator, String bazelWorkspacePath) {
        IPreferenceStore prefsStore =  BazelPluginActivator.getResourceHelper().getPreferenceStore(activator);
        prefsStore.setValue(BazelProjectPreferences.BAZEL_WORKSPACE_ROOT_ABSPATH_PROPERTY, bazelWorkspacePath);
    }
    
    
    /**
     * The label that identifies the Bazel package that represents this Eclipse project. This will
     * be the 'module' label when we start supporting multiple BUILD files in a single 'module'.
     * Example:  //projects/libs/foo
     * See https://github.com/salesforce/bazel-eclipse/issues/24
     */
    public static String getBazelLabelForEclipseProject(IProject eclipseProject) {
        Preferences eclipseProjectBazelPrefs = BazelPluginActivator.getResourceHelper().getProjectBazelPreferences(eclipseProject);
        return eclipseProjectBazelPrefs.get(PROJECT_PACKAGE_LABEL, null);
    }

    
    /**
     * List the Bazel targets the user has chosen to activate for this Eclipse project. Each project configured 
     * for Bazel is configured to track certain targets and this function fetches this list from the project preferences.
     * After initial import, this will be just the wildcard target (:*) which means all targets are activated. This
     * is the safest choice as new targets that are added to the BUILD file will implicitly get picked up. But users
     * may choose to be explicit if one or more targets in a BUILD file is not needed for development.
     * <p>
     * By contract, this method will return only one target if the there is a wildcard target, even if the user does
     * funny things in their prefs file and sets multiple targets along with the wildcard target.
     */
    public static EclipseProjectBazelTargets getConfiguredBazelTargets(IProject eclipseProject, boolean addWildcardIfNoTargets) {
        Preferences eclipseProjectBazelPrefs = BazelPluginActivator.getResourceHelper().getProjectBazelPreferences(eclipseProject);
        String projectLabel = eclipseProjectBazelPrefs.get(PROJECT_PACKAGE_LABEL, null);

        EclipseProjectBazelTargets activatedTargets = new EclipseProjectBazelTargets(eclipseProject, projectLabel);
        
        boolean addedTarget = false;
        Set<String> activeTargets = new TreeSet<>();
        for (String propertyName : getKeys(eclipseProjectBazelPrefs)) {
            if (propertyName.startsWith(TARGET_PROPERTY_PREFIX)) {
                String target = eclipseProjectBazelPrefs.get(propertyName, "");
                if (!target.isEmpty()) {
                    if (!target.startsWith(projectLabel)) {
                        // the user jammed in a label not associated with this project, ignore
                        //continue;
                    }
                    if (target.endsWith(":*")) {
                        // we have a wildcard target, so discard any existing targets we gathered (if the user messed up their prefs)
                        // and just go with that.
                        activatedTargets.activateWildcardTarget();
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
    public static List<String> getBazelBuildFlagsForEclipseProject(IProject eclipseProject) {
        Preferences eclipseProjectBazelPrefs = BazelPluginActivator.getResourceHelper().getProjectBazelPreferences(eclipseProject);
        
        ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
        for (String property : getKeys(eclipseProjectBazelPrefs)) {
            if (property.startsWith(BUILDFLAG_PROPERTY_PREFIX)) {
                listBuilder.add(eclipseProjectBazelPrefs.get(property, ""));
            }
        }
        return listBuilder.build();
    }
    
    
    public static void addSettingsToEclipseProject(IProject eclipseProject, String bazelWorkspaceRoot,
            String bazelProjectLabel, List<String> bazelTargets, List<String> bazelBuildFlags) throws BackingStoreException {

        Preferences eclipseProjectBazelPrefs = BazelPluginActivator.getResourceHelper().getProjectBazelPreferences(eclipseProject);

        eclipseProjectBazelPrefs.put(BAZEL_WORKSPACE_ROOT_ABSPATH_PROPERTY, bazelWorkspaceRoot);
        if (!bazelProjectLabel.startsWith("//")) {
            bazelProjectLabel = "//"+bazelProjectLabel;
        }
        eclipseProjectBazelPrefs.put(PROJECT_PACKAGE_LABEL, bazelProjectLabel);
        
        int i = 0;
        for (String bazelTarget : bazelTargets) {
            eclipseProjectBazelPrefs.put(TARGET_PROPERTY_PREFIX + i, bazelTarget);
            i++;
        }
        i = 0;
        for (String bazelBuildFlag : bazelBuildFlags) {
            eclipseProjectBazelPrefs.put(BUILDFLAG_PROPERTY_PREFIX + i, bazelBuildFlag);
            i++;
        }
        eclipseProjectBazelPrefs.flush();
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