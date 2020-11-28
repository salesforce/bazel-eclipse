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
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
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
import com.salesforce.bazel.sdk.project.ProjectView;
import com.salesforce.bazel.sdk.project.ProjectViewConstants;

/**
 * Builds and handles interaction with the project tree view on the Bazel import workspace wizard.
 */
public class BazelImportWizardProjectTree {
    private static final Object[] EMPTY = new Object[0];

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
        final Label projectsLabel = new Label(composite, SWT.NONE);
        projectsLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));
        projectsLabel.setText("Bazel Java Packages:");

        projectTreeViewer = new CheckboxTreeViewer(composite, SWT.BORDER);

        projectTreeViewer.addCheckStateListener(new ICheckStateListener() {
            @Override
            public void checkStateChanged(CheckStateChangedEvent event) {
                updateCheckedState();
                page.setPageComplete();
            }
        });

        projectTreeViewer.addSelectionChangedListener(new ISelectionChangedListener() {

            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                IStructuredSelection selection = (IStructuredSelection) event.getSelection();
                btnSelectTree.setEnabled(!selection.isEmpty());
                btnDeselectTree.setEnabled(!selection.isEmpty());
                if (selection.getFirstElement() != null) {
                    String errorMsg = validateProjectInfo((BazelPackageInfo) selection.getFirstElement());
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
            }
        });

        projectTreeViewer.setContentProvider(new ITreeContentProvider() {

            @Override
            public Object[] getElements(Object element) {
                if (element instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<BazelPackageInfo> projects = (List<BazelPackageInfo>) element;
                    return projects.toArray(new BazelPackageInfo[projects.size()]);
                }
                return EMPTY;
            }

            @Override
            public Object[] getChildren(Object parentElement) {
                if (parentElement instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<BazelPackageInfo> projects = (List<BazelPackageInfo>) parentElement;
                    return projects.toArray(new BazelPackageInfo[projects.size()]);
                } else if (parentElement instanceof BazelPackageInfo) {
                    BazelPackageInfo bazelProjectInfo = (BazelPackageInfo) parentElement;
                    Collection<BazelPackageInfo> packages = bazelProjectInfo.getChildPackageInfos();
                    return packages.toArray(new BazelPackageInfo[packages.size()]);
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
                } else if (parentElement instanceof BazelPackageInfo) {
                    BazelPackageInfo bazelPackageInfo = (BazelPackageInfo) parentElement;
                    return !bazelPackageInfo.getChildPackageInfos().isEmpty();
                }
                return false;
            }

            @Override
            public void dispose() {}

            @Override
            public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}
        });

        projectTreeViewer.setLabelProvider(new DelegatingStyledCellLabelProvider(labelProvider));

        final Tree projectTree = projectTreeViewer.getTree();
        GridData projectTreeData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 5);
        projectTreeData.heightHint = 250;
        projectTreeData.widthHint = 500;
        projectTree.setLayoutData(projectTreeData);

        Menu menu = new Menu(projectTree);
        projectTree.setMenu(menu);

        MenuItem mntmSelectTree = new MenuItem(menu, SWT.NONE);
        mntmSelectTree.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                setProjectSubtreeChecked(page, true);
            }
        });
        mntmSelectTree.setText("Bazel Text 8");

        MenuItem mntmDeselectTree = new MenuItem(menu, SWT.NONE);
        mntmDeselectTree.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                setProjectSubtreeChecked(page, false);
            }
        });
        mntmDeselectTree.setText("Bazel Text 9");

        final Button selectAllButton = new Button(composite, SWT.NONE);
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

        final Button deselectAllButton = new Button(composite, SWT.NONE);
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

        final Button refreshButton = new Button(composite, SWT.NONE);
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
                FileDialog dialog = new FileDialog(page.getShell());
                dialog.setText("Locate the Project View file to import");
                dialog.setFileName(ProjectViewConstants.PROJECT_VIEW_FILE_NAME);
                dialog.setFilterPath(BazelImportWizardProjectTree.this.rootWorkspaceDirectory);
                String path = dialog.open();
                if (path != null) {
                    Set<BazelPackageInfo> packagesToImport = new HashSet<>();
                    ProjectView projectView = new ProjectView(
                            new File(BazelImportWizardProjectTree.this.rootWorkspaceDirectory), readFile(path));
                    Set<String> projectViewPaths = projectView.getDirectories().stream()
                            .map(p -> p.getBazelPackageFSRelativePath()).collect(Collectors.toSet());
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

    private static String readFile(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
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

    public void updateCheckedState() {
        Object[] elements = projectTreeViewer.getCheckedElements();
        for (int i = 0; i < elements.length; i++) {
            Object element = elements[i];
            if (element instanceof BazelPackageInfo) {
                BazelPackageInfo info = (BazelPackageInfo) element;
                if (isAlreadyImported(info)) {
                    projectTreeViewer.setChecked(info, false);
                }
            }
        }
    }

    private String validateProjectInfo(BazelPackageInfo info) {

        // TODO m2e plugin does a lot validation logic here  MavenImportWizardPage.validateProjectInfo
        // set page.loadingErrorMessage on error

        return null;
    }

    private boolean isAlreadyImported(BazelPackageInfo info) {

        // TODO implement logic to determine if a Bazel package has already been imported

        return false;
    }

    protected void validate(BazelImportWizardPage page) {
        if (projectTreeViewer.getControl().isDisposed()) {
            return;
        }
        Object[] elements = projectTreeViewer.getCheckedElements();
        for (int i = 0; i < elements.length; i++) {
            Object element = elements[i];
            if (element instanceof BazelPackageInfo) {
                String errorMsg = validateProjectInfo((BazelPackageInfo) element);
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

    void setAllChecked(boolean state) {
        @SuppressWarnings("unchecked")
        List<BazelPackageInfo> input = (List<BazelPackageInfo>) projectTreeViewer.getInput();
        if (input != null) {
            for (BazelPackageInfo bazelProjectInfo : input) {
                setSubtreeChecked(bazelProjectInfo, state);
            }
            updateCheckedState();
        }
    }

    void setSubtreeChecked(Object obj, boolean checked) {
        // CheckBoxTreeViewer#setSubtreeChecked is severely inefficient
        projectTreeViewer.setChecked(obj, checked);
        Object[] children = ((ITreeContentProvider) projectTreeViewer.getContentProvider()).getChildren(obj);
        if (children != null) {
            for (Object child : children) {
                setSubtreeChecked(child, checked);
            }
        }
    }

    void setProjectSubtreeChecked(BazelImportWizardPage page, boolean checked) {
        ITreeSelection selection = (ITreeSelection) projectTreeViewer.getSelection();
        setSubtreeChecked(selection.getFirstElement(), checked);
        updateCheckedState();
        page.setPageComplete();
    }

    void setProjectSubtreeChecked(boolean checked) {
        ITreeSelection selection = (ITreeSelection) projectTreeViewer.getSelection();
        setSubtreeChecked(selection.getFirstElement(), checked);
        updateCheckedState();
        page.setPageComplete();
    }

    void setRootWorkspaceDirectory(String rootWorkspaceDirectory) {
        this.rootWorkspaceDirectory = rootWorkspaceDirectory;
    }
}
