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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.osgi.service.prefs.BackingStoreException;

import com.salesforce.bazel.eclipse.component.JavaCoreHelperComponentFacade;
import com.salesforce.bazel.eclipse.component.ResourceHelperComponentFacade;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.sdk.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.sdk.logging.LogHelper;

public class BazelClasspathContainerInitializer extends ClasspathContainerInitializer {
    private static final LogHelper LOG = LogHelper.log(BazelClasspathContainerInitializer.class);

    // error state
    private static AtomicBoolean isCorrupt = new AtomicBoolean(false);

    @Override
    public void initialize(IPath eclipseProjectPath, IJavaProject eclipseJavaProject) throws CoreException {
        IProject eclipseProject = eclipseJavaProject.getProject();
        try {
            //remove projects added to the workspace after a corrupted package in identified
            if (isCorrupt.get()) {
                undo(eclipseJavaProject.getProject());
                return;
            }

            BazelClasspathContainer container = new BazelClasspathContainer(eclipseProject);
            setClasspathContainerForProject(eclipseProjectPath, eclipseJavaProject, container);

        } catch (IOException | InterruptedException | BackingStoreException e) {
            LOG.error("Error while creating Bazel classpath container.", e);
        } catch (BazelCommandLineToolConfigurationException e) {
            LOG.error("Bazel not found", e);
        }
    }

    private void undo(IProject project) {
        if (ResourceHelperComponentFacade.getInstance().getComponent().getEclipseWorkspace().isTreeLocked()) {
            return;
        }

        try {
            project.delete(true, null);
        } catch (CoreException e) {
            LOG.error("Undo operation failed", e);
        }
    }

    public static AtomicBoolean getIsCorrupt() {
        return isCorrupt;
    }

    private static void setClasspathContainerForProject(IPath projectPath, IJavaProject project,
            IClasspathContainer container) throws JavaModelException {
        setClasspathContainerForProject(projectPath, project, container, null);
    }

    private static void setClasspathContainerForProject(IPath projectPath, IJavaProject project,
            IClasspathContainer container, IProgressMonitor monitor) throws JavaModelException {
        JavaCoreHelper ch = JavaCoreHelperComponentFacade.getInstance().getComponent();
        ch.setClasspathContainer(projectPath, new IJavaProject[] { project }, new IClasspathContainer[] { container },
            monitor);
    }

}
