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
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringButtonDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringDialogField;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Text;

import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.core.projectview.BazelProjectFileReader;

/**
 * Class that sets up the UI for the Bazel Import Workspace wizard.
 */
@SuppressWarnings("restriction")
public class BazelImportWizardMainPage extends WizardPage {

    private class ProjectViewAdapter implements IDialogFieldListener, IStringButtonAdapter {

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

    private static final String IMPORT_BAZEL_PROJECT_VIEW = "Import Bazel Project View.";

    private final StringButtonDialogField projectViewDialogField;
    private StringDialogField workspaceInfoField;

    private IPath bazelWorkspaceRoot;
    private IPath bazelProjectView;

    public BazelImportWizardMainPage() {
        super("Import Bazel Workspace");

        setTitle("Import Bazel Workspace");
        setDescription(IMPORT_BAZEL_PROJECT_VIEW);

        var adapter = new ProjectViewAdapter();

        projectViewDialogField = new StringButtonDialogField(adapter);
        projectViewDialogField.setLabelText("Project View:");
        projectViewDialogField.setButtonLabel("Bro&wse...");
        projectViewDialogField.setDialogFieldListener(adapter);

        workspaceInfoField = new StringDialogField() {
            @Override
            protected Text createTextControl(Composite parent) {
                return new Text(parent, SWT.READ_ONLY | SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
            }
        };
        workspaceInfoField.setLabelText("Workspace:");

        doStatusLineUpdate();
    }

    private IPath chooseProjectView() {
        var dialog = new FileDialog(getShell(), SWT.OPEN | SWT.SINGLE | SWT.SHEET);
        dialog.setText("Select Project View");
        dialog.setFilterPath(System.getProperty("user.home"));
        dialog.setFilterExtensions(new String[] { "*.bazelproject", "*.*" });
        dialog.setFilterNames(new String[] { "Bazel Project View  (*.bazelproject)" });

        var selectedFile = dialog.open();
        return selectedFile != null ? new Path(selectedFile) : null;
    }

    @Override
    public void createControl(Composite parent) {
        initializeDialogUnits(parent);

        var composite = new Composite(parent, SWT.NONE);
        composite.setLayout(GridLayoutFactory.swtDefaults().numColumns(3).create());

        projectViewDialogField.doFillIntoGrid(composite, 3);
        setHorizontalGrabbing(projectViewDialogField.getTextControl(null));
        setWidthHint(projectViewDialogField.getTextControl(null), convertWidthInCharsToPixels(50));

        projectViewDialogField.postSetFocusOnDialogField(composite.getDisplay());

        workspaceInfoField.doFillIntoGrid(composite, 3);
        setHorizontalGrabbing(workspaceInfoField.getTextControl(null));
        LayoutUtil.setVerticalGrabbing(workspaceInfoField.getTextControl(null));
        setWidthHint(workspaceInfoField.getTextControl(null), convertWidthInCharsToPixels(50));

        setControl(composite);
        applyDialogFont(composite);
    }

    private boolean detectPageComplete() {
        bazelProjectView = null;
        bazelWorkspaceRoot = null;
        workspaceInfoField.setText("");

        var projectViewValue = projectViewDialogField.getText();
        if (projectViewValue.isBlank()) {
            setMessage("Select a Project View (.projectview file) to import.");
            return false;
        }
        if (!Path.isValidWindowsPath(projectViewValue) && !Path.isValidPosixPath(projectViewValue)) {
            setMessage("Invalid path value. Please enter a valid path!");
            return false;
        }

        var projectViewPath = new Path(projectViewValue);
        if (!isRegularFile(projectViewPath.toPath())) {
            setMessage("Project View not found!");
            return false;
        }

        var workspaceRoot = findWorkspaceRoot(projectViewPath);
        if (workspaceRoot == null) {
            setMessage(
                "Project View outside Bazel workspace. Please select a .projectview file which is located within a Bazel workspace!");
            return false;
        }

        try {
            var info = readWorkspaceInfo(projectViewPath, workspaceRoot);
            workspaceInfoField.setText(info.toString());
        } catch (IOException e) {
            workspaceInfoField.setText(e.getMessage());
            setMessage("Error reading Project View. Please select a different one!");
            return false;
        }

        bazelProjectView = projectViewPath;
        bazelWorkspaceRoot = workspaceRoot;

        // all good
        setMessage(IMPORT_BAZEL_PROJECT_VIEW);
        return true;
    }

    protected void doChangeControlPressed() {
        var projectView = chooseProjectView();
        if (projectView != null) {
            projectViewDialogField.setText(projectView.toString());
        }
    }

    protected void doStatusLineUpdate() {
        setPageComplete(detectPageComplete());
    }

    private IPath findWorkspaceRoot(IPath projectView) {
        var workspaceRoot = projectView.toPath().getParent();

        while (workspaceRoot != null) {
            if (BazelWorkspace.findWorkspaceFile(workspaceRoot) != null) {
                return new Path(workspaceRoot.toString());
            }

            workspaceRoot = workspaceRoot.getParent();
        }

        return null;
    }

    public IPath getBazelProjectView() {
        return bazelProjectView;
    }

    public IPath getBazelWorkspaceRoot() {
        return bazelWorkspaceRoot;
    }

    private StringBuilder readWorkspaceInfo(Path projectViewPath, IPath workspaceRoot) throws IOException {
        var projectView =
                new BazelProjectFileReader(projectViewPath.toPath(), workspaceRoot.toPath()).read();
        var info = new StringBuilder();
        info.append("Location:").append(System.lineSeparator()).append("  ").append(workspaceRoot)
                .append(System.lineSeparator());
        info.append("Targets:").append(System.lineSeparator());
        if (projectView.deriveTargetsFromDirectories()) {
            info.append("  ").append("derive from directories").append(System.lineSeparator());
        } else if (projectView.targetsToInclude().isEmpty()) {
            info.append("  ").append("none").append(System.lineSeparator());
        } else {
            for (String target : projectView.targetsToInclude()) {
                info.append("  ").append(target).append(System.lineSeparator());
            }
            for (String target : projectView.targetsToExclude()) {
                info.append("  -").append(target).append(System.lineSeparator());
            }
        }
        info.append("Directories:").append(System.lineSeparator());
        if (projectView.directoriesToInclude().isEmpty()) {
            info.append("  ").append(".").append(System.lineSeparator());
        } else {
            for (String target : projectView.directoriesToInclude()) {
                info.append("  ").append(target).append(System.lineSeparator());
            }
        }
        for (String target : projectView.directoriesToExclude()) {
            info.append("  ").append(target).append(System.lineSeparator());
        }
        return info;
    }

}
