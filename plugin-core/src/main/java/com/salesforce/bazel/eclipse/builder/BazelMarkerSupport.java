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
package com.salesforce.bazel.eclipse.builder;

import java.util.Collection;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.ui.actions.WorkspaceModifyOperation;

import com.salesforce.bazel.eclipse.config.BazelEclipseProjectSupport;
import com.salesforce.bazel.eclipse.model.BazelBuildError;

/**
 * Encapsulates Bazel Error Marker logic.
 *
 * @author nishant.dsouza
 *
 */
public class BazelMarkerSupport {

    private static final String BAZEL_MARKER = "com.salesforce.bazel.eclipse.bazelmarker";

    /**
     * Publishes the specified build errors to the Problems View.
     *
     * This method publishes the specified BazelBuildError instances as problem markers to
     * the Problems View. If you want to clear Problems View before publishing, call 
     * clearProblemMarkersForProject before this method.
     *
     * Note that this method runs within a WorkspaceModifyOperation.
     *
     * @param project  the project for which to publish the specified errors
     * @param errors  the errors to publish to the Problems View
     * @param monitor  progress monitor
     */
    public static void publishToProblemsView(IProject project, Collection<BazelBuildError> errors, IProgressMonitor monitor) {
        BazelEclipseProjectSupport.runWithProgress(monitor, new WorkspaceModifyOperation() {
            @Override
            protected void execute(IProgressMonitor monitor) throws CoreException {
                publishProblemMarkersForProject(project, errors);
            }
        });
    }

    /**
     * Clears the Problems View for the specified project.
     * 
     * Note that this method runs within a WorkspaceModifyOperation.
     *
     * @param project  the project for which to remove associated markers from the Problems View
     */
    public static void clearProblemMarkersForProject(IProject project, IProgressMonitor monitor) {
        BazelEclipseProjectSupport.runWithProgress(monitor, new WorkspaceModifyOperation() {
            @Override
            protected void execute(IProgressMonitor monitor) throws CoreException {
                project.deleteMarkers(BazelMarkerSupport.BAZEL_MARKER, true, IResource.DEPTH_INFINITE);
            }
        });
    }
    
    private static void publishProblemMarkersForProject(IProject project, Collection<BazelBuildError> errorDetails) throws CoreException {        
        for (BazelBuildError errorDetail : errorDetails) {
            String resourcePath = errorDetail.getResourcePath();
            IResource resource = project.findMember(resourcePath);
            IMarker marker = resource.createMarker(BAZEL_MARKER);
            marker.setAttribute(IMarker.LINE_NUMBER, errorDetail.getLineNumber());
            marker.setAttribute(IMarker.LOCATION, "line " + errorDetail.getLineNumber());
            marker.setAttribute(IMarker.MESSAGE, errorDetail.getDescription());
            marker.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
            marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
        }
    }
}
