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
 *      Salesforce - inspired from M2E
 */
package com.salesforce.bazel.eclipse.core.launching;

import org.eclipse.jdt.launching.StandardClasspathProvider;

/**
 * Adaption of {@link StandardClasspathProvider} to Bazel.
 */
public class BazelRuntimeClasspathProvider extends StandardClasspathProvider {

    public static final String BAZEL_SOURCEPATH_PROVIDER =
            "com.salesforce.bazel.eclipse.launchconfig.sourcepathProvider";
    public static final String BAZEL_CLASSPATH_PROVIDER = "com.salesforce.bazel.eclipse.launchconfig.classpathProvider";

}
