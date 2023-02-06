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

package com.salesforce.bazel.eclipse.wizard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkingSet;

import com.salesforce.bazel.eclipse.util.SelectionUtil;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelPackageInfo;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;

/**
 * Entrypoint for the Bazel Workspace import wizard
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
        List<Object> grayedBazelPackages = Arrays.asList(page.projectTree.projectTreeViewer.getGrayedElements());

        List<BazelPackageLocation> bazelPackagesToImport = Arrays.asList(selectedBazelPackages).stream()
                .filter(bpi -> !grayedBazelPackages.contains(bpi))
                .map(bpi -> (BazelPackageInfo) bpi).collect(Collectors.toList());

        BazelProjectImporter.run(workspaceRootProject, bazelPackagesToImport, getContainer());

        return true;
    }

    @Override
    public boolean needsProgressMonitor() {
        return true;
    }

}
