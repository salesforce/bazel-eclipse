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
package com.salesforce.bazel.eclipse.wizard;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.salesforce.bazel.eclipse.projectimport.ProjectImporter;
import com.salesforce.bazel.eclipse.projectimport.ProjectImporterFactory;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;

/**
 * Imports projects with a Progress Dialog. This is used by the Import Wizard and the ProjectView machinery.
 */
public class BazelProjectImporter {
    private static final LogHelper LOG = LogHelper.log(MethodHandles.lookup().lookupClass());

    public static void run(BazelPackageLocation workspaceRootProject,
            List<BazelPackageLocation> bazelPackagesToImport) {
        IRunnableWithProgress op = new IRunnableWithProgress() {
            @Override
            public void run(IProgressMonitor monitor) {
                ProjectImporterFactory importerFactory =
                        new ProjectImporterFactory(workspaceRootProject, bazelPackagesToImport);
                ProjectImporter projectImporter = importerFactory.build();
                try {
                    projectImporter.run(monitor);
                } catch (Exception e) {
                    LOG.error("catch error during import", e);
                    openError("Error", e);
                }
            }
        };

        try {
            new ProgressMonitorDialog(new Shell()).run(true, true, op);
        } catch (InvocationTargetException e) {
            openError("Error", e.getTargetException());
        } catch (Exception e) {
            openError("Error", e);
        }
    }

    private static void openError(String title, Throwable ex) {
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                String exceptionMessage = ex.getMessage();
                if ((exceptionMessage == null) || exceptionMessage.isEmpty()) {
                    // Exception does not have a message, which usually means it is an NPE.
                    exceptionMessage = "An exception of type [" + ex.getClass().getName()
                            + "] was thrown, but no additional message details are available. "
                            + "Check the console window where you launched Eclipse, or Eclipse log for the full stack trace.";
                }
                MessageDialog.openError(new Shell(), title, exceptionMessage);
            }
        });
    }
}
