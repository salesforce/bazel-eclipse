/*-
 * Copyright (c) 2023 Salesforce and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *      Salesforce - adapted from M2E, JDT or other Eclipse project
 */
package com.salesforce.bazel.eclipse.ui.launchconfiguration;

import org.eclipse.debug.ui.AbstractLaunchConfigurationTabGroup;
import org.eclipse.debug.ui.CommonTab;
import org.eclipse.debug.ui.EnvironmentTab;
import org.eclipse.debug.ui.ILaunchConfigurationDialog;
import org.eclipse.debug.ui.ILaunchConfigurationTab;
import org.eclipse.debug.ui.sourcelookup.SourceLookupTab;

public class BazelRunLaunchConfigurationTabGroup extends AbstractLaunchConfigurationTabGroup {

    @Override
    public void createTabs(ILaunchConfigurationDialog dialog, String mode) {
        ILaunchConfigurationTab[] tabs = { new BazelTargetTab(), new BazelArgumentsTab(), new SourceLookupTab(),
                new EnvironmentTab(), new CommonTab() };
        setTabs(tabs);
    }
}
