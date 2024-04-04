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
package com.salesforce.bazel.eclipse.core.model.discovery.analyzers;

import org.eclipse.core.runtime.CoreException;

import com.salesforce.bazel.eclipse.core.model.buildfile.GlobInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.EntrySettings;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaProjectInfo;

/**
 * Default <code>java_test</code> (same as <code>java_library</code>)
 */
public class JavaTestAnalyzer extends JavaLibraryAnalyzer {

    @Override
    protected void addResourceFileOrLabel(JavaProjectInfo javaInfo, String fileOrLabel, String resourceStripPrefix)
            throws CoreException {
        javaInfo.addTestResource(fileOrLabel, resourceStripPrefix);
    }

    @Override
    protected void addResourceGlob(JavaProjectInfo javaInfo, GlobInfo glob) throws CoreException {
        javaInfo.addResource(glob);
    }

    @Override
    protected void addSrcFileOrLabel(JavaProjectInfo javaInfo, String fileOrLabel, EntrySettings entrySettings)
            throws CoreException {
        javaInfo.addTestSrc(fileOrLabel, entrySettings);
    }

    @Override
    protected void addSrcGlob(JavaProjectInfo javaInfo, GlobInfo glob, EntrySettings entrySettings)
            throws CoreException {
        javaInfo.addTestSrc(glob, entrySettings);
    }
}
