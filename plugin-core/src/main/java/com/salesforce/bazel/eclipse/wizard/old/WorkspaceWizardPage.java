// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.salesforce.bazel.eclipse.wizard.old;

import java.io.File;
import java.util.function.Consumer;

import org.eclipse.jface.bindings.keys.KeyStroke;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Text;

import com.google.common.collect.ImmutableList;
import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.logging.LogHelper;
import com.salesforce.bazel.eclipse.model.BazelWorkspace;
import com.salesforce.bazel.eclipse.model.RealOperatingEnvironmentDetectionStrategy;

/**
 * This is a quick wizard page that ask the user for the various targets and source path he wants to include.
 * 
 * REFERENCE ONLY! This is the old mechanism from the original Bazel plugin. It used the New... extension point instead
 * of the Import... extension point.
 */
public class WorkspaceWizardPage extends WizardPage {
    static final LogHelper LOG = LogHelper.log(WorkspaceWizardPage.class);
    private Label workspaceRoot;
    private List targets;
    private Text target;
    private Button addTargetButton;
    private Button removeTargetButton;
    private CheckboxTreeViewer directories;
    private Composite container;
    private Button workspaceRootButton;
    private DirectoryDialog dialog;
    private BazelTargetCompletionContentProposalProvider completionProvider;

    protected WorkspaceWizardPage() {
        super("Import Bazel project");
        setTitle("Import Bazel project");
    }

    /**
     * Returns the list of targets selected by the user.
     */
    ImmutableList<String> getTargets() {
        return ImmutableList.copyOf(targets.getItems());
    }

    /**
     * Returns the list of directories selected by the user.
     */
    ImmutableList<String> getDirectories() {
        return DirectoryTreeContentProvider.getSelectPathsRelativeToRoot(directories);
    }

    /**
     * Returns the workspace root selected by the user.
     */
    public String getWorkspaceRoot() {
        return workspaceRoot.getText();
    }

    /**
     * Returns the language version for the new project.
     */
    int getJavaLanguageVersion() {
        return 8;
    }

    @Override
    public void createControl(Composite parent) {
        container = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.numColumns = 3;
        container.setLayout(layout);

        createWorkspaceSelectionControls();

        addLabel("Java Source Directories:\n(check the box for\neach directory that\nis the parent of\nJava packages)",
            1, 5);
        directories = DirectoryTreeContentProvider.createTreeView(container);
        setControlGridData(directories.getTree(), 2, 5, true);
        directories.addCheckStateListener(e -> updateControls());

        new Label(container, SWT.NONE)
                .setText("Bazel Targets\n(the classpath of each\nentry will be added to\nthe Eclipse tooling):");
        createTargetTextField();
        setControlGridData(target, 1, 1, false);
        addTargetButton = createButton("+", e -> addTarget());

        targets = new List(container, SWT.SINGLE | SWT.BORDER);
        setControlGridData(targets, 2, 5, true);
        removeTargetButton = createButton("-", e -> deleteTarget());
        targets.addSelectionListener(
            createSelectionListener(e -> removeTargetButton.setEnabled(targets.getSelectionCount() > 0)));

        setControl(container);
        updateControls();
    }

    // A simple helper to use lambdas to create a SelectionListener that does the same action on all
    // cases.
    private static SelectionListener createSelectionListener(Consumer<SelectionEvent> f) {
        return new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                f.accept(e);

            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                f.accept(e);
            }
        };
    }

    private Button createButton(String text, Consumer<SelectionEvent> handler) {
        Button result = new Button(container, SWT.DEFAULT);
        result.setText(text);
        result.addSelectionListener(createSelectionListener(handler));
        result.setEnabled(false);
        return result;
    }

    private void createWorkspaceSelectionControls() {
        Label label = new Label(container, SWT.NONE);
        label.setText("Workpsace root: ");

        workspaceRoot = new Label(container, SWT.BORDER);
        workspaceRoot.setText("");
        workspaceRoot.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false));

        dialog = new DirectoryDialog(getShell(), SWT.OPEN);
        workspaceRootButton = new Button(container, SWT.DEFAULT);
        workspaceRootButton.setText("...");
        workspaceRootButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String wr = dialog.open();
                if (wr != null) {
                    workspaceRoot.setText(wr);
                    DirectoryTreeContentProvider.setFileTreeRoot(directories, new File(wr));
                    BazelWorkspace bazelWorkspace = new BazelWorkspace(null, new File(getWorkspaceRoot()), new RealOperatingEnvironmentDetectionStrategy());
                    completionProvider.setBazelInstance(BazelPluginActivator.getBazelCommandManager()
                            .getWorkspaceCommandRunner(bazelWorkspace));
                }
                updateControls();
            }
        });
        workspaceRootButton.setLayoutData(new GridData(GridData.END, GridData.CENTER, false, false));
    }

    private void setControlGridData(Control control, int horizontalSpan, int verticalSpan, boolean verticalGrow) {
        GridData data = new GridData(GridData.FILL, verticalGrow ? GridData.FILL : GridData.CENTER, true, verticalGrow);
        data.horizontalSpan = horizontalSpan;
        data.verticalSpan = verticalSpan;
        control.setLayoutData(data);
    }

    private void addLabel(String labelText, int horizontalSpan, int verticalSpan) {
        Label label = new Label(container, SWT.NONE);
        label.setText(labelText);
        GridData data = new GridData(GridData.BEGINNING, GridData.FILL, false, true);
        data.horizontalSpan = horizontalSpan;
        data.verticalSpan = verticalSpan;
        label.setLayoutData(data);
    }

    private void updateControls() {
        boolean enabled = !workspaceRoot.getText().isEmpty();
        directories.getTree().setEnabled(enabled);
        targets.setEnabled(enabled);
        target.setEnabled(enabled);
        addTargetButton.setEnabled(enabled && !target.getText().isEmpty());
        removeTargetButton.setEnabled(enabled && targets.getSelectionCount() > 0);
        setPageComplete(enabled && (directories.getCheckedElements().length > 0) && (targets.getItemCount() > 0));
    }

    private void setAutoCompletion() {
        try {
            ContentProposalAdapter adapter = null;
            completionProvider = new BazelTargetCompletionContentProposalProvider();
            KeyStroke ks = KeyStroke.getInstance("Ctrl+Space");
            adapter = new ContentProposalAdapter(target, new TextContentAdapter(), completionProvider, ks, null);
            adapter.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private void createTargetTextField() {
        target = new Text(container, SWT.BORDER);
        setAutoCompletion();
        target.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent ke) {
                if (ke.keyCode == '\r' && (ke.stateMask & SWT.SHIFT) != 0 && !target.getText().isEmpty()) {
                    addTarget();
                }
            }
        });
        target.addModifyListener(e -> updateControls());
    }

    private void addTarget() {
        targets.add(target.getText());
        target.setText("");
        setAutoCompletion();
        updateControls();
    }

    private void deleteTarget() {
        targets.remove(targets.getSelectionIndices());
        updateControls();
    }
}
