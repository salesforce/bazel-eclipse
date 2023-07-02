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
import com.salesforce.bazel.eclipse.core.model.buildfile.MacroCall;
import com.salesforce.bazel.eclipse.core.model.discovery.MacroCallAnalyzer;
import com.salesforce.bazel.eclipse.core.model.discovery.projects.JavaProjectInfo;

/**
 * Default <code>java_library</code>
 */
public class JavaLibraryAnalyzer implements MacroCallAnalyzer {

    protected void addResourceFileOrLabel(JavaProjectInfo javaInfo, String fileOrLabel, String resourceStripPrefix)
            throws CoreException {
        javaInfo.addResource(fileOrLabel, resourceStripPrefix);
    }

    protected void addResourceGlob(JavaProjectInfo javaInfo, GlobInfo glob) throws CoreException {
        javaInfo.addResource(glob);
    }

    protected void addSrcFileOrLabel(JavaProjectInfo javaInfo, String fileOrLabel) throws CoreException {
        javaInfo.addSrc(fileOrLabel);
    }

    protected void addSrcGlob(JavaProjectInfo javaInfo, GlobInfo glob) throws CoreException {
        javaInfo.addSrc(glob);
    }

    @Override
    public boolean analyze(MacroCall macroCall, JavaProjectInfo javaInfo) throws CoreException {
        // note: for absolute correctness all 'srcs' should be processed in their order; we deliberately say no to this effort

        var addedSomething = false;

        // process globs first
        var globs = macroCall.getGlobInfoFromArgument("srcs");
        if (globs != null) {
            addedSomething = true;
            for (GlobInfo glob : globs) {
                addSrcGlob(javaInfo, glob);
            }
        }

        // process labels or files
        var labelsOrFiles = macroCall.getStringListArgument("srcs");
        if (labelsOrFiles != null) {
            addedSomething = true;
            // this can be null if glob is the only srcs
            for (String labelOrFile : labelsOrFiles) {
                addSrcFileOrLabel(javaInfo, labelOrFile);
            }
        }

        // process resources globs first
        globs = macroCall.getGlobInfoFromArgument("resources");
        if (globs != null) {
            addedSomething = true;
            for (GlobInfo glob : globs) {
                addResourceGlob(javaInfo, glob);
            }
        }

        // process resources labels or files
        labelsOrFiles = macroCall.getStringListArgument("resources");
        if (labelsOrFiles != null) {
            addedSomething = true;
            // this can be null if glob is the only srcs
            var resourceStripPrefix = macroCall.getStringArgument("resource_strip_prefix");
            for (String labelOrFile : labelsOrFiles) {
                addResourceFileOrLabel(javaInfo, labelOrFile, resourceStripPrefix);
            }
        }

        return addedSomething; // only relevant if something was added
    }
}
