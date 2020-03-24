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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;

import com.salesforce.bazel.eclipse.BazelPluginActivator;

public class BazelImportWizardLocationControl {

    protected BazelImportWizardPage page;

    protected Combo rootDirectoryCombo;
    protected String rootDirectory;

    protected List<String> locations;
    private boolean showLocation = true;

    protected IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();

    /** the Map of field ids to List of comboboxes that share the same history */
    private Map<String, List<Combo>> fieldsWithHistory;

    public BazelImportWizardLocationControl(BazelImportWizardPage page) {
        this.page = page;
    }

    public void addLocationControl(Composite composite) {
        fieldsWithHistory = new HashMap<String, List<Combo>>();

        if (showLocation || locations == null || locations.isEmpty()) {
            final Label selectRootDirectoryLabel = new Label(composite, SWT.NONE);
            selectRootDirectoryLabel.setLayoutData(new GridData());
            selectRootDirectoryLabel.setText("WORKSPACE File:");

            rootDirectoryCombo = new Combo(composite, SWT.NONE);
            rootDirectoryCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            rootDirectoryCombo.setFocus();
            addFieldWithHistory("rootDirectory", rootDirectoryCombo); //$NON-NLS-1$

            if (locations != null && locations.size() == 1) {
                rootDirectoryCombo.setText(locations.get(0));
                rootDirectory = locations.get(0);
            }

            final Button browseButton = new Button(composite, SWT.NONE);
            browseButton.setText("Browse...");
            browseButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
            browseButton.addSelectionListener(new SelectionAdapter() {
                public void widgetSelected(SelectionEvent e) {
                    FileDialog dialog = new FileDialog(page.getShell());
                    dialog.setFileName("WORKSPACE");
                    dialog.setText("Locate the Bazel WORKSPACE file");
                    String path = rootDirectoryCombo.getText();
                    if (path.length() == 0) {
                        path = BazelPluginActivator.getResourceHelper().getEclipseWorkspaceRoot().getLocation().toPortableString();
                    }
                    dialog.setFilterPath(path);

                    String selectedFile = dialog.open();
                    if (selectedFile != null) {
                        File workspaceFile = new File(selectedFile);
                        if (!workspaceFile.isFile() || !workspaceFile.getName().equals("WORKSPACE")) {
                            MessageDialog.openError(page.getShell(), "Import WORKSPACE", "You must select a Bazel WORKSPACE File");                            
                        } else {
                            rootDirectoryCombo.setText(workspaceFile.getParentFile().getAbsolutePath());
                            if (rootDirectoryChanged()) {
                                page.scanProjects();
                            }
                        }
                    }
                }
            });

            rootDirectoryCombo.addListener(SWT.Traverse, new Listener() {
                public void handleEvent(Event e) {
                    if (e.keyCode == SWT.CR && rootDirectoryChanged()) {
                        //New location entered : don't finish the wizard
                        if (e.detail == SWT.TRAVERSE_RETURN) {
                            e.doit = false;
                        }
                        page.scanProjects();
                    }
                }
            });

            rootDirectoryCombo.addFocusListener(new FocusAdapter() {
                public void focusLost(FocusEvent e) {
                    if (rootDirectoryChanged()) {
                        page.scanProjects();
                    }
                }
            });
            rootDirectoryCombo.addSelectionListener(new SelectionAdapter() {
                public void widgetDefaultSelected(SelectionEvent e) {
                    if (rootDirectoryChanged()) {
                        page.scanProjects();
                    }
                }

                public void widgetSelected(SelectionEvent e) {
                    if (rootDirectoryChanged()) {
                        //in runnable to have the combo popup collapse before disabling controls.
                        Display.getDefault().asyncExec(new Runnable() {
                            public void run() {
                                page.scanProjects();
                            }
                        });
                    }
                }
            });
        }

        if (locations != null && !locations.isEmpty()) {
            page.scanProjects();
        }

    }

    protected boolean rootDirectoryChanged() {
        String _rootDirectory = rootDirectory;
        rootDirectory = rootDirectoryCombo.getText().trim();
        IPath p = new Path(rootDirectory);
        if (p.isRoot()) {
            // This could theoretically be caused by the user entering a path into the text box manually, and forgetting
            // to provide a full path. But in practice, that error is caught before we get here, so as of now this error
            // will not happen.
            page.setErrorMessage(
                "Path [" + rootDirectory + "] is incomplete. Please provide the full path to the Bazel workspace.");
            return false;
        }
        return _rootDirectory == null || !_rootDirectory.equals(rootDirectory);
    }

    /** Adds an input control to the list of fields to save. */
    protected void addFieldWithHistory(String id, Combo combo) {
        if (combo != null) {
            List<Combo> combos = fieldsWithHistory.get(id);
            if (combos == null) {
                combos = new ArrayList<Combo>();
                fieldsWithHistory.put(id, combos);
            }
            combos.add(combo);
        }
    }

}
