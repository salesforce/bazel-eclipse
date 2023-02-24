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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Tree;

import com.salesforce.bazel.sdk.model.BazelPackageInfo;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.project.ProjectView;
import com.salesforce.bazel.sdk.project.ProjectViewConstants;

/**
 * Builds and handles interaction with the project tree view on the Bazel import workspace wizard.
 */
public class BazelImportWizardProjectTree {
    private static final Object[] EMPTY = {};

    private static String readFile(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String rootWorkspaceDirectory;
    private Button btnSelectTree;
    private Button btnDeselectTree;

    Button importProjectViewButton;
    BazelImportWizardPage page;

    BazelImportWizardLabelProvider labelProvider;

    CheckboxTreeViewer projectTreeViewer;

    public BazelImportWizardProjectTree(BazelImportWizardPage page, BazelImportWizardLabelProvider labelProvider) {
        this.page = page;
        this.labelProvider = labelProvider;
    }

    public void addProjectTree(Composite composite) {
        final var projectsLabel = new Label(composite, SWT.NONE);
        projectsLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
        projectsLabel.setText("Bazel Java Packages:");

        projectTreeViewer = new CheckboxTreeViewer(composite, SWT.BORDER);

        projectTreeViewer.addCheckStateListener(event -> {
            updateCheckedState();
            page.setPageComplete();
        });

        projectTreeViewer.addSelectionChangedListener(event -> {
            var selection = (IStructuredSelection) event.getSelection();
            btnSelectTree.setEnabled(!selection.isEmpty());
            btnDeselectTree.setEnabled(!selection.isEmpty());
            if (selection.getFirstElement() != null) {
                var errorMsg = validateProjectInfo();
                if (errorMsg != null) {
                    page.setMessage(errorMsg, IMessageProvider.WARNING);
                } else {
                    //TODO if no error on current, shall show any existing general errors if found..
                    page.setMessage(page.loadingErrorMessage, IMessageProvider.WARNING);
                }
            } else {
                //TODO if on current selection, shall show any existing general errors if existing..
                page.setMessage(page.loadingErrorMessage, IMessageProvider.WARNING);
            }
        });

        projectTreeViewer.setContentProvider(new ITreeContentProvider() {

            @Override
            public void dispose() {}

            @Override
            public Object[] getChildren(Object parentElement) {
                if (parentElement instanceof List) {
                    @SuppressWarnings("unchecked")
                    var projects = (List<BazelPackageInfo>) parentElement;
                    return projects.toArray(new BazelPackageInfo[projects.size()]);
                }
                if (parentElement instanceof BazelPackageInfo bazelProjectInfo) {
                    var packages = bazelProjectInfo.getChildPackageInfos();
                    return packages.toArray(new BazelPackageInfo[packages.size()]);
                }
                return EMPTY;
            }

            @Override
            public Object[] getElements(Object element) {
                if (element instanceof List) {
                    @SuppressWarnings("unchecked")
                    var projects = (List<BazelPackageInfo>) element;
                    return projects.toArray(new BazelPackageInfo[projects.size()]);
                }
                return EMPTY;
            }

            @Override
            public Object getParent(Object element) {
                return null;
            }

            @Override
            public boolean hasChildren(Object parentElement) {
                if (parentElement instanceof List) {
                    List<?> projects = (List<?>) parentElement;
                    return !projects.isEmpty();
                }
                if (parentElement instanceof BazelPackageInfo bazelPackageInfo) {
                    return !bazelPackageInfo.getChildPackageInfos().isEmpty();
                }
                return false;
            }

            @Override
            public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}
        });

        projectTreeViewer.setLabelProvider(new DelegatingStyledCellLabelProvider(labelProvider));

        final var projectTree = projectTreeViewer.getTree();
        var projectTreeData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 5);
        projectTreeData.heightHint = 250;
        projectTreeData.widthHint = 500;
        projectTree.setLayoutData(projectTreeData);

        var menu = new Menu(projectTree);
        projectTree.setMenu(menu);

        var mntmSelectTree = new MenuItem(menu, SWT.NONE);
        mntmSelectTree.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                setProjectSubtreeChecked(page, true);
            }
        });
        mntmSelectTree.setText("Bazel Text 8");

        var mntmDeselectTree = new MenuItem(menu, SWT.NONE);
        mntmDeselectTree.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                setProjectSubtreeChecked(page, false);
            }
        });
        mntmDeselectTree.setText("Bazel Text 9");

        final var selectAllButton = new Button(composite, SWT.NONE);
        selectAllButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
        selectAllButton.setText("Select All");
        selectAllButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                projectTreeViewer.expandAll();
                setAllChecked(true);
                // projectTreeViewer.setSubtreeChecked(projectTreeViewer.getInput(), true);
                validate(page);
            }
        });

        final var deselectAllButton = new Button(composite, SWT.NONE);
        deselectAllButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
        deselectAllButton.setText("Deselect All");
        deselectAllButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                setAllChecked(false);
                // projectTreeViewer.setSubtreeChecked(projectTreeViewer.getInput(), false);
                page.setPageComplete(false);
            }
        });

        btnSelectTree = new Button(composite, SWT.NONE);
        btnSelectTree.setEnabled(false);
        btnSelectTree.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                setProjectSubtreeChecked(true);
            }
        });
        btnSelectTree.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        btnSelectTree.setText("Select Tree");

        btnDeselectTree = new Button(composite, SWT.NONE);
        btnDeselectTree.setEnabled(false);
        btnDeselectTree.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                setProjectSubtreeChecked(false);
            }
        });
        btnDeselectTree.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
        btnDeselectTree.setText("Deselect Tree");

        final var refreshButton = new Button(composite, SWT.NONE);
        refreshButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, true));
        refreshButton.setText("Refresh");
        refreshButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                page.scanProjects();
            }
        });

        importProjectViewButton = new Button(composite, SWT.NONE);
        importProjectViewButton.setEnabled(false);
        importProjectViewButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, true));
        importProjectViewButton.setText("Import Project View");
        importProjectViewButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                var dialog = new FileDialog(page.getShell());
                dialog.setText("Locate the Project View file to import");
                dialog.setFileName(ProjectViewConstants.PROJECT_VIEW_FILE_NAME);
                dialog.setFilterPath(rootWorkspaceDirectory);
                var path = dialog.open();
                if (path != null) {
                    Set<BazelPackageInfo> packagesToImport = new HashSet<>();
                    var projectView = new ProjectView(new File(rootWorkspaceDirectory), readFile(path));
                    Set<String> projectViewPaths = projectView.getDirectories().stream()
                            .map(BazelPackageLocation::getBazelPackageFSRelativePath).collect(Collectors.toSet());
                    for (BazelPackageInfo bpi : getAllBazelPackageInfos()) {
                        if (projectViewPaths.contains(bpi.getBazelPackageFSRelativePath())) {
                            packagesToImport.add(bpi);
                        }
                    }
                    for (BazelPackageInfo bpi : packagesToImport) {
                        projectTreeViewer.setChecked(bpi, true);
                    }
                    MessageDialog.openInformation(page.getShell(), "Imported Project View",
                        "Selected " + packagesToImport.size() + " Bazel Packages to import");
                    page.setPageComplete();
                }
            }
        });
    }

    private List<BazelPackageInfo> getAllBazelPackageInfos() {
        // seems hacky - but this will change again as we update the import UI
        setAllChecked(true);
        try {
            return Arrays.stream(projectTreeViewer.getCheckedElements()).filter(el -> (el instanceof BazelPackageInfo))
                    .map(el -> (BazelPackageInfo) el).collect(Collectors.toList());
        } finally {
            setAllChecked(false);
        }

    }

    private boolean isAlreadyImported() {

        // TODO implement logic to determine if a Bazel package has already been imported

        return false;
    }

    void setAllChecked(boolean state) {
        @SuppressWarnings("unchecked")
        var input = (List<BazelPackageInfo>) projectTreeViewer.getInput();
        if (input != null) {
            for (BazelPackageInfo bazelProjectInfo : input) {
                setSubtreeChecked(bazelProjectInfo, state);
            }
            updateCheckedState();
        }
    }

    void setProjectSubtreeChecked(BazelImportWizardPage page, boolean checked) {
        var selection = (ITreeSelection) projectTreeViewer.getSelection();
        setSubtreeChecked(selection.getFirstElement(), checked);
        updateCheckedState();
        page.setPageComplete();
    }

    void setProjectSubtreeChecked(boolean checked) {
        var selection = (ITreeSelection) projectTreeViewer.getSelection();
        setSubtreeChecked(selection.getFirstElement(), checked);
        updateCheckedState();
        page.setPageComplete();
    }

    void setRootWorkspaceDirectory(String rootWorkspaceDirectory) {
        this.rootWorkspaceDirectory = rootWorkspaceDirectory;
    }

    void setSubtreeChecked(Object obj, boolean checked) {
        // CheckBoxTreeViewer#setSubtreeChecked is severely inefficient
        projectTreeViewer.setChecked(obj, checked);
        var children = ((ITreeContentProvider) projectTreeViewer.getContentProvider()).getChildren(obj);
        if (children != null) {
            for (Object child : children) {
                setSubtreeChecked(child, checked);
            }
        }
    }

    public void updateCheckedState() {
        var elements = projectTreeViewer.getCheckedElements();
        for (Object element : elements) {
            if (element instanceof BazelPackageInfo info) {
                if (isAlreadyImported()) {
                    projectTreeViewer.setChecked(info, false);
                }
            }
        }
    }

    protected void validate(BazelImportWizardPage page) {
        if (projectTreeViewer.getControl().isDisposed()) {
            return;
        }
        var elements = projectTreeViewer.getCheckedElements();
        for (Object element : elements) {
            if (element instanceof BazelPackageInfo) {
                var errorMsg = validateProjectInfo();
                if (errorMsg != null) {
                    page.setPageComplete(false);
                    return;
                }
            }
        }
        page.setMessage(null);
        page.setPageComplete();
        projectTreeViewer.refresh();
    }

    private String validateProjectInfo() {

        // TODO m2e plugin does a lot validation logic here  MavenImportWizardPage.validateProjectInfo
        // set page.loadingErrorMessage on error

        return null;
    }
}
