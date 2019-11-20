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

import java.util.Collection;
import java.util.stream.Collectors;

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
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorPart;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.model.AspectPackageInfo;
import com.salesforce.bazel.eclipse.model.BazelLabel;
import com.salesforce.bazel.eclipse.model.TargetKind;

/**
 * Supports the Run/Debug operations for Java classes with a main method.
 */
public class BazelTargetLaunchShortcut implements ILaunchShortcut {

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
                //methodName = cu.getElementName();
                fileName = cu.getParent().getElementName();
                packageName = cu.getParent().getParent().getParent().getElementName();
                projectName = cu.getJavaProject().getElementName();
            }
        }
        
        String fqClassName = packageName + "." + fileName;
        IWorkspaceRoot eclipseWorkspaceRoot = BazelPluginActivator.getResourceHelper().getEclipseWorkspaceRoot();
        IJavaModel eclipseJavaModel = BazelPluginActivator.getJavaCoreHelper().getJavaModelForWorkspace(eclipseWorkspaceRoot);
        IProject project = eclipseJavaModel.getJavaProject(projectName).getProject();

        Collection<AspectPackageInfo> apis = support.getRunnableAspectPackageInfosForProject(project);
        Collection<AspectPackageInfo> matchingInfos = apis.stream().filter(api -> fqClassName.equals(api.getMainClass())).collect(Collectors.toList());
        if (matchingInfos.isEmpty()) {
            // bazel allows a java binary rule to specify the main_class as the target name, so we should also look at the name of the targets
            // however bazel does not like the common "src/main/java" root:
            // error: "main_class was not provided and cannot be inferred: source path doesn't include a known root (java, javatests, src, testsrc)"
            throw new IllegalStateException("Unable to find a java_binary target that has a main_class of " + fqClassName);
        } else if (matchingInfos.size() > 1) {
            // surface correctly
            throw new IllegalStateException("Found multiple java_binary targets that have a main_class of " + fqClassName + " - create a launch configuration manually");            
        }

        ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType type = manager.getLaunchConfigurationType(BazelLaunchConfigurationDelegate.ID);
        try {
            ILaunchConfigurationWorkingCopy config = type.newInstance(null, fileName);
            AspectPackageInfo api = matchingInfos.iterator().next();
            BazelLabel label = new BazelLabel(api.getLabel());
            TargetKind kind = TargetKind.valueOfIgnoresCase(api.getKind());
            support.populateBazelLaunchConfig(config, projectName, label, kind);
            if (mode.equalsIgnoreCase(ILaunchManager.DEBUG_MODE)) {
                config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_ALLOW_TERMINATE, true);
            }
            DebugUITools.launch(config.doSave(), mode);

        } catch (CoreException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public void launch(IEditorPart editor, String mode) {
        //TODO: Add UI to the editor to run from the editor
    }

}
