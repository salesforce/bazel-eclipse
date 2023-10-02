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

package com.salesforce.bazel.eclipse.ui.wizards;

import static java.lang.String.format;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;
import static org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil.setHorizontalGrabbing;
import static org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil.setWidthHint;
import static org.eclipse.jface.dialogs.Dialog.applyDialogFont;

import java.io.IOException;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogFieldGroup;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringButtonDialogField;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.BazelCore;
import com.salesforce.bazel.eclipse.core.setup.DefaultProjectViewFileInitializer;

/**
 * Class that sets up the UI for the Bazel Import Workspace wizard.
 */
@SuppressWarnings("restriction")
public class BazelImportWizardMainPage extends WizardPage {

    private class WorkspaceAdapter implements IDialogFieldListener, IStringButtonAdapter {

        // -------- IDialogFieldListener

        @Override
        public void changeControlPressed(DialogField field) {
            doChangeControlPressed();
        }

        @Override
        public void dialogFieldChanged(DialogField field) {
            doStatusLineUpdate();
        }
    }

    private static Logger LOG = LoggerFactory.getLogger(BazelImportWizardMainPage.class);

    private static final String DESC_IMPORT_WORKSPACE = "Import a Bazel Workspace into Eclipse.";

    private final StringButtonDialogField workspaceDialogField;

    private IPath bazelProjectView;

    private final SelectionButtonDialogFieldGroup workspaceOrProjectViewButtons;

    public BazelImportWizardMainPage() {
        super("Import Bazel Workspace");

        setTitle("Import Bazel Workspace");
        setDescription(DESC_IMPORT_WORKSPACE);

        workspaceOrProjectViewButtons = new SelectionButtonDialogFieldGroup(
                SWT.RADIO,
                new String[] { "Select an existing Project View in the next step",
                        "Select a Bazel Workspace folder below and create a default Project View" },
                1);

        workspaceOrProjectViewButtons.setLabelText("Project View (.bazelproject file)");
        workspaceOrProjectViewButtons.setDialogFieldListener(field -> {
            doStatusLineUpdate();
        });

        var adapter = new WorkspaceAdapter();

        workspaceDialogField = new StringButtonDialogField(adapter);
        workspaceDialogField.setLabelText("Workspace:");
        workspaceDialogField.setButtonLabel("Bro&wse...");
        workspaceDialogField.setDialogFieldListener(adapter);

        doStatusLineUpdate();
    }

    private IPath chooseWorkspace() {
        var dialog = new DirectoryDialog(getShell());
        dialog.setText("Select Workspace");
        dialog.setMessage("Select the Bazel Workspace folder root directory.");

        var selectedDirectory = dialog.open();
        return selectedDirectory != null ? Path.fromOSString(selectedDirectory) : null;
    }

    @Override
    public void createControl(Composite parent) {
        initializeDialogUnits(parent);

        var composite = new Composite(parent, SWT.NONE);
        composite.setLayout(GridLayoutFactory.swtDefaults().numColumns(3).create());

        workspaceOrProjectViewButtons.doFillIntoGrid(composite, 3);
        setHorizontalGrabbing(workspaceOrProjectViewButtons.getSelectionButtonsGroup(null));

        workspaceDialogField.doFillIntoGrid(composite, 3);
        setHorizontalGrabbing(workspaceDialogField.getTextControl(null));
        setWidthHint(workspaceDialogField.getTextControl(null), convertWidthInCharsToPixels(50));

        workspaceOrProjectViewButtons.postSetFocusOnDialogField(composite.getDisplay());

        setControl(composite);
        applyDialogFont(composite);
    }

    private boolean detectPageComplete() {
        bazelProjectView = null;

        if (workspaceOrProjectViewButtons.isSelected(1)) {
            workspaceDialogField.setEnabled(true);

            var workspaceFolderValue = workspaceDialogField.getText();
            if (workspaceFolderValue.isBlank()) {
                setMessage("Select a Bazel Workspace folder.");
                return false;
            }
            if (!Path.isValidWindowsPath(workspaceFolderValue) && !Path.isValidPosixPath(workspaceFolderValue)) {
                setMessage("Invalid path value. Please enter a valid path!");
                return false;
            }

            var workspaceRoot = Path.fromOSString(workspaceFolderValue);
            if (!isDirectory(workspaceRoot.toPath())) {
                setMessage("Bazel Workspace folder not found!");
                return false;
            }

            var bazelWorkspace = BazelCore.createWorkspace(workspaceRoot);
            if (!bazelWorkspace.exists()) {
                setMessage("Not a valid Bazel Workspace. Please select a Bazel Workspace root folder!");
                return false;
            }

            var projectViewLocation = bazelWorkspace.getBazelProjectFileSystemMapper().getProjectViewLocation();
            if (!isRegularFile(projectViewLocation.toPath())) {
                try {
                    new DefaultProjectViewFileInitializer(bazelWorkspace.getLocation().toPath())
                            .create(projectViewLocation.toPath());
                } catch (IOException e) {
                    LOG.error(
                        "Error creating default project view at '{}': {}",
                        projectViewLocation,
                        e.getMessage(),
                        e);
                    setErrorMessage(format("Unable to create default project view at '%s'", projectViewLocation));
                    return false;
                }
            }

            this.bazelProjectView = projectViewLocation;
        } else {
            workspaceDialogField.setEnabled(false);

            if (!workspaceOrProjectViewButtons.isSelected(0)) {
                setMessage(DESC_IMPORT_WORKSPACE);
                return false;
            }
        }

        // all good
        setErrorMessage(null);
        setMessage(DESC_IMPORT_WORKSPACE);
        return true;
    }

    protected void doChangeControlPressed() {
        var projectView = chooseWorkspace();
        if (projectView != null) {
            workspaceDialogField.setText(projectView.toString());
        }
    }

    protected void doStatusLineUpdate() {
        try {
            setPageComplete(detectPageComplete());
        } catch (Exception | LinkageError e) {
            setErrorMessage(format("A runtime error occured. Please check the Eclipse error log for details. (%s)", e));
            setPageComplete(false);
        }
    }

    public IPath getBazelProjectView() {
        return bazelProjectView;
    }

}
