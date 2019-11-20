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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.osgi.service.prefs.BackingStoreException;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.command.BazelCommandLineToolConfigurationException;
import com.salesforce.bazel.eclipse.logging.LogHelper;

public class BazelClasspathContainerInitializer extends ClasspathContainerInitializer {
    static final LogHelper LOG = LogHelper.log(BazelClasspathContainerInitializer.class);

    private static List<IProject> importedProjects = Collections.synchronizedList(new ArrayList<IProject>());
    
    // error state
    public static AtomicBoolean isCorrupt = new AtomicBoolean(false);
    private static String corruptPackage = null;
    
    @Override
    public void initialize(IPath eclipseProjectPath, IJavaProject eclipseJavaProject) throws CoreException {
        IProject eclipseProject = eclipseJavaProject.getProject();
        try {      
            //remove projects added to the workspace after a corrupted package in identified
            if (isCorrupt.get()) {
                undo(eclipseJavaProject.getProject());
                return;
            }
            
            BazelClasspathContainer container = new BazelClasspathContainer(eclipseProject, eclipseJavaProject);
            if (container.isValid()) {
                BazelPluginActivator.getJavaCoreHelper().setClasspathContainer(eclipseProjectPath, new IJavaProject[] { eclipseJavaProject },
                    new IClasspathContainer[] { container }, null);
                importedProjects.add(eclipseJavaProject.getProject());
            } else {
                // this is not exactly the package path, it is just the leaf node name
                corruptPackage = eclipseJavaProject.getPath().toString();
                String errorMsg = generateImportErrorMessage();
                BazelPluginActivator.error(errorMsg);
                LOG.error(errorMsg);

                if (!isCorrupt.get()) {
                    importedProjects.add(eclipseProject);
                }
                undo();
            }
            
        } catch (IOException | InterruptedException | BackingStoreException e) {
            BazelPluginActivator.error("Error while creating Bazel classpath container.", e);
        } catch (BazelCommandLineToolConfigurationException e) {
            BazelPluginActivator.error("Bazel not found: " + e.getMessage());
        }
    }
    
    // Remove projects imported successfully 
    private void undo() throws CoreException {
        synchronized (importedProjects) {
            if (BazelPluginActivator.getResourceHelper().getEclipseWorkspace().isTreeLocked()) {
                // cannot delete projects, as the workspace is locked
                return;
            }
            for(IProject project: importedProjects) {
                project.delete(true, null);
            }            
        }
        isCorrupt.set(true);
        
        MessageDialog.openError(Display.getDefault().getActiveShell(), "Error", generateImportErrorMessage());
    }
    
    private String generateImportErrorMessage() {
        String errorMsg = "Failed during Bazel dependency computation and import has been cancelled."+
                "\nEnsure that the Bazel workspace builds correctly on the command line before importing into Eclipse.";
        if (corruptPackage != null) {
            errorMsg = "Failed to compute dependencies for package "+corruptPackage+" and import has been cancelled."+
                    "\nEnsure that the Bazel workspace builds correctly on the command line before importing into Eclipse.";
        }
        return errorMsg;
    }
    
    private void undo(IProject project) {
        try {
            project.delete(true,  null);
        } catch (CoreException e) {
            e.printStackTrace();
        }
    }

}
