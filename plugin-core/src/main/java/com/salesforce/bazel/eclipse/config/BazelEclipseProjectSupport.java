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

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

import com.google.common.collect.ImmutableList;
import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.builder.BazelMarkerManagerSingleton;
import com.salesforce.bazel.eclipse.model.BazelLabel;
import com.salesforce.bazel.eclipse.model.BazelMarkerDetails;

/**
 * Support class that provides interaction methods for existing Eclipse Bazel projects.
 */
public class BazelEclipseProjectSupport {
    static final String WORKSPACE_ROOT_PROPERTY = "bazel.workspace.root";
    static final String TARGET_PROPERTY_PREFIX = "bazel.target";
    static final String BUILDFLAG_PROPERTY_PREFIX = "bazel.build.flag";

    private static final BazelMarkerManagerSingleton MARKER_MANAGER = BazelMarkerManagerSingleton.getInstance();

    /**
     * List the Bazel targets configured for this Eclipse project. Each project configured for Bazel is configured to
     * track certain targets and this function fetches this list from the project preferences.
     */
    public static List<String> getBazelTargetsForEclipseProject(IProject eclipseProject, boolean addWildcardIfNoTargets) {
        // Get the list of targets from the preferences
        Preferences eclipseProjectBazelPrefs = BazelPluginActivator.getResourceHelper().getProjectBazelPreferences(eclipseProject);
        ImmutableList.Builder<String> listBuilder = ImmutableList.builder();

        boolean addedTarget = false;
        for (String propertyName : getKeys(eclipseProjectBazelPrefs)) {
            if (propertyName.startsWith(TARGET_PROPERTY_PREFIX)) {
                String target = eclipseProjectBazelPrefs.get(propertyName, "");
                if (!"//...".equals(target) && !target.isEmpty()) {
                    listBuilder.add(target);
                    addedTarget = true;
                }
            }
        }

        if (!addedTarget && addWildcardIfNoTargets) {
            listBuilder.add("//...");
        }

        return listBuilder.build();
    }
    
    /**
     * List of Bazel build flags for this Eclipse project, taken from the project configuration
     */
    public static List<String> getBazelBuildFlagsForEclipseProject(IProject eclipseProject) {
        // Get the list of build flags from the preferences
        IScopeContext eclipseProjectScope = BazelPluginActivator.getResourceHelper().getProjectScopeContext(eclipseProject);
        Preferences eclipseProjectNode = eclipseProjectScope.getNode(BazelPluginActivator.PLUGIN_ID);
        
        ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
        for (String property : getKeys(eclipseProjectNode)) {
            if (property.startsWith(BUILDFLAG_PROPERTY_PREFIX)) {
                listBuilder.add(eclipseProjectNode.get(property, ""));
            }
        }
        return listBuilder.build();
    }
    
    /**
     * Publishes problem markers for the specified project.
     */
    public static void publishProblemMarkers(IProject project, IProgressMonitor monitor, Collection<BazelMarkerDetails> errors, Collection<BazelLabel> labels) {
        runWithProgress(monitor, new WorkspaceModifyOperation() {
            @Override
            protected void execute(IProgressMonitor monitor) throws CoreException {
                MARKER_MANAGER.clearProblemMarkersForProject(project);
                MARKER_MANAGER.publishProblemMarkersForProject(project, errors, labels);
            }
        });
    }

    /**
     * Boilerplate to run a IRunnableWithProgress and deal with checked exceptions.
     */
    public static void runWithProgress(IProgressMonitor monitor, IRunnableWithProgress runnable) {
        try {
            runnable.run(monitor);
        } catch (InvocationTargetException | InterruptedException ex) {
            throw new IllegalStateException(ex);
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
