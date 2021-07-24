/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.eclipse.launch;

import java.util.List;

import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;

import com.salesforce.bazel.eclipse.launch.BazelLaunchConfigurationSupport.BazelLaunchConfigAttributes;
import com.salesforce.bazel.sdk.command.ArgumentSplitter;

/**
 * Launch Configuration Tab to specify arguments for Bazel runnable targets.
 * <p>
 * Inspired by org.eclipse.jdt.debug.ui.launchConfigurations.JavaArgumentsTab.
 */
public class BazelLaunchConfigurationArgumentsTab extends AbstractLaunchConfigurationTab {

    private final ArgumentSplitter argumentSplitter = new ArgumentSplitter();

    private Text programArgumentsText;

    @Override
    public String getName() {
        return "Arguments";
    }

    @Override
    public void createControl(Composite parent) {
        Font font = parent.getFont();
        Composite comp = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, true);
        comp.setLayout(layout);
        comp.setFont(font);

        GridData gd = new GridData(GridData.FILL_BOTH);
        comp.setLayoutData(gd);
        setControl(comp);

        Group group = new Group(comp, SWT.NONE);
        group.setFont(font);
        layout = new GridLayout();
        group.setLayout(layout);
        group.setLayoutData(new GridData(GridData.FILL_BOTH));

        String controlName = ("Program arguments:");
        group.setText(controlName);

        programArgumentsText = new Text(group, SWT.MULTI | SWT.WRAP | SWT.BORDER | SWT.V_SCROLL);
        programArgumentsText.addTraverseListener(new TraverseListener() {
            @Override
            public void keyTraversed(TraverseEvent e) {
                switch (e.detail) {
                case SWT.TRAVERSE_ESCAPE:
                case SWT.TRAVERSE_PAGE_NEXT:
                case SWT.TRAVERSE_PAGE_PREVIOUS:
                    e.doit = true;
                    break;
                case SWT.TRAVERSE_RETURN:
                case SWT.TRAVERSE_TAB_NEXT:
                case SWT.TRAVERSE_TAB_PREVIOUS:
                    if ((programArgumentsText.getStyle() & SWT.SINGLE) != 0) {
                        e.doit = true;
                    } else {
                        if (!programArgumentsText.isEnabled() || ((e.stateMask & SWT.MODIFIER_MASK) != 0)) {
                            e.doit = true;
                        }
                    }
                    break;
                }
            }
        });
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.heightHint = 100;
        gd.widthHint = 100;
        programArgumentsText.setLayoutData(gd);
        programArgumentsText.setFont(font);
        programArgumentsText.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent evt) {
                scheduleUpdateJob();
            }
        });
    }

    @Override
    public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {}

    @Override
    public void initializeFrom(ILaunchConfiguration configuration) {
        List<String> args = BazelLaunchConfigAttributes.USER_BAZEL_ARGS.getListValue(configuration);
        programArgumentsText.setText(String.join(" ", args));
    }

    @Override
    public void performApply(ILaunchConfigurationWorkingCopy configuration) {
        List<String> args = argumentSplitter.split(programArgumentsText.getText());
        configuration.setAttribute(BazelLaunchConfigAttributes.USER_BAZEL_ARGS.getAttributeName(), args);
    }
}
