/*-
 *  Copyright (c) 2000 IBM Corporation and others.
 *
 *  This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License 2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-2.0/
 *
 *  SPDX-License-Identifier: EPL-2.0
 *
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *     Salesforce - copied and adapted from JDT BazelArgumentsTab
 */
package com.salesforce.bazel.eclipse.ui.launchconfiguration;

import static com.salesforce.bazel.eclipse.core.launchconfiguration.BazelLaunchConfigurationConstants.RUN_ARGS;
import static com.salesforce.bazel.eclipse.core.launchconfiguration.BazelLaunchConfigurationConstants.TARGET_ARGS;
import static com.salesforce.bazel.eclipse.core.launchconfiguration.BazelLaunchConfigurationConstants.WORKING_DIRECTORY;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.debug.ui.ILaunchConfigurationDialog;
import org.eclipse.debug.ui.StringVariableSelectionDialog;
import org.eclipse.debug.ui.WorkingDirectoryBlock;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.launchconfiguration.BazelLaunchConfigurationConstants;
import com.salesforce.bazel.eclipse.ui.BazelUIPlugin;

/**
 * A launch configuration tab that displays and edits program arguments, Bazel arguments, and working directory launch
 * configuration attributes.
 */
public class BazelArgumentsTab extends AbstractLaunchConfigurationTab {

    private static Logger LOG = LoggerFactory.getLogger(BazelArgumentsTab.class);

    protected static final String EMPTY_STRING = "";

    protected Label programArgumentsLabel;
    protected Text programArgumentsText;
    protected CommandArgumentsBlock bazelRunArgumentsBlock;
    protected WorkingDirectoryBlock workingDirectoryBlock;

    public BazelArgumentsTab() {
        bazelRunArgumentsBlock = createVMArgsBlock();
        workingDirectoryBlock = createWorkingDirBlock();
    }

    @Override
    public void activated(ILaunchConfigurationWorkingCopy workingCopy) {
        workingDirectoryBlock.initializeFrom(workingCopy);
    }

    /**
     * @see org.eclipse.debug.ui.ILaunchConfigurationTab#createControl(Composite)
     */
    @Override
    public void createControl(Composite parent) {
        var font = parent.getFont();
        var comp = new Composite(parent, SWT.NONE);
        var layout = new GridLayout(1, true);
        comp.setLayout(layout);
        comp.setFont(font);

        var gd = new GridData(GridData.FILL_BOTH);
        comp.setLayoutData(gd);
        setControl(comp);

        var group = new Group(comp, SWT.NONE);
        group.setFont(font);
        layout = new GridLayout();
        group.setLayout(layout);
        group.setLayoutData(new GridData(GridData.FILL_BOTH));

        group.setText("Program arguments:");

        programArgumentsText = new Text(group, SWT.MULTI | SWT.WRAP | SWT.BORDER | SWT.V_SCROLL);
        programArgumentsText.addTraverseListener(e -> {
            switch (e.detail) {
                case SWT.TRAVERSE_ESCAPE:
                case SWT.TRAVERSE_PAGE_NEXT:
                case SWT.TRAVERSE_PAGE_PREVIOUS:
                    e.doit = true;
                    break;
                case SWT.TRAVERSE_RETURN:
                case SWT.TRAVERSE_TAB_NEXT:
                case SWT.TRAVERSE_TAB_PREVIOUS:
                    if (((programArgumentsText.getStyle() & SWT.SINGLE) != 0)
                            || (!programArgumentsText.isEnabled() || ((e.stateMask & SWT.MODIFIER_MASK) != 0))) {
                        e.doit = true;
                    }
                    break;
            }
        });
        gd = new GridData(GridData.FILL_BOTH);
        gd.heightHint = 40;
        gd.widthHint = 100;
        programArgumentsText.setLayoutData(gd);
        programArgumentsText.setFont(font);
        programArgumentsText.addModifyListener(evt -> scheduleUpdateJob());

        var pgrmArgVariableButton = createPushButton(group, "Variables...", null);
        pgrmArgVariableButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
        pgrmArgVariableButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                var dialog = new StringVariableSelectionDialog(getShell());
                dialog.open();
                var variable = dialog.getVariableExpression();
                if (variable != null) {
                    programArgumentsText.insert(variable);
                }
            }
        });

        bazelRunArgumentsBlock.createControl(comp);
        workingDirectoryBlock.createControl(comp);
    }

    protected CommandArgumentsBlock createVMArgsBlock() {
        return new CommandArgumentsBlock();
    }

    protected WorkingDirectoryBlock createWorkingDirBlock() {
        return new BazelWorkingDirectoryBlock();
    }

    /**
     * Returns the string in the text widget, or <code>null</code> if empty.
     *
     * @param text
     *            the widget to get the value from
     * @return text or <code>null</code>
     */
    protected String getAttributeValueFrom(Text text) {
        var content = text.getText().trim();
        if (content.length() > 0) {
            return content;
        }
        return null;
    }

    @Override
    public String getErrorMessage() {
        var m = super.getErrorMessage();
        if (m == null) {
            return workingDirectoryBlock.getErrorMessage();
        }
        return m;
    }

    @Override
    public String getId() {
        return "com.salesforce.bazel.eclipse.ui.launchconfiguration.tabs.arguments";
    }

    @Override
    public Image getImage() {
        return BazelUIPlugin.getDefault().getImageRegistry().get(BazelUIPlugin.IMG_VIEW_ARGUMENTS_TAB);
    }

    @Override
    public String getMessage() {
        var m = super.getMessage();
        if (m == null) {
            return workingDirectoryBlock.getMessage();
        }
        return m;
    }

    @Override
    public String getName() {
        return "Arguments";
    }

    @Override
    protected void initializeAttributes() {
        super.initializeAttributes();
        getAttributesLabelsForPrototype().put(TARGET_ARGS, "Program arguments");
        getAttributesLabelsForPrototype().put(RUN_ARGS, "Bazel command arguments");
        getAttributesLabelsForPrototype().put(WORKING_DIRECTORY, "Working directory");
    }

    @Override
    public void initializeFrom(ILaunchConfiguration configuration) {
        try {
            programArgumentsText.setText(configuration.getAttribute(BazelLaunchConfigurationConstants.TARGET_ARGS, "")); //$NON-NLS-1$
            bazelRunArgumentsBlock.initializeFrom(configuration);
            workingDirectoryBlock.initializeFrom(configuration);
        } catch (CoreException e) {
            setErrorMessage("Error reading launch configuration: " + e.getStatus().getMessage());
            LOG.error("An error occured while reading launch configuration '{}'", configuration.getName(), e);
        }
    }

    @Override
    public boolean isValid(ILaunchConfiguration config) {
        return workingDirectoryBlock.isValid(config);
    }

    @Override
    public void performApply(ILaunchConfigurationWorkingCopy configuration) {
        configuration.setAttribute(
            BazelLaunchConfigurationConstants.TARGET_ARGS,
            getAttributeValueFrom(programArgumentsText));
        bazelRunArgumentsBlock.performApply(configuration);
        workingDirectoryBlock.performApply(configuration);
    }

    @Override
    public void setDefaults(ILaunchConfigurationWorkingCopy config) {
        config.setAttribute(BazelLaunchConfigurationConstants.TARGET_ARGS, (String) null);
        bazelRunArgumentsBlock.setDefaults(config);
        workingDirectoryBlock.setDefaults(config);
    }

    @Override
    public void setLaunchConfigurationDialog(ILaunchConfigurationDialog dialog) {
        super.setLaunchConfigurationDialog(dialog);
        workingDirectoryBlock.setLaunchConfigurationDialog(dialog);
        bazelRunArgumentsBlock.setLaunchConfigurationDialog(dialog);
    }
}
