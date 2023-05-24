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
 *      Salesforce - Partially adapted and heavily inspired from Eclipse JDT, M2E and PDE
 */
package com.salesforce.bazel.eclipse.core.model.discovery;

import static java.lang.String.format;

import java.util.UUID;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.launching.AbstractVMInstall;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstallType;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.VMStandin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.core.model.BazelWorkspace;

/**
 * A tool for configuration of JVM installations in Eclipse.
 */
@SuppressWarnings("restriction")
public class JvmConfigurator {

    private static Logger LOG = LoggerFactory.getLogger(JvmConfigurator.class);

    static final String STANDARD_VM_TYPE = "org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType"; //$NON-NLS-1$
    static final String MAC_OSX_VM_TYPE = "org.eclipse.jdt.internal.launching.macosx.MacOSXType"; //$NON-NLS-1$

    public void configureJVMSettings(IJavaProject javaProject, IVMInstall vmInstall) {
        if (javaProject == null) {
            return;
        }
        var version = "";
        if (vmInstall instanceof AbstractVMInstall jvm) {
            version = jvm.getJavaVersion();
            var jdkLevel = CompilerOptions.versionToJdkLevel(jvm.getJavaVersion());
            var compliance = CompilerOptions.versionFromJdkLevel(jdkLevel);
            var options = javaProject.getOptions(false);
            JavaCore.setComplianceOptions(compliance, options);
        }
        if (JavaCore.isSupportedJavaVersion(version)
                && (JavaCore.compareJavaVersions(version, JavaCore.latestSupportedJavaVersion()) >= 0)) {
            //Enable Java preview features for the latest JDK release by default and stfu about it
            javaProject.setOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
            javaProject.setOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, JavaCore.IGNORE);
        } else {
            javaProject.setOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.DISABLED);
        }
    }

    public IVMInstall configureVMInstall(java.nio.file.Path resolvedJavaHomePath, BazelWorkspace bazelWorkspace)
            throws CoreException {
        var name = format("Bazel Java Toolchain (%s, %s)", resolvedJavaHomePath.getFileName().toString(),
            bazelWorkspace.getName());
        var vm = findVmForNameOrPath(resolvedJavaHomePath, name);

        // delete if location does not match
        if ((vm != null) && !vm.getInstallLocation().toPath().equals(resolvedJavaHomePath)) {
            LOG.debug("Disposing existing VMInstall at '{}' ({}, {})", vm.getInstallLocation(), vm.getName(),
                vm.getId());
            vm.getVMInstallType().disposeVMInstall(vm.getId());
            vm = null;
        }

        // create new if missing
        if (vm == null) {
            var installType = JavaRuntime.getVMInstallType(STANDARD_VM_TYPE);
            if ((installType == null) || (installType.getVMInstalls().length == 0)) {
                // https://github.com/eclipse/eclipse.jdt.ls/issues/1646
                var macInstallType = JavaRuntime.getVMInstallType(MAC_OSX_VM_TYPE);
                if (macInstallType != null) {
                    installType = macInstallType;
                }
            }
            var vmId = generateUnusedVmId(installType);
            var vmStandin = new VMStandin(installType, vmId);
            vmStandin.setName(name);
            vmStandin.setInstallLocation(resolvedJavaHomePath.toFile());
            vm = vmStandin.convertToRealVM();
            JavaRuntime.saveVMConfiguration();
        }

        LOG.debug("Configured VMInstall at '{}' ({}, {})", resolvedJavaHomePath, vm.getName(), vm.getId());
        return vm;
    }

    private IVMInstall findVmForNameOrPath(java.nio.file.Path javaHome, String name) {
        var file = javaHome.toFile();
        var types = JavaRuntime.getVMInstallTypes();
        for (IVMInstallType type : types) {
            var installs = type.getVMInstalls();
            for (IVMInstall install : installs) {
                if ((name != null) && name.equals(install.getName())) {
                    return install;
                }
                if ((file != null) && file.equals(install.getInstallLocation())) {
                    return install;
                }
            }
        }
        return null;
    }

    public String generateUnusedVmId(IVMInstallType installType) {
        var unique = "bazel-workspace-jvm_" + UUID.randomUUID().toString();
        while (installType.findVMInstall(unique) != null) {
            unique = "bazel-workspace-jvm_" + UUID.randomUUID().toString();
        }
        return unique;
    }

}
