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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.launching.AbstractVMInstall;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstallType;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.VMStandin;
import org.eclipse.jdt.launching.environments.IExecutionEnvironment;
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
    final List<String> supportedSources;
    final List<String> supportedTargets;
    final List<String> supportedReleases;

    private final LinkedHashMap<String, String> environmentIdByComplianceVersion = new LinkedHashMap<>();

    public JvmConfigurator() {
        Set<String> supportedExecutionEnvironmentTypes = Set.of("JRE", "J2SE", "JavaSE");

        List<String> sources = new ArrayList<>();

        List<String> targets = new ArrayList<>();
        //Special case
        targets.add("jsr14"); //$NON-NLS-1$
        environmentIdByComplianceVersion.put("jsr14", "J2SE-1.5"); //$NON-NLS-1$ //$NON-NLS-2$

        List<String> releases = new ArrayList<>(List.of("6", "7", "8"));

        for (var ee : JavaRuntime.getExecutionEnvironmentsManager().getExecutionEnvironments()) {
            var eeId = ee.getId();
            if (supportedExecutionEnvironmentTypes.stream().filter(type -> eeId.startsWith(type)).findAny().isEmpty()) {
                continue;
            }
            var compliance = ee.getComplianceOptions().get(JavaCore.COMPILER_COMPLIANCE);
            if (compliance != null) {
                sources.add(compliance);
                targets.add(compliance);
                if (JavaCore.ENABLED.equals(ee.getComplianceOptions().get(JavaCore.COMPILER_RELEASE))) {
                    releases.add(compliance);
                }
                environmentIdByComplianceVersion.put(compliance, eeId);
            }
        }

        supportedSources = Collections.unmodifiableList(sources);
        supportedTargets = Collections.unmodifiableList(targets);
        supportedReleases = Collections.unmodifiableList(releases);
    }

    public void applyJavaProjectOptions(IJavaProject javaProject, String source, String target, String release) {
        var javaProjectOptions = javaProject.getOptions(false);

        // initialize null values if possible
        source = source == null ? target : source;
        target = target == null ? source : target;
        release = release == null ? target : release;

        if (source != null) {
            javaProject.setOption(JavaCore.COMPILER_SOURCE, source);
            javaProject.setOption(JavaCore.COMPILER_COMPLIANCE, source);
        }
        if (target != null) {
            javaProject.setOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, target);
        }
        javaProject.setOption(JavaCore.COMPILER_RELEASE, (release == null) ? JavaCore.DISABLED : JavaCore.ENABLED);

        // initialize more options if not set yet (but allow users to customize)
        if (javaProjectOptions.get(JavaCore.COMPILER_CODEGEN_METHOD_PARAMETERS_ATTR) == null) {
            javaProject.setOption(JavaCore.COMPILER_CODEGEN_METHOD_PARAMETERS_ATTR, JavaCore.GENERATE);
        }
        if (javaProjectOptions.get(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES) == null) {
            javaProject.setOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, JavaCore.ENABLED);
            if (javaProjectOptions.get(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES) == null) {
                javaProject.setOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, JavaCore.IGNORE);
            }
        }
    }

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
            javaProject.setOptions(options);
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
        var name = format(
            "Bazel Java Toolchain (%s, %s)",
            resolvedJavaHomePath.getFileName().toString(),
            bazelWorkspace.getName());
        var vm = findVmForNameOrPath(resolvedJavaHomePath, name);

        // delete if location does not match
        if ((vm != null) && !vm.getInstallLocation().toPath().equals(resolvedJavaHomePath)) {
            LOG.debug(
                "Disposing existing VMInstall at '{}' ({}, {})",
                vm.getInstallLocation(),
                vm.getName(),
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

    private IExecutionEnvironment getExecutionEnvironment(String environmentId) {
        if (environmentId == null) {
            return null;
        }

        var manager = JavaRuntime.getExecutionEnvironmentsManager();
        var environment = manager.getEnvironment(environmentId);
        if ((environment != null) && (environment.getCompatibleVMs().length > 0)) {
            return environment;
        }
        LOG.error(
            "Failed to find a compatible VM for environment id '{}', falling back to workspace default",
            environmentId);
        return null;
    }

    public String getExecutionEnvironmentId(IJavaProject javaProject) {
        var options = javaProject.getOptions(false);
        return environmentIdByComplianceVersion.get(options.get(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM));
    }

    public IClasspathEntry getJreClasspathContainerForExecutionEnvironment(String environmentId,
            IClasspathAttribute[] extraAttributes) {
        var executionEnvironment = getExecutionEnvironment(environmentId);
        IPath containerPath;
        if (executionEnvironment == null) {
            containerPath = JavaRuntime.getDefaultJREContainerEntry().getPath();
        } else {
            containerPath = JavaRuntime.newJREContainerPath(executionEnvironment);
        }
        return JavaCore.newContainerEntry(
            containerPath,
            null /* no access rules */,
            extraAttributes,
            false /* not exported */);
    }

}
