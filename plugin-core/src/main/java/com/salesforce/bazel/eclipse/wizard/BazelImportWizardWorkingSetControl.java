/*******************************************************************************
 * Copyright (c) 2008-2018 Sonatype, Inc. and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Sonatype, Inc. - initial API and implementation
 *      Red Hat, Inc. - refactored lifecycle mapping discovery
 *      Salesforce - Adapted for Bazel Eclipse
 *******************************************************************************/

// adapted from M2Eclipse org.eclipse.m2e.core.ui.internal.wizards.MavenImportWizardPage

package com.salesforce.bazel.eclipse.wizard;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;

import com.salesforce.bazel.sdk.model.BazelPackageInfo;

public class BazelImportWizardWorkingSetControl {

    BazelImportWizardPage page;

    private Button createWorkingSet;
    private Combo workingSetName;
    private String preselectedWorkingSetName;

    public BazelImportWizardWorkingSetControl(BazelImportWizardPage page) {
        this.page = page;
    }

    public void addWorkingSetControl(Composite composite) {
        createWorkingSet = new Button(composite, SWT.CHECK);
        createWorkingSet.setText("Add project(s) to working set");
        createWorkingSet.setSelection(true);
        createWorkingSet.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
        createWorkingSet.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                boolean enabled = createWorkingSet.getSelection();
                workingSetName.setEnabled(enabled);
                if (enabled) {
                    workingSetName.setFocus();
                }
            }
        });

        workingSetName = new Combo(composite, SWT.BORDER);
        GridData gd_workingSet = new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1);
        gd_workingSet.horizontalIndent = 20;
        workingSetName.setLayoutData(gd_workingSet);

    }

    public void setWorkingSetName(String workingSetName) {
        this.preselectedWorkingSetName = workingSetName;
    }

    void updateWorkingSet(BazelPackageInfo rootWorkspacePackage) {

        // check if working set name was preselected
        if (preselectedWorkingSetName != null) {
            updateWorkingSet(preselectedWorkingSetName, true);
            return;
        }

        String rootDirectory = page.locationControl.rootDirectoryCombo != null
                ? page.locationControl.rootDirectoryCombo.getText().trim() : null;

        if (rootDirectory != null && rootDirectory.length() > 0) {
            Set<IWorkingSet> workingSets = new HashSet<IWorkingSet>();
            URI rootURI = new File(rootDirectory).toURI();
            IContainer[] containers = page.locationControl.workspaceRoot.findContainersForLocationURI(rootURI);

            for (IContainer container : containers) {
                workingSets.addAll(getAssignedWorkingSets(container.getProject()));
            }
            if (workingSets.size() == 1) {
                updateWorkingSet(workingSets.iterator().next().getName(), true);
                return;
            }
        }

        // derive working set name from Bazel package name
        if (rootWorkspacePackage != null) {
            updateWorkingSet(rootWorkspacePackage.getBazelPackageFSRelativePathForUI(),
                !rootWorkspacePackage.getChildPackageInfos().isEmpty());
        } else {
            updateWorkingSet(null, false);
        }
    }

    private void updateWorkingSet(String name, boolean enabled) {
        Set<String> workingSetNames = new LinkedHashSet<String>();
        if (name == null) {
            name = ""; //$NON-NLS-1$
        } else {
            workingSetNames.add(name);
        }
        workingSetNames.addAll(Arrays.asList(getWorkingSets()));
        workingSetName.setItems(workingSetNames.toArray(new String[workingSetNames.size()]));
        workingSetName.setText(name);
        createWorkingSet.setSelection(enabled);
        workingSetName.setEnabled(enabled);
    }

    public static String[] getWorkingSets() {
        List<String> workingSets = new ArrayList<String>();
        IWorkingSetManager workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager();
        for (IWorkingSet workingSet : workingSetManager.getWorkingSets()) {
            if (workingSet.isVisible()) {
                workingSets.add(workingSet.getName());
            }
        }
        return workingSets.toArray(new String[workingSets.size()]);
    }

    // Taken from org.eclipse.m2e.core.ui.internal.WorkingSets

    /**
     * Returns one of the working sets the element directly belongs to. Returns {@code null} if the element does not
     * belong to any working set.
     *
     * @since 1.5
     */
    public static IWorkingSet getAssignedWorkingSet(IResource element) {
        IWorkingSetManager workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager();
        for (IWorkingSet workingSet : workingSetManager.getWorkingSets()) {
            for (IAdaptable adaptable : workingSet.getElements()) {
                if (adaptable.getAdapter(IResource.class) == element) {
                    return workingSet;
                }
            }
        }
        return null;
    }

    /**
     * Returns all working sets the element directly belongs to. Returns empty collection if the element does not belong
     * to any working set. The order of returned working sets is not specified.
     *
     * @since 1.5
     */
    public static List<IWorkingSet> getAssignedWorkingSets(IResource element) {
        List<IWorkingSet> list = new ArrayList<IWorkingSet>();
        IWorkingSetManager workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager();
        for (IWorkingSet workingSet : workingSetManager.getWorkingSets()) {
            for (IAdaptable adaptable : workingSet.getElements()) {
                if (adaptable.getAdapter(IResource.class) == element) {
                    list.add(workingSet);
                }
            }
        }
        return list;
    }
}
