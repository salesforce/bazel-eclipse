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

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWizard;
import org.eclipse.ui.dialogs.WizardNewProjectCreationPage;

/**
 * A wizard to create a Bazel import project.
 * 
 * REFERENCE ONLY! This is the old mechanism from the original Bazel plugin. It used the New... extension point instead
 * of the Import... extension point.
 * 
 */
public class BazelWizard extends Wizard implements IWorkbenchWizard {

    protected WorkspaceWizardPage page2;
    protected WizardNewProjectCreationPage page1;

    @Override
    public void addPages() {
        page1 = new WizardNewProjectCreationPage("Creating a new Bazel import project");
        page2 = new WorkspaceWizardPage();
        addPage(page1);
        addPage(page2);
    }

    @Override
    public String getWindowTitle() {
        return "Import Bazel Workspace...";
    }

    @Override
    public boolean performFinish() {
//        BazelEclipseProjectFactory.createEclipseProjectForBazelPackage(page1.getProjectName(), page1.getLocationURI(),
//            page2.getWorkspaceRoot(), "", page2.getDirectories(), page2.getTargets(), page2.getJavaLanguageVersion());
        return true;
    }

    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {}

}
