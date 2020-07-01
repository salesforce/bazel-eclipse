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

// adapted from M2Eclipse org.eclipse.m2e.core.ui.internal.wizards.MavenImportWizardPage::ProjectLabelProvider

package com.salesforce.bazel.eclipse.wizard;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.model.WorkbenchLabelProvider;

import com.salesforce.bazel.sdk.model.BazelPackageInfo;

public class BazelImportWizardLabelProvider extends LabelProvider
        implements IColorProvider, DelegatingStyledCellLabelProvider.IStyledLabelProvider {

    private ILabelProvider labelProvider = WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider();
    BazelImportWizardPage page;

    public BazelImportWizardLabelProvider(BazelImportWizardPage page) {
        this.page = page;
    }

    @Override
    public String getText(Object element) {
        return labelProvider.getText(element);
    }

    @Override
    public Image getImage(Object element) {
        return labelProvider.getImage(element);
    }

    public Color getForeground(Object element) {
        if (element instanceof IResource && isKnownModule((IResource) element)) {
            return Display.getDefault().getSystemColor(SWT.COLOR_GRAY);
        }
        return null;
    }

    public Color getBackground(Object element) {
        return null;
    }

    private boolean isKnownModule(IResource element) {
        // TODO implement gray out of existing modules if reimport a Bazel workspace
        // element.getLocation()
        return false;
    }

    @Override
    public StyledString getStyledText(Object element) {
        if (element instanceof BazelPackageInfo) {
            BazelPackageInfo info = (BazelPackageInfo) element;
            StyledString ss = new StyledString();

            // this will produce lines in the project tree dialog like:
            //    WORKSPACE //...
            // or
            //    /projects/libs/apple  //projects/libs/apple
            // with the second string in gray

            String label = info.getBazelPackageFSRelativePathForUI();
            ss.append(label + "  ");

            String bazelPath = info.getBazelPackageName();
            ss.append(bazelPath, StyledString.DECORATIONS_STYLER);
            return ss;
        }
        return null;
    }
}
