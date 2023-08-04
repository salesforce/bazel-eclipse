/*******************************************************************************
 * Copyright (c) 2008-2018 Sonatype, Inc. and others. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors: Sonatype, Inc. - initial API and implementation
 *******************************************************************************/

// adapted from M2Eclipse org.eclipse.m2e.core.ui.internal.wizards.MavenImportWizard

package com.salesforce.bazel.eclipse.ui.wizards;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.statushandlers.StatusAdapter;
import org.eclipse.ui.statushandlers.StatusManager;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.setup.ImportBazelWorkspaceJob;

/**
 * Entrypoint for the Bazel Workspace import wizard
 */
public class BazelImportWizard extends Wizard implements IImportWizard {

    protected IStructuredSelection selection;
    protected List<IWorkingSet> workingSets = new ArrayList<>();

    private BazelImportWizardMainPage mainPage;
    private BazelImportWizardProjectViewPage projectViewPage;

    @Override
    public void addPages() {
        mainPage = new BazelImportWizardMainPage();
        projectViewPage = new BazelImportWizardProjectViewPage(mainPage::getBazelProjectView);

        addPage(mainPage);
        addPage(projectViewPage);
    }

    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        setNeedsProgressMonitor(true);
        setWindowTitle("Import Bazel Workspace");
    }

    @Override
    public boolean needsProgressMonitor() {
        return true;
    }

    @Override
    public boolean performFinish() {
        var workspaceRoot = projectViewPage.getBazelWorkspaceRoot();
        var projectView = projectViewPage.getBazelProjectView();

        var bazelWorkspace = BazelCore.getModel().getBazelWorkspace(workspaceRoot);

        var importBazelWorkspaceJob = new ImportBazelWorkspaceJob(bazelWorkspace, projectView);

        // although this is a job, we don't run it in the background but in the wizard

        try {
            getContainer().run(true, true, monitor -> {
                try {
                    ResourcesPlugin.getWorkspace().run(progress -> {
                        var status = importBazelWorkspaceJob.runInWorkspace(progress);
                        if (status == Status.CANCEL_STATUS) {
                            throw new OperationCanceledException();
                        }
                        if (!status.isOK()) {
                            throw new CoreException(status);
                        }
                    }, monitor);
                } catch (CoreException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (InterruptedException e) {
            MessageDialog.openInformation(
                getShell(),
                "Bazel Import Canceled",
                "The Bazel workspace import was canceled. Your workspace might be in an incomplete state. Please perform any necessary cleanups yourself.");
            return true; // close wizard
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            if ((cause instanceof RuntimeException) && (cause.getCause() != null)) {
                cause = cause.getCause();
            }

            var status = new StatusAdapter(Status.error("Bazel Workspace Import Failed", cause));
            StatusManager.getManager().handle(status, StatusManager.BLOCK | StatusManager.LOG);
            return false; // keep wizard open
        }

        return true;
    }

}
