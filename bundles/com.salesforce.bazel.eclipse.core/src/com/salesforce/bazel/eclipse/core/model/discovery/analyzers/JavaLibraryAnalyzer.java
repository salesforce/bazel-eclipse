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
import com.salesforce.bazel.eclipse.core.model.discovery.JavaProjectInfo;
import com.salesforce.bazel.eclipse.core.model.discovery.MacroCallAnalyzer;

/**
 * Default <code>java_library</code>
 */
public class JavaLibraryAnalyzer implements MacroCallAnalyzer {

    protected void addFileOrLabel(JavaProjectInfo javaInfo, String fileOrLabel) throws CoreException {
        javaInfo.addSrc(fileOrLabel);
    }

    protected void addGlob(JavaProjectInfo javaInfo, GlobInfo glob) throws CoreException {
        javaInfo.addSrc(glob);
    }

    @Override
    public boolean analyze(MacroCall macroCall, JavaProjectInfo javaInfo) throws CoreException {
        // process globs first
        var globs = macroCall.getGlobInfoFromArgument("srcs");
        if (globs == null) {
            // ignore call without 'srcs'
            return false;
        }
        for (GlobInfo glob : globs) {
            addGlob(javaInfo, glob);
        }

        // process labels or files
        var labelsOrFiles = macroCall.getStringListArgument("srcs");
        if (labelsOrFiles != null) {
            // this can be null if glob is the only srcs
            for (String labelOrFile : labelsOrFiles) {
                addFileOrLabel(javaInfo, labelOrFile);
            }
        }

        return true;
    }
}
