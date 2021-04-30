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
