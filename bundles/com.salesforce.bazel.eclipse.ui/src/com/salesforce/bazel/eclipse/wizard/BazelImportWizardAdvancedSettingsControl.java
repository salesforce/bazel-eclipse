/*******************************************************************************
 * Copyright (c) 2008-2018 Sonatype, Inc. and others. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors: Sonatype, Inc. - initial API and implementation
 *******************************************************************************/

// adapted from M2Eclipse org.eclipse.m2e.core.ui.internal.wizards.MavenImportWizardPage

package com.salesforce.bazel.eclipse.wizard;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;

// TODO advanced settings, needed for Bazel?

public class BazelImportWizardAdvancedSettingsControl {
    BazelImportWizardPage page;

    public BazelImportWizardAdvancedSettingsControl(BazelImportWizardPage page) {
        this.page = page;
    }

    void addAdvancedSettingsControl(Composite composite) {
        var gridData = new GridData(SWT.FILL, SWT.TOP, false, false, 3, 1);
        gridData.verticalIndent = 7;

        //        resolverConfigurationComponent = new ResolverConfigurationComponent(composite, page.importConfiguration, true);
        //        resolverConfigurationComponent.setLayoutData(gridData);
        //        addFieldWithHistory("projectNameTemplate", resolverConfigurationComponent.template); //$NON-NLS-1$

        //        resolverConfigurationComponent.template.addModifyListener(new ModifyListener() {
        //           public void modifyText(ModifyEvent arg0) {
        //              Display.getDefault().asyncExec(new Runnable() {
        //                 public void run() {
        //                    validate();
        //              }
        //        });
        //  }
        // });

    }

}
