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
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
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
import com.salesforce.bazel.eclipse.preferences.BazelPreferenceKeys;

/**
 * This class encapsulates the two controls above the tree control on the first page of the import wizard.
 * The combo box which holds the chosen file system path, and the Browse button.
 */
public class BazelImportWizardLocationControl {

    protected BazelImportWizardPage page;

    // We support Bazel workspaces in which the WORKSPACE file in the root is actually a soft link to the actual
    // file in a subdirectory. Due to the way the system Open dialog works, we have to do some sad logic to figure
    // out this is the case. This features is enabled if this boolean is true.
    // https://github.com/salesforce/bazel-eclipse/issues/164
    // Please drop a note in that Issue if this feature causes a problem for you.
    // Disable it with this pref in your global prefs file (~/.bazel/eclipse.properties)
    //    DISABLE_UNRESOLVE_WORKSPACEFILE_SOFTLINK=true
    protected boolean doUnresolveWorkspaceFileSoftLink = true;

    protected Combo rootDirectoryCombo;
    protected String rootDirectory;

    protected List<String> locations;
    private boolean showLocation = true;

    protected IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();

    /** the Map of field ids to List of comboboxes that share the same history */
    private Map<String, List<Combo>> fieldsWithHistory;

    public BazelImportWizardLocationControl(BazelImportWizardPage page, IPreferenceStore prefs) {
        this.page = page;

        if (prefs != null) {
            // default response is false if the pref is not set
            doUnresolveWorkspaceFileSoftLink = !prefs.getBoolean(BazelPreferenceKeys.DISABLE_UNRESOLVE_WORKSPACEFILE_SOFTLINK);
        }
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
                        path = BazelPluginActivator.getResourceHelper().getEclipseWorkspaceRoot().getLocation()
                                .toPortableString();
                    }
                    dialog.setFilterPath(path);

                    String selectedFile = dialog.open();
                    if (selectedFile != null) {
                        File workspaceFile = new File(selectedFile);
                        if (!workspaceFile.isFile() || !hasWorkspaceFilename(workspaceFile)) {
                            Display.getDefault().syncExec(new Runnable() {
                                public void run() {
                                    MessageDialog.openError(page.getShell(), "Import WORKSPACE",
                                        "You must select a Bazel WORKSPACE File");
                                }
                            });
                        } else {
                            workspaceFile = unresolveSoftLink(workspaceFile);
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

    // See note at top of file for what this feature does and how to disable it if it causes problems
    protected File unresolveSoftLink(File resolvedWorkspaceFile) {
        if (!doUnresolveWorkspaceFileSoftLink) {
            return resolvedWorkspaceFile;
        }

        try {
            if (!resolvedWorkspaceFile.exists()) {
                return resolvedWorkspaceFile;
            }
            File directory = resolvedWorkspaceFile.getParentFile().getParentFile();
            // traverse up in the directory hierarchy, looking for a WORKSPACE file that is a soft link
            // to the one returned by the system Open dialog. We max it out at 5 since it seems unreasonable
            // to go further
            for (int i=0; i<5; i++) {
                if (!directory.exists() || !directory.canRead()) {
                    return resolvedWorkspaceFile;
                }
                File[] workspaceFiles = directory.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return hasWorkspaceFilename(name);
                    }
                });
                for (File candidateFile : workspaceFiles) {
                    if (candidateFile.exists() && candidateFile.canRead()) {
                        String absPath = candidateFile.getAbsolutePath();
                        String canonPath = candidateFile.getCanonicalFile().getAbsolutePath();
                        if (canonPath.equals(resolvedWorkspaceFile.getAbsolutePath())) {

                            // The candidateFile is a soft link to the real WORKSPACE file returned by the Open dialog
                            // This means the candidateFile is probably in the real root directory of the Bazel workspace.

                            System.out.println("WORKSPACE file ["+absPath+"] is a soft link to ["+canonPath+
                                "]. Setting workspace root as ["+candidateFile.getParentFile().getAbsolutePath()+"]");
                            return candidateFile;
                        }
                    }
                }
            }
        } catch (Exception anyE) {
            anyE.printStackTrace();
            return resolvedWorkspaceFile;
        }

        return resolvedWorkspaceFile;
    }

    // TODO move these functions to bazel sdk
    protected boolean hasWorkspaceFilename(File candidateWorkspaceFile) {
        return hasWorkspaceFilename(candidateWorkspaceFile.getName());
    }

    protected boolean hasWorkspaceFilename(String candidateWorkspaceFilename) {
        if (candidateWorkspaceFilename.equals("WORKSPACE")) {
            return true;
        }
        if (candidateWorkspaceFilename.equals("WORKSPACE.bazel")) {
            return true;
        }
        return false;
    }

}
