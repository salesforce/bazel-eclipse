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
package com.salesforce.bazel.eclipse.core.launchconfiguration;

import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;

import com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants;

/**
 * Interface with shared constants
 */
public interface BazelLaunchConfigurationConstants extends BazelCoreSharedContstants {

    /**
     * Name of the Bazel project (value is a string)
     */
    String PROJECT_NAME = IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME;

    /**
     * Label of the target (value is a string)
     */
    String TARGET_LABEL = PLUGIN_ID + ".launchconfiguration.target";

    /**
     * Arguments to pass to the target (value is a string)
     */
    String TARGET_ARGS = IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS;

    /**
     * Additional arguments to pass to <code>bazel run</code> (value is a string)
     */
    String RUN_ARGS = PLUGIN_ID + ".launchconfiguration.target.args";

    /**
     * Indicates if the Java debugger should be attached after launching (value is a boolean)
     */
    String JAVA_DEBUG = PLUGIN_ID + ".launchconfiguration.java_debug";

    /**
     * Indicates if the '--debug' argument should be added when attaching the Java debugger (value is a boolean)
     */
    String ADD_DEBUG_TARGET_ARG = PLUGIN_ID + ".launchconfiguration.add_debug_target_arg";

    /**
     * The working directory (value is a string)
     */
    String WORKING_DIRECTORY = IJavaLaunchConfigurationConstants.ATTR_WORKING_DIRECTORY;
}
