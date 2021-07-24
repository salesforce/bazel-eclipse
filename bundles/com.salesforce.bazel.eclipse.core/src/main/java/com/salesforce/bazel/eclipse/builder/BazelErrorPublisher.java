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
package com.salesforce.bazel.eclipse.builder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;

import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelProblem;
import com.salesforce.bazel.sdk.project.BazelProject;

/**
 * Publishes BazelError instances to the Problems View.
 */
class BazelErrorPublisher {

    static final String UNKNOWN_PROJECT_ERROR_MSG_PREFIX = "ERROR IN UNKNOWN PROJECT: ";

    private static final LogHelper LOG = LogHelper.log(BazelErrorPublisher.class);

    private final IProject rootProject;
    private final Collection<IProject> projects;
    private final Map<BazelLabel, BazelProject> labelToProject;
    private final BazelProblemMarkerManager markerManager;

    public BazelErrorPublisher(IProject rootProject, Collection<IProject> projects,
            Map<BazelLabel, BazelProject> labelToProject) {
        this.rootProject = rootProject;
        this.projects = projects;
        this.labelToProject = labelToProject;
        markerManager = new BazelProblemMarkerManager(getClass().getName());
    }

    public void publish(List<BazelProblem> errors, IProgressMonitor monitor) {
        clearProblemsView(monitor);
        publishToProblemsView(errors, monitor);
    }

    // maps the specified errors to the project instances they belong to, and returns that mapping
    static Map<IProject, List<BazelProblem>> assignErrorsToOwningProject(List<BazelProblem> errors,
            Map<BazelLabel, BazelProject> labelToProject, IProject rootProject) {
        Map<IProject, List<BazelProblem>> projectToErrors = new HashMap<>();
        List<BazelProblem> remainingErrors = new LinkedList<>(errors);
        for (BazelProblem error : errors) {
            BazelLabel owningLabel = error.getOwningLabel(labelToProject.keySet());
            if (owningLabel != null) {
                BazelProject project = labelToProject.get(owningLabel);
                IProject eclipseProject = (IProject) project.getProjectImpl();
                mapProblemToProject(error.toErrorWithRelativizedResourcePath(owningLabel), eclipseProject,
                    projectToErrors);
                remainingErrors.remove(error);
            }
        }
        if (!remainingErrors.isEmpty()) {
            if (rootProject != null) {
                for (BazelProblem error : remainingErrors) {
                    mapProblemToProject(error.toGenericWorkspaceLevelError(UNKNOWN_PROJECT_ERROR_MSG_PREFIX),
                        rootProject, projectToErrors);
                }
            } else {
                // getting here is a bug - at least log the errors we didn't assign to any project
                for (BazelProblem error : remainingErrors) {
                    LOG.error("Unhandled error: " + error);
                }
            }
        }
        return projectToErrors;
    }

    private void clearProblemsView(IProgressMonitor monitor) {
        List<IProject> allProjects = new ArrayList<>(projects.size() + 1);
        allProjects.addAll(projects);
        if (rootProject != null) {
            allProjects.add(rootProject);
        }
        markerManager.clear(allProjects, monitor);
    }

    private void publishToProblemsView(List<BazelProblem> errors, IProgressMonitor monitor) {
        Map<IProject, List<BazelProblem>> projectToErrors =
                assignErrorsToOwningProject(errors, labelToProject, rootProject);
        for (IProject project : projectToErrors.keySet()) {
            markerManager.publish(projectToErrors.get(project), project, monitor);
        }
    }

    private static void mapProblemToProject(BazelProblem problem, IProject project,
            Map<IProject, List<BazelProblem>> projectToErrors) {
        List<BazelProblem> storedErrors = projectToErrors.get(project);
        if (storedErrors == null) {
            storedErrors = new ArrayList<>();
        }
        storedErrors.add(problem);
        projectToErrors.put(project, storedErrors);
    }
}
