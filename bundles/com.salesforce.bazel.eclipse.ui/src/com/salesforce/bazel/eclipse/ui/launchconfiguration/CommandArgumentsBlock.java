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
 *     Salesforce - copied and adapted from JDT CommandArgumentsBlock
 */
package com.salesforce.bazel.eclipse.ui.launchconfiguration;

import static com.salesforce.bazel.eclipse.core.launchconfiguration.BazelLaunchConfigurationConstants.RUN_ARGS;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.debug.ui.StringVariableSelectionDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Editor for command level arguments of a Bazel launch configuration.
 */
public class CommandArgumentsBlock extends AbstractLaunchConfigurationTab {

    private static Logger LOG = LoggerFactory.getLogger(CommandArgumentsBlock.class);

    protected Text commandArgumentsText;
    private Button variablesButton;

    /**
     * @see org.eclipse.debug.ui.ILaunchConfigurationTab#createControl(Composite)
     */
    @Override
    public void createControl(Composite parent) {
        var font = parent.getFont();

        var group = new Group(parent, SWT.NONE);
        setControl(group);
        var topLayout = new GridLayout();
        group.setLayout(topLayout);
        var gd = new GridData(GridData.FILL_BOTH);
        group.setLayoutData(gd);
        group.setFont(font);
        group.setText("Bazel arguments:");

        commandArgumentsText = new Text(group, SWT.MULTI | SWT.WRAP | SWT.BORDER | SWT.V_SCROLL);
        commandArgumentsText.addTraverseListener(e -> {
            switch (e.detail) {
                case SWT.TRAVERSE_ESCAPE:
                case SWT.TRAVERSE_PAGE_NEXT:
                case SWT.TRAVERSE_PAGE_PREVIOUS:
                    e.doit = true;
                    break;
                case SWT.TRAVERSE_RETURN:
                case SWT.TRAVERSE_TAB_NEXT:
                case SWT.TRAVERSE_TAB_PREVIOUS:
                    if (((commandArgumentsText.getStyle() & SWT.SINGLE) != 0)
                            || (!commandArgumentsText.isEnabled() || ((e.stateMask & SWT.MODIFIER_MASK) != 0))) {
                        e.doit = true;
                    }
                    break;
            }
        });
        gd = new GridData(GridData.FILL_BOTH);
        gd.heightHint = 40;
        gd.widthHint = 100;
        commandArgumentsText.setLayoutData(gd);
        commandArgumentsText.setFont(font);
        commandArgumentsText.addModifyListener(evt -> scheduleUpdateJob());

        variablesButton = createPushButton(group, "Variables...", null);
        variablesButton.setFont(font);
        variablesButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
        variablesButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                var dialog = new StringVariableSelectionDialog(getShell());
                dialog.open();
                var variable = dialog.getVariableExpression();
                if (variable != null) {
                    commandArgumentsText.insert(variable);
                }
            }
        });
    }

    /**
     * Returns the string in the text widget, or <code>null</code> if empty.
     *
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
    public String getName() {
        return "Bazel Run Arguments";
    }

    @Override
    public void initializeFrom(ILaunchConfiguration configuration) {
        try {
            commandArgumentsText.setText(configuration.getAttribute(RUN_ARGS, ""));
        } catch (CoreException e) {
            setErrorMessage("Error reading launch configuration: " + e.getStatus().getMessage());
            LOG.error("An error occured while reading launch configuration '{}'", configuration.getName(), e);
        }
    }

    @Override
    public void performApply(ILaunchConfigurationWorkingCopy configuration) {
        configuration.setAttribute(RUN_ARGS, getAttributeValueFrom(commandArgumentsText));
    }

    @Override
    public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
        configuration.setAttribute(RUN_ARGS, (String) null);
    }

    public void setEnabled(boolean enabled) {
        commandArgumentsText.setEnabled(enabled);
        variablesButton.setEnabled(enabled);
    }
}