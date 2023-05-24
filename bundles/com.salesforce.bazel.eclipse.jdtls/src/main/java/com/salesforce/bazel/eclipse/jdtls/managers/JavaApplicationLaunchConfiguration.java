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
 *      Salesforce - copied from org.eclipse.jdt.ls.core.internal.managers.JavaApplicationLaunchConfiguration
 */
package com.salesforce.bazel.eclipse.jdtls.managers;

import java.util.Date;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.internal.core.LaunchConfiguration;
import org.eclipse.debug.internal.core.LaunchConfigurationInfo;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.ls.core.internal.managers.JavaLaunchConfigurationInfo;

/**
 * JavaApplicationLaunchConfiguration duplicated from
 * <code>org.eclipse.jdt.ls.core.internal.managers.JavaApplicationLaunchConfiguration</code>
 */
@SuppressWarnings("restriction")
public class JavaApplicationLaunchConfiguration extends LaunchConfiguration {

    private final IProject project;
    private final String scope;
    private final String classpathProvider;
    private final LaunchConfigurationInfo launchInfo;

    protected JavaApplicationLaunchConfiguration(IProject project, String scope, String classpathProvider)
            throws CoreException {
        super(String.valueOf(new Date().getTime()), null, false);
        this.project = project;
        this.scope = scope;
        this.classpathProvider = classpathProvider;
        this.launchInfo = new JavaLaunchConfigurationInfo(scope);
    }

    @Override
    public boolean getAttribute(String attributeName, boolean defaultValue) throws CoreException {
        if (IJavaLaunchConfigurationConstants.ATTR_EXCLUDE_TEST_CODE.equalsIgnoreCase(attributeName)) {
            return !"test".equals(this.scope);
        }

        return super.getAttribute(attributeName, defaultValue);
    }

    @Override
    public String getAttribute(String attributeName, String defaultValue) throws CoreException {
        if (IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME.equalsIgnoreCase(attributeName)) {
            return project.getName();
        }
        if (IJavaLaunchConfigurationConstants.ATTR_CLASSPATH_PROVIDER.equalsIgnoreCase(attributeName)) {
            return this.classpathProvider;
        }

        return super.getAttribute(attributeName, defaultValue);
    }

    @Override
    protected LaunchConfigurationInfo getInfo() throws CoreException {
        return this.launchInfo;
    }
}
