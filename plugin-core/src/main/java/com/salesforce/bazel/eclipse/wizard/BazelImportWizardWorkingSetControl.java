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

import com.salesforce.bazel.eclipse.model.BazelPackageInfo;

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

    /*******************************************************************************
     * Copyright (c) 2013 Igor Fedorenko and others. All rights reserved. This program and the accompanying materials
     * are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution, and is
     * available at http://www.eclipse.org/legal/epl-v10.html
     *
     * Contributors: Igor Fedorenko - initial API and implementation
     *******************************************************************************/

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
