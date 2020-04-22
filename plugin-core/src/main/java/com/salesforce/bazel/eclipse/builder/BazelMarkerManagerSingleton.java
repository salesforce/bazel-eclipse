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

import com.salesforce.bazel.eclipse.model.BazelBuildError;

/**
 * Encapsulates Bazel Error Marker logic.
 *
 * @author nishant.dsouza
 *
 */
public class BazelMarkerManagerSingleton {

    private static BazelMarkerManagerSingleton singletonInstance = null;

    private BazelMarkerManagerSingleton() {

    }

    public static final String BAZEL_MARKER = "com.salesforce.bazel.eclipse.bazelmarker";

    /**
     * This method modifies the workspace and must always be executed within a WorkspaceModifyOperation
     *
     * @param project
     *            the project to clear problem markers for
     * @throws CoreException
     */
    public void clearProblemMarkersForProject(IProject project) throws CoreException {
        project.deleteMarkers(BazelMarkerManagerSingleton.BAZEL_MARKER, true, IResource.DEPTH_INFINITE);
    }

    /**
     * This method modifies the workspace and must always be executed within a WorkspaceModifyOperation
     *
     * @param the
     *            project the problem markers are related to
     * @param errorDetails
     *            the errors that problem markers should be created for
     * @param labels
     *            the Bazel Labels that were built and resulted in the specified errors
     * @throws CoreException
     */
    public void publishProblemMarkersForProject(IProject project, Collection<BazelBuildError> errorDetails) throws CoreException {
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

    public static BazelMarkerManagerSingleton getInstance() {
        if(singletonInstance == null) {
            singletonInstance = new BazelMarkerManagerSingleton();
        }
        return singletonInstance;
    }
}
