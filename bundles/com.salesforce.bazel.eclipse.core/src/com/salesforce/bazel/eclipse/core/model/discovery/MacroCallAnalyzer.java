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
 *      Salesforce - initial implementation
 */
package com.salesforce.bazel.eclipse.core.model.discovery;

import org.eclipse.core.runtime.CoreException;

import com.salesforce.bazel.eclipse.core.model.buildfile.FunctionCall;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaProjectInfo;

/**
 * Analyzer for {@link FunctionCall} to extract information relevant for Eclipse to create a proper Java project.
 * <p>
 * Analyzers are short lived objects. It's ok to persist state although their lifecycle maybe just for one single
 * analysis invocation.
 * </p>
 */
public interface MacroCallAnalyzer {

    /**
     * Called by {@link BuildFileAndVisibilityDrivenProvisioningStrategy} to analyze the given {@link FunctionCall} and add
     * any relevant information to {@link JavaProjectInfo}.
     *
     * @param macroCall
     *            the {@link FunctionCall} to analyze (never <code>null</code>)
     * @param javaInfo
     *            the {@link JavaProjectInfo} to populate (never <code>null</code>)
     * @return <code>true</code> if the macro call was analyzed and should not processed further (<code>false</code>
     *         otherwise)
     * @throws CoreException
     *             if there is a problem analyzing the {@link FunctionCall} which should be reported
     */
    boolean analyze(FunctionCall macroCall, JavaProjectInfo javaInfo) throws CoreException;

}
