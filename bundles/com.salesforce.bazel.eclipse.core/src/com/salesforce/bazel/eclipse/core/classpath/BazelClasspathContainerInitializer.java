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
package com.salesforce.bazel.eclipse.core.classpath;

import static java.lang.String.format;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import com.salesforce.bazel.eclipse.core.BazelCorePlugin;

public class BazelClasspathContainerInitializer extends ClasspathContainerInitializer {

    @Override
    public boolean canUpdateClasspathContainer(IPath containerPath, IJavaProject project) {
        return true;
    }

    BazelClasspathManager getClasspathManager() {
        return BazelCorePlugin.getInstance().getBazelModelManager().getClasspathManager();
    }

    @Override
    public void initialize(IPath containerPath, IJavaProject project) throws CoreException {
        if (!BazelClasspathHelpers.isBazelClasspathContainer(containerPath)) {
            return;
        }

        var bazelContainer = getClasspathManager().getSavedContainer(project.getProject());
        if (bazelContainer != null) {
            JavaCore.setClasspathContainer(containerPath, new IJavaProject[] { project },
                new IClasspathContainer[] { bazelContainer }, new NullProgressMonitor());
        }
    }

    @Override
    public void requestClasspathContainerUpdate(IPath containerPath, IJavaProject project,
            IClasspathContainer containerSuggestion) throws CoreException {
        new Job("Saving Bazel Classpath Container") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    getClasspathManager().persistAttachedSourcesAndJavadoc(project, containerSuggestion, monitor);
                } catch (CoreException ex) {
                    return Status
                            .error(format("An error occured saving the Bazel classpath container for project '%s'.",
                                project.getElementName()), ex);
                }
                return Status.OK_STATUS;
            }
        }.schedule();
    }
}
