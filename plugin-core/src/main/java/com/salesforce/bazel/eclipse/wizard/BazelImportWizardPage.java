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
 * Copyright (c) 2008-2013 Sonatype, Inc. and others. Copyright 2018-2019 Salesforce
 * 
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Sonatype, Inc. - initial API and implementation Red Hat, Inc. - refactored lifecycle mapping discovery
 *******************************************************************************/

// adapted from M2Eclipse org.eclipse.m2e.core.ui.internal.wizards.MavenImportWizardPage

package com.salesforce.bazel.eclipse.wizard;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.config.BazelEclipseProjectSupport;
import com.salesforce.bazel.eclipse.importer.BazelProjectImportScanner;
import com.salesforce.bazel.eclipse.logging.LogHelper;
import com.salesforce.bazel.eclipse.model.BazelLabel;
import com.salesforce.bazel.eclipse.model.BazelPackageInfo;

/**
 * Class that sets up the UI for the Bazel Import Workspace wizard.
 */
public class BazelImportWizardPage extends WizardPage {
    static final LogHelper LOG = LogHelper.log(BazelImportWizardPage.class);

    static final Object[] EMPTY = new Object[0];

    BazelImportWizardLocationControl locationControl;
    BazelImportWizardLabelProvider labelProvider;
    BazelImportWizardProjectTree projectTree;
    BazelImportWizardWorkingSetControl workingSetControl;
    BazelImportWizardAdvancedSettingsControl advancedSettingsControl;

    BazelPackageInfo workspaceRootPackage = null;

    // errors should be set into this field, which will be shown to the user
    String loadingErrorMessage;

    public BazelImportWizardPage() {
        super("BazelImportWizardPage");

        setTitle("Import a Bazel Workspace");
        setDescription("Imports a Bazel Workspace into Eclipse");
        setPageComplete(false);
    }

    @Override
    public void createControl(Composite parent) {
        LOG.info("BazelImportWizardPage.createControl");
        Composite composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(3, false));
        setControl(composite);

        locationControl = new BazelImportWizardLocationControl(this);
        locationControl.addLocationControl(composite);

        labelProvider = new BazelImportWizardLabelProvider(this);

        projectTree = new BazelImportWizardProjectTree(this, labelProvider);
        projectTree.addProjectTree(composite);

        workingSetControl = new BazelImportWizardWorkingSetControl(this);
        workingSetControl.addWorkingSetControl(composite);

        advancedSettingsControl = new BazelImportWizardAdvancedSettingsControl(this);
        advancedSettingsControl.addAdvancedSettingsControl(composite);
    }

    /**
     * Determines if the user has done enough for the Finish button to be enabled.
     */
    void setPageComplete() {
        // determine if there are any checked Bazel packages in the project tree. If there are, that means
        // that the user correctly pointed the location control to a Bazel workspace, and it found one or
        // more Java packages.
        Object[] checkedElements = projectTree.projectTreeViewer.getCheckedElements();
        boolean isComplete = checkedElements != null && checkedElements.length > 0;
        setPageComplete(isComplete);
        LOG.info("BazelImportWizardPage.setPageComplete: {}", isComplete);
    }

    @SuppressWarnings("deprecation")
    public void scanProjects() {
        // this the heavy lifting of scanning the file system for BUILD files, checking if BUILD file is a Java package
        BazelProjectImportScanner projectScanner = new BazelProjectImportScanner();
        try {
            List<String> newFilesystemLocations = new ArrayList<>();
            List<BazelPackageInfo> newEclipseProjects = new ArrayList<>();
            
            // get the selected location
            // when the wizard is first opened, the location field is blank and we have a null root package
            if (this.locationControl.rootDirectory != null) {
                this.projectTree.setRootWorkspaceDirectory(this.locationControl.rootDirectory);
                this.workspaceRootPackage = projectScanner.getProjects(this.locationControl.rootDirectory);
                if (workspaceRootPackage != null) {
                    // make sure the user chose a Bazel workspace
                    newEclipseProjects.add(workspaceRootPackage);
                    newFilesystemLocations.add(workspaceRootPackage.getBazelPackageFSAbsolutePath());
                    this.projectTree.projectTreeViewer.setInput(newEclipseProjects);
                    this.projectTree.projectTreeViewer.expandAll();
                    this.projectTree.importProjectViewButton.setEnabled(true);
                    if (workspaceRootPackage.getChildPackageInfos().size() < 10) {
                        // short term usability hack, enable all for import if there are less than 10 Bazel packages
                        this.projectTree.projectTreeViewer.setAllChecked(true);
                    }
                    uncheckAlreadyImportedProjects(this.projectTree.projectTreeViewer, this.workspaceRootPackage);
                } else {
                    this.projectTree.projectTreeViewer.setAllChecked(true);
                }
            }

            this.locationControl.locations = newFilesystemLocations;

            setPageComplete();
            setErrorMessage(null);
            setMessage(null);

            this.loadingErrorMessage = null;
            this.workingSetControl.updateWorkingSet(this.workspaceRootPackage);
        } catch (Exception anyE) {
            LOG.error(anyE.getMessage(), anyE);
            setErrorMessage("Error importing the Bazel workspace. Details: " + anyE.getMessage());
        }
    }

    
    private void uncheckAlreadyImportedProjects(CheckboxTreeViewer projectTreeViewer, BazelPackageInfo rootBazelPackage) {
        List<BazelPackageInfo> importedBazelPackages = getImportedBazelPackages(rootBazelPackage);

        if (importedBazelPackages.size() > 0) {
            projectTreeViewer.setChecked(rootBazelPackage, false);
            projectTreeViewer.setGrayed(rootBazelPackage, true);
            for (BazelPackageInfo alreadyImportedPackage : importedBazelPackages) {
                projectTreeViewer.setChecked(alreadyImportedPackage, false);
                projectTreeViewer.setGrayed(alreadyImportedPackage, true);
            }
        }
    }
    
    private static List<BazelPackageInfo> getImportedBazelPackages(BazelPackageInfo rootPackage) {
        List<BazelPackageInfo> importedPackages = new ArrayList<>();
        IJavaProject[] javaProjects = BazelPluginActivator.getJavaCoreHelper().getAllBazelJavaProjects(false);
        for (IJavaProject javaProject : javaProjects) {
            String target = BazelEclipseProjectSupport.getBazelTargetsForEclipseProject(javaProject.getProject(), false).get(0);
            BazelLabel label = new BazelLabel(target);
            String pack = label.getDefaultPackageLabel().getLabel();
            BazelPackageInfo bpi = rootPackage.findByPackage(pack);
            if (bpi != null) {
                importedPackages.add(bpi);
            }
        }
        return importedPackages;
    }
    
}
