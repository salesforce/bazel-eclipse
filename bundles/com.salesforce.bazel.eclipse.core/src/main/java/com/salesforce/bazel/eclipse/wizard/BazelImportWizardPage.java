/*******************************************************************************
 * Copyright (c) 2008-2018 Sonatype, Inc. and others.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which accompanies this distribution, and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors: Sonatype, Inc. - initial API and implementation Red Hat, Inc. - refactored lifecycle mapping discovery
 * Salesforce - Adapted for Bazel Eclipse
 *******************************************************************************/

// adapted from M2Eclipse org.eclipse.m2e.core.ui.internal.wizards.MavenImportWizardPage

package com.salesforce.bazel.eclipse.wizard;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageInfo;
import com.salesforce.bazel.sdk.project.BazelProject;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.workspace.BazelWorkspaceScanner;

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

        IPreferenceStore prefs = BazelPluginActivator.getInstance().getPreferenceStore();
        locationControl = new BazelImportWizardLocationControl(this, prefs);
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
        BazelWorkspaceScanner workspaceScanner = new BazelWorkspaceScanner();
        try {
            List<String> newFilesystemLocations = new ArrayList<>();
            List<BazelPackageInfo> newEclipseProjects = new ArrayList<>();

            // get the selected location
            // when the wizard is first opened, the location field is blank and we have a null root package
            if (this.locationControl.rootDirectory != null) {
                this.projectTree.setRootWorkspaceDirectory(this.locationControl.rootDirectory);
                this.workspaceRootPackage = workspaceScanner.getPackages(this.locationControl.rootDirectory);
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

    private void uncheckAlreadyImportedProjects(CheckboxTreeViewer projectTreeViewer,
            BazelPackageInfo rootBazelPackage) {
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
        BazelProjectManager bazelProjectManager = BazelPluginActivator.getBazelProjectManager();

        for (IJavaProject javaProject : javaProjects) {
            // TODO it is possible there are no targets configured for a project
            String projectName = javaProject.getProject().getName();
            BazelProject bazelProject = bazelProjectManager.getProject(projectName);
            String target = bazelProjectManager.getConfiguredBazelTargets(bazelProject, false).getConfiguredTargets()
                    .iterator().next();
            BazelLabel label = new BazelLabel(target);
            String pack = label.toDefaultPackageLabel().getLabel();
            BazelPackageInfo bpi = rootPackage.findByPackage(pack);
            if (bpi != null) {
                importedPackages.add(bpi);
            }
        }
        return importedPackages;
    }

}
