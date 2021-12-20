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
 */
package com.salesforce.bazel.eclipse.launch;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.sdk.aspect.AspectTargetInfo;
import com.salesforce.bazel.sdk.aspect.jvm.JVMAspectTargetInfo;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelTargetKind;

/**
 * Supports the Run or Debug operations for Java classes with a main method.
 */
public class BazelTargetLaunchShortcut implements ILaunchShortcut {
    private static final LogHelper LOG = LogHelper.log(BazelTargetLaunchShortcut.class);

    private final BazelLaunchConfigurationSupport support = new BazelLaunchConfigurationSupport();

    @Override
    public void launch(ISelection selection, String mode) {
        IStructuredSelection structured = (IStructuredSelection) selection;
        String fileName = null;
        String projectName = null;
        String packageName = null;

        if (structured.getFirstElement() instanceof ICompilationUnit) {
            ICompilationUnit cu = (ICompilationUnit) structured.getFirstElement();
            fileName = cu.getElementName().replace(".java", "");
            packageName = cu.getParent().getElementName();
            projectName = cu.getParent().getJavaProject().getElementName();
        } else if (structured.getFirstElement() instanceof IJavaElement) {
            if (structured.getFirstElement() instanceof IType) {
                IJavaElement cu = (IJavaElement) structured.getFirstElement();
                fileName = cu.getElementName();
                packageName = cu.getParent().getParent().getElementName();
                projectName = cu.getJavaProject().getElementName();
            } else if (structured.getFirstElement() instanceof IMethod) {
                IJavaElement cu = (IJavaElement) structured.getFirstElement();
                fileName = cu.getParent().getElementName();
                packageName = cu.getParent().getParent().getParent().getElementName();
                projectName = cu.getJavaProject().getElementName();
            }
        }

        String fqClassName = packageName + "." + fileName;
        LOG.info("Bazel target launcher for [{}]", fqClassName);

        IWorkspaceRoot eclipseWorkspaceRoot = ComponentContext.getInstance().getResourceHelper().getEclipseWorkspaceRoot();
        IJavaModel eclipseJavaModel =
                ComponentContext.getInstance().getJavaCoreHelper().getJavaModelForWorkspace(eclipseWorkspaceRoot);
        IProject project = eclipseJavaModel.getJavaProject(projectName).getProject();

        Collection<AspectTargetInfo> apis = support.getLaunchableAspectTargetInfosForProject(project);
        Collection<AspectTargetInfo> mainClassInfos = new ArrayList<>();
        for (AspectTargetInfo info : apis) {
            if (info instanceof JVMAspectTargetInfo) {
                JVMAspectTargetInfo jvmInfo = (JVMAspectTargetInfo) info;
                if (fqClassName.equals(jvmInfo.getMainClass())) {
                    mainClassInfos.add(info);
                }
            }
        }

        // validation
        if (mainClassInfos.isEmpty()) {
            // we need a java_binary rule to specify the main_class as our target class...
            String message =
                    "Unable to find a 'java_binary' target in the BUILD file that has a 'main_class' of " + fqClassName;
            Display.getDefault().asyncExec(new Runnable() {
                @Override
                public void run() {
                    Display.getDefault().syncExec(new Runnable() {
                        @Override
                        public void run() {
                            MessageDialog.openError(Display.getDefault().getActiveShell(), "No Launchable Target",
                                message);
                        }
                    });
                }
            });
            throw new IllegalStateException(message);
        } else if (mainClassInfos.size() > 1) {
            // if there are multiple java_binary rules found, we don't know which one to invoke...
            String message = "Found multiple 'java_binary' targets in the BUILD file that have a 'main_class' of "
                    + fqClassName + " - create a launch configuration manually in the Run Configurations menu.";
            Display.getDefault().asyncExec(new Runnable() {
                @Override
                public void run() {
                    Display.getDefault().syncExec(new Runnable() {
                        @Override
                        public void run() {
                            MessageDialog.openError(Display.getDefault().getActiveShell(),
                                "Multiple Launchable Targets", message);
                        }
                    });
                }
            });
            throw new IllegalStateException(message);
        }

        ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType type = manager.getLaunchConfigurationType(BazelLaunchConfigurationDelegate.ID);
        try {
            ILaunchConfigurationWorkingCopy config = type.newInstance(null, fileName);
            support.setLaunchConfigDefaults(config);
            AspectTargetInfo api = mainClassInfos.iterator().next();
            BazelLabel label = api.getLabel();
            BazelTargetKind kind = BazelTargetKind.valueOfIgnoresCase(api.getKind());
            support.populateBazelLaunchConfig(config, projectName, label, kind);
            DebugUITools.launch(config.doSave(), mode);

        } catch (CoreException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public void launch(IEditorPart editor, String mode) {
        //TODO: Add UI to the editor to run from the editor
        LOG.info("BEF received an editor launch event, but does not currently have an implementation for it.");
    }

}
