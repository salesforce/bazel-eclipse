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
/*******************************************************************************
 * Copyright (c) 2008-2013 Sonatype, Inc. and others. Copyright (c) 2019 Salesforce All rights reserved. This program
 * and the accompanying materials are made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Sonatype, Inc. - initial API and implementation Red Hat, Inc. - refactored lifecycle mapping discovery
 * out Salesforce - repurpose for Bazel
 *******************************************************************************/

// adapted from M2Eclipse org.eclipse.m2e.core.ui.internal.wizards.MavenImportWizard

package com.salesforce.bazel.eclipse.wizard;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkingSet;

import com.salesforce.bazel.eclipse.abstractions.WorkProgressMonitor;
import com.salesforce.bazel.eclipse.config.BazelEclipseProjectFactory;
import com.salesforce.bazel.eclipse.logging.LogHelper;
import com.salesforce.bazel.eclipse.model.BazelPackageInfo;
import com.salesforce.bazel.eclipse.model.BazelPackageLocation;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseWorkProgressMonitor;
import com.salesforce.bazel.eclipse.util.SelectionUtil;

/**
 * Entrypoint for the Bazel Workspace import wizard
 *
 * @author plaird
 */
public class BazelImportWizard extends Wizard implements IImportWizard {
    static final LogHelper LOG = LogHelper.log(BazelImportWizard.class);

    protected IStructuredSelection selection;
    protected List<IWorkingSet> workingSets = new ArrayList<IWorkingSet>();

    private boolean initialized = false;

    private BazelImportWizardPage page;

    public BazelImportWizard() {
        setNeedsProgressMonitor(true);
        setWindowTitle("Import Bazel Workspace");
    }

    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        LOG.info("BazelImportWizard.init");
        this.selection = selection;
        IWorkingSet workingSet = SelectionUtil.getSelectedWorkingSet(selection);
        if (workingSet != null) {
            this.workingSets.add(workingSet);
        }
        initialized = true;
    }

    @Override
    public void addPages() {
        if (!initialized) {
            init(null, null);
        }
        LOG.info("BazelImportWizard.addPages");

        page = new BazelImportWizardPage();
        if (selection != null && selection.size() == 1) {
            // can't use SelectionUtil.getSelectedWorkingSet because it also looks at
            // selected IResource
            IWorkingSet workingSet = SelectionUtil.getType(selection.getFirstElement(), IWorkingSet.class);

            // https://bugs.eclipse.org/bugs/show_bug.cgi?id=427205
            // ideally, this should be contributed by m2e jdt.ui, but this looks like
            // overkill
            String JDT_OTHER_PROJECTS = "org.eclipse.jdt.internal.ui.OthersWorkingSet";
            if (workingSet != null && !JDT_OTHER_PROJECTS.equals(workingSet.getId())) {
                page.workingSetControl.setWorkingSetName(workingSet.getName());
            }
        }
        addPage(page);
    }

    @Override
    public boolean performFinish() {
        // the first node in the project tree is the root node - for now we'll always import this root node regardless of what the user actually selected
        BazelPackageInfo workspaceRootProject = page.workspaceRootPackage;

        Object[] selectedBazelPackages = page.projectTree.projectTreeViewer.getCheckedElements();
        List<BazelPackageLocation> bazelPackagesToImport =
                Arrays.asList(selectedBazelPackages).stream().filter(bpi -> bpi != workspaceRootProject)
                        .map(bpi -> (BazelPackageInfo) bpi).collect(Collectors.toList());

        // TODO implement progress monitor for the import
        // this.getContainer().run()

        WorkProgressMonitor progressMonitor = new EclipseWorkProgressMonitor(null);
        IRunnableWithProgress op = new IRunnableWithProgress() {
            @Override
            public void run(IProgressMonitor monitor) {
                try {
                    BazelEclipseProjectFactory.importWorkspace(workspaceRootProject, bazelPackagesToImport, progressMonitor,
                        monitor);
                } catch (Exception e) {
                    e.printStackTrace();
                    MessageDialog.openError(new Shell(), "Error", e.getMessage());
                }
            }
        };

        try {
            new ProgressMonitorDialog(new Shell()).run(true, true, op);
        } catch (InvocationTargetException e) {
            MessageDialog.openError(new Shell(), "Error", e.getTargetException().getMessage());
        } catch (Exception e) {
            MessageDialog.openError(new Shell(), "Error", e.getMessage());
        }

        return true;
    }

    @Override
    public boolean needsProgressMonitor() {
        return true;
    }

}
