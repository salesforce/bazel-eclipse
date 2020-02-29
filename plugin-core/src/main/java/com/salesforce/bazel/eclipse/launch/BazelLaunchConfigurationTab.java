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
package com.salesforce.bazel.eclipse.launch;

import java.util.Collection;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.launch.BazelLaunchConfigurationSupport.BazelLaunchConfigAttributes;
import com.salesforce.bazel.eclipse.launch.BazelLaunchConfigurationSupport.TypedBazelLabel;
import com.salesforce.bazel.eclipse.model.BazelLabel;
import com.salesforce.bazel.eclipse.model.TargetKind;
import com.salesforce.bazel.eclipse.ui.BazelSWTFactory;

/**
 * Launch Configuration Tab to select a project and Bazel target to launch.
 *
 * @author stoens
 * @since summer 2019
 */
public class BazelLaunchConfigurationTab extends AbstractLaunchConfigurationTab {

    private final BazelLaunchConfigurationSupport support = new BazelLaunchConfigurationSupport();

    private Text projectTextInput;
    private Text targetTextInput;
    
    private String loadedProjectName = "";
    private String loadedTargetKind;

    private Collection<TypedBazelLabel> labelsForSelectedProject = null;

    @Override
    public String getName() {
        return "Bazel Target";
    }

    @Override
    public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {}

    @Override
    public void initializeFrom(ILaunchConfiguration configuration) {
        try {
            loadedProjectName = configuration.getAttribute(BazelLaunchConfigAttributes.PROJECT.getAttributeName(), "");
            if (!loadedProjectName.isEmpty() && getSelectedProject() != null) {
                projectTextInput.setText(loadedProjectName);
                initializeLabelsForSelectedProject(getSelectedProject().getProject());
            }
            String targetName = configuration.getAttribute(BazelLaunchConfigAttributes.LABEL.getAttributeName(), (String)null);
            if (targetName != null) {
                targetTextInput.setText(targetName);
            }
            loadedTargetKind = configuration.getAttribute(BazelLaunchConfigAttributes.TARGET_KIND.getAttributeName(), (String)null);
        } catch (CoreException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public void performApply(ILaunchConfigurationWorkingCopy configuration) {
        String projectName = projectTextInput.getText();
        if (projectName == null || projectName.isEmpty()) {
            // there is a weird state issue sometimes where the project name in the text field gets lost
            if (!loadedProjectName.isEmpty()) {
                projectName = loadedProjectName;
                projectTextInput.setText(projectName);
            }
        }
        
        BazelLabel label = targetTextInput.getText().trim().isEmpty() ? null : new BazelLabel(targetTextInput.getText());
        TargetKind targetKind = label == null ? null : lookupLabelKind(label);
        if (targetKind == null && loadedTargetKind != null) {
            targetKind = TargetKind.valueOfIgnoresCase(loadedTargetKind);
        }
        
        support.populateBazelLaunchConfig(configuration, projectName, label, targetKind);
    }

    @Override
    public void createControl(Composite parent) {
        // the code here has been copied and adapted from
        // https://github.com/eclipse/eclipse.jdt.debug/blob/master/org.eclipse.jdt.debug.ui/ui/org/eclipse/jdt/debug/ui/launchConfigurations/JavaMainTab.java
        // https://github.com/eclipse/eclipse.jdt.debug/blob/master/org.eclipse.jdt.debug.ui/ui/org/eclipse/jdt/internal/debug/ui/launcher/SharedJavaMainTab.java
        Composite comp = BazelSWTFactory.createComposite(parent, parent.getFont(), 1, 1, GridData.FILL_BOTH);
        ((GridLayout) comp.getLayout()).verticalSpacing = 0;
        createProjectEditor(comp);
        createBazelTargetEditor(comp);
        setControl(comp);
    }

    private void createBazelTargetEditor(Composite parent) {
        Group group = BazelSWTFactory.createGroup(parent, "Bazel Target", 2, 1, GridData.FILL_HORIZONTAL);
        targetTextInput = BazelSWTFactory.createSingleText(group, 1);
        targetTextInput.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                updateLaunchConfigurationDialog();
            }
        });
        Button fSearchButton = createPushButton(group, "Search...", null);
        fSearchButton.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                handleBazelTargetButtonSelected();
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e) {}
        });
    }

    private void handleBazelTargetButtonSelected() {
        IJavaProject selectedProject = getSelectedProject();
        if (selectedProject == null) {
            throw new IllegalStateException("A project must be selected first");
        }
        BazelLabel target = chooseBazelTargetForProject(selectedProject.getProject());
        if (target != null) {
            targetTextInput.setText(target.toString());
        }
    }

    private BazelLabel chooseBazelTargetForProject(IProject project) {
        ElementListSelectionDialog dialog = new ElementListSelectionDialog(getShell(), BAZEL_LABEL_LABEL_PROVIDER);
        dialog.setTitle("Select Target");
        dialog.setMessage("Select Target");
        initializeLabelsForSelectedProject(project);
        dialog.setElements(labelsForSelectedProject.stream().map(typedBazelLabel -> typedBazelLabel.getBazelLabel())
                .toArray(BazelLabel[]::new));

        BazelLabel target = getSelectedTarget();
        if (target != null) {
            dialog.setInitialSelections(new Object[] { target.toString() });
        }
        if (dialog.open() == Window.OK) {
            return (BazelLabel) dialog.getFirstResult();
        }
        return null;
    }
    
    private synchronized void initializeLabelsForSelectedProject(IProject project) {
        if (labelsForSelectedProject == null) {
            labelsForSelectedProject = support.getLaunchableBazelTargetsForProject(project);
        }
    }
    
    private synchronized void clearLabelsForSelectedProject() {
        labelsForSelectedProject = null;
    }

    private void createProjectEditor(Composite parent) {
        Group group = BazelSWTFactory.createGroup(parent, "Project", 2, 1, GridData.FILL_HORIZONTAL);
        projectTextInput = BazelSWTFactory.createSingleText(group, 1);
        Button fProjButton = createPushButton(group, "Browse...", null);
        fProjButton.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                handleProjectButtonSelected();
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e) {}
        });
    }

    private void handleProjectButtonSelected() {
        IJavaProject project = chooseBazelProject();
        if (project != null) {
            clearLabelsForSelectedProject();
            String projectName = project.getElementName();
            projectTextInput.setText(projectName);
            targetTextInput.setText("");
        }
    }

    private IJavaProject chooseBazelProject() {
        ElementListSelectionDialog dialog = new ElementListSelectionDialog(getShell(), BAZEL_PROJECT_LABEL_PROVIDER);
        dialog.setTitle("Select Project");
        dialog.setMessage("Select Project");
        dialog.setElements(BazelPluginActivator.getJavaCoreHelper().getAllBazelJavaProjects());

        IJavaProject javaProject = getSelectedProject();
        if (javaProject != null) {
            dialog.setInitialSelections(new Object[] { javaProject });
        }
        if (dialog.open() == Window.OK) {
            return (IJavaProject) dialog.getFirstResult();
        }
        return null;
    }

    private IJavaProject getSelectedProject() {
        String projectName = projectTextInput.getText().trim();
        if (projectName.isEmpty()) {
            return null;
        }
        return getJavaModel().getJavaProject(projectName);
    }

    private BazelLabel getSelectedTarget() {
        String targetName = targetTextInput.getText().trim();
        if (targetName.length() < 1) {
            return null;
        }
        return new BazelLabel(targetName);
    }

    private TargetKind lookupLabelKind(BazelLabel label) {
        if (labelsForSelectedProject != null) {
            for (TypedBazelLabel typedBazelLabel : labelsForSelectedProject) {
                if (typedBazelLabel.getBazelLabel().equals(label)) {
                    return typedBazelLabel.getTargetKind();
                }
            }
        }
        return null;
    }

    private static IJavaModel getJavaModel() {
        IWorkspaceRoot wsRoot = BazelPluginActivator.getResourceHelper().getEclipseWorkspaceRoot();
        return BazelPluginActivator.getJavaCoreHelper().getJavaModelForWorkspace(wsRoot);
    }

    private static abstract class DefaultLabelProvider implements ILabelProvider {

        @Override
        public void addListener(ILabelProviderListener listener) {

        }

        @Override
        public void dispose() {

        }

        @Override
        public boolean isLabelProperty(Object element, String property) {
            return false;
        }

        @Override
        public void removeListener(ILabelProviderListener listener) {

        }

        @Override
        public Image getImage(Object element) {
            return null;
        }
    }

    private static ILabelProvider BAZEL_PROJECT_LABEL_PROVIDER = new DefaultLabelProvider() {
        @Override
        public String getText(Object element) {
            return ((IJavaProject) element).getProject().getName();
        }
    };

    private ILabelProvider BAZEL_LABEL_LABEL_PROVIDER = new DefaultLabelProvider() {
        @Override
        public String getText(Object element) {
            // lets also show the target type next to each label, for example:
            // //projects/libs/scone:app (java_binrary)
            // //projects/libs/scone/abstractions:mytest1 (java_test)
            BazelLabel label = ((BazelLabel)element);
            TargetKind targetKind = BazelLaunchConfigurationTab.this.lookupLabelKind(label);
            return label.getLabel() + " (" + targetKind.getKind() + ")";
        }
    };
}
