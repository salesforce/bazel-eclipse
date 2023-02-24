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
import org.eclipse.ui.PlatformUI;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.core.BazelCorePlugin;
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

    static final Object[] EMPTY = {};

    private static List<BazelPackageInfo> getImportedBazelPackages(BazelPackageInfo rootPackage) {
        List<BazelPackageInfo> importedPackages = new ArrayList<>();
        var javaProjects = ComponentContext.getInstance().getJavaCoreHelper().getAllBazelJavaProjects(false);
        var bazelProjectManager = ComponentContext.getInstance().getProjectManager();

        for (IJavaProject javaProject : javaProjects) {
            // TODO it is possible there are no targets configured for a project
            var projectName = javaProject.getProject().getName();
            var bazelProject = bazelProjectManager.getProject(projectName);
            var target = bazelProjectManager.getConfiguredBazelTargets(bazelProject, false).getConfiguredTargets()
                    .iterator().next();
            var label = new BazelLabel(target);
            var pack = label.getPackagePath(true);
            var bpi = rootPackage.findByPackage(pack);
            if (bpi != null) {
                importedPackages.add(bpi);
            }
        }
        return importedPackages;
    }

    BazelImportWizardLocationControl locationControl;
    BazelImportWizardLabelProvider labelProvider;
    BazelImportWizardProjectTree projectTree;
    final BazelImportWizardWorkingSetControl workingSetControl;

    BazelImportWizardAdvancedSettingsControl advancedSettingsControl;

    BazelPackageInfo workspaceRootPackage = null;

    // errors should be set into this field, which will be shown to the user
    String loadingErrorMessage;

    public BazelImportWizardPage() {
        super("BazelImportWizardPage");

        setTitle("Import a Bazel Workspace");
        setDescription("Imports a Bazel Workspace into Eclipse");
        setPageComplete(false);

        workingSetControl = new BazelImportWizardWorkingSetControl(this);
    }

    /**
     * Called once each time the user picks the Bazel import wizard.
     */
    @Override
    public void createControl(Composite parent) {
        LOG.info("BazelImportWizardPage.createControl");
        var composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new GridLayout(3, false));
        setControl(composite);

        var prefs = PlatformUI.createPreferenceStore(BazelCorePlugin.class);
        locationControl = new BazelImportWizardLocationControl(this, prefs);
        var hasWorkspaceLocation = locationControl.addLocationControl(composite);

        labelProvider = new BazelImportWizardLabelProvider(this);

        projectTree = new BazelImportWizardProjectTree(this, labelProvider);
        projectTree.addProjectTree(composite);

        workingSetControl.addWorkingSetControl(composite);

        advancedSettingsControl = new BazelImportWizardAdvancedSettingsControl(this);
        advancedSettingsControl.addAdvancedSettingsControl(composite);

        if (hasWorkspaceLocation) {
            // this dialog had been opened before, and a Bazel workspace path from history was
            // loaded into the view; populate the dialog and mark the ones not already open for import
            scanProjects();
        }
    }

    @SuppressWarnings("deprecation")
    public void scanProjects() {
        // this the heavy lifting of scanning the file system for BUILD files, checking if BUILD file is a Java package
        var workspaceScanner = new BazelWorkspaceScanner();
        try {
            List<String> newFilesystemLocations = new ArrayList<>();
            List<BazelPackageInfo> newEclipseProjects = new ArrayList<>();

            // get the selected location
            // when the wizard is first opened, the location field is blank and we have a null root package
            var currentRootDirectory = locationControl.getRootDirectory();
            if (currentRootDirectory != null) {
                projectTree.setRootWorkspaceDirectory(currentRootDirectory);
                workspaceRootPackage = workspaceScanner.getPackages(currentRootDirectory);
                if (workspaceRootPackage != null) {
                    // make sure the user chose a Bazel workspace
                    newEclipseProjects.add(workspaceRootPackage);
                    newFilesystemLocations.add(workspaceRootPackage.getBazelPackageFSAbsolutePath());
                    projectTree.projectTreeViewer.setInput(newEclipseProjects);
                    projectTree.projectTreeViewer.expandAll();
                    projectTree.importProjectViewButton.setEnabled(true);
                    if (workspaceRootPackage.getChildPackageInfos().size() < 10) {
                        // short term usability hack, enable all for import if there are less than 10 Bazel packages
                        projectTree.projectTreeViewer.setAllChecked(true);
                    }
                    uncheckAlreadyImportedProjects(projectTree.projectTreeViewer, workspaceRootPackage);
                } else {
                    projectTree.projectTreeViewer.setAllChecked(true);
                }
            }

            BazelImportWizardLocationControl.rootDirHistoryList = newFilesystemLocations;

            setPageComplete();
            setErrorMessage(null);
            setMessage(null);

            loadingErrorMessage = null;
            workingSetControl.updateWorkingSet(workspaceRootPackage);
        } catch (Exception anyE) {
            LOG.error(anyE.getMessage(), anyE);
            setErrorMessage("Error importing the Bazel workspace. Details: " + anyE.getMessage());
        }
    }

    /**
     * Determines if the user has done enough for the Finish button to be enabled.
     */
    void setPageComplete() {
        // determine if there are any checked Bazel packages in the project tree. If there are, that means
        // that the user correctly pointed the location control to a Bazel workspace, and it found one or
        // more Java packages.
        var checkedElements = projectTree.projectTreeViewer.getCheckedElements();
        var isComplete = (checkedElements != null) && (checkedElements.length > 0);
        setPageComplete(isComplete);
        LOG.info("BazelImportWizardPage.setPageComplete: {}", isComplete);
    }

    private void uncheckAlreadyImportedProjects(CheckboxTreeViewer projectTreeViewer,
            BazelPackageInfo rootBazelPackage) {
        var importedBazelPackages = getImportedBazelPackages(rootBazelPackage);

        if (importedBazelPackages.size() > 0) {
            projectTreeViewer.setChecked(rootBazelPackage, false);
            projectTreeViewer.setGrayed(rootBazelPackage, true);
            for (BazelPackageInfo alreadyImportedPackage : importedBazelPackages) {
                projectTreeViewer.setChecked(alreadyImportedPackage, false);
                projectTreeViewer.setGrayed(alreadyImportedPackage, true);
            }
        }
    }

}
