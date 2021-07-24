/**
 * Copyright (c) 2020, Salesforce.com, Inc. All rights reserved.
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
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.salesforce.bazel.eclipse.builder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;

import com.salesforce.bazel.sdk.model.BazelProblem;

/**
 * This class publishes JDT based code warnings to the Problems View.
 */
class JDTWarningPublisher implements IElementChangedListener {

    // maps a project name to a map of filePath -> BazelProblems in that file
    private final ConcurrentHashMap<String, Map<String, List<BazelProblem>>> projectNameToProblems =
            new ConcurrentHashMap<>();

    @Override
    public void elementChanged(ElementChangedEvent event) {
        if (event.getType() == ElementChangedEvent.POST_RECONCILE) {
            IJavaElementDelta delta = event.getDelta();
            CompilationUnit ast = delta.getCompilationUnitAST();
            if (ast != null) {
                List<BazelProblem> warnings = getWarnings(ast);
                IJavaElement element = delta.getElement();
                String filePath = getFilePath(element);
                if (filePath != null) {
                    IProject project = element.getJavaProject().getProject();
                    Map<String, List<BazelProblem>> filePathToWarnings = new HashMap<>();
                    filePathToWarnings.put(filePath, warnings);
                    projectNameToProblems.merge(project.toString(), filePathToWarnings, (currentValue, newValue) -> {
                        currentValue.put(filePath, warnings);
                        return currentValue;
                    });
                }
            }
        }
    }

    void publish(Collection<IProject> projects, IProgressMonitor monitor) {
        for (IProject project : projects) {
            Map<String, List<BazelProblem>> filePathToWarnings = projectNameToProblems.remove(project.toString());
            if (filePathToWarnings != null) {
                for (String filePath : filePathToWarnings.keySet()) {
                    String ownerId = this.getClass().getName() + "__" + filePath;
                    BazelProblemMarkerManager mgr = new BazelProblemMarkerManager(ownerId);
                    mgr.clearAndPublish(filePathToWarnings.get(filePath), project, monitor);
                }
            }
        }
    }

    private static List<BazelProblem> getWarnings(CompilationUnit ast) {
        IProblem[] problems = ast.getProblems();
        List<BazelProblem> warnings = new ArrayList<>();
        for (IProblem problem : problems) {
            if (!problem.isWarning()) {
                continue;
            }
            String path = new String(problem.getOriginatingFileName());
            path = removeLeadingProjectName(path);
            warnings.add(BazelProblem.createWarning(path, problem.getSourceLineNumber(), problem.getMessage()));

        }
        return warnings;
    }

    private static String getFilePath(IJavaElement el) {
        IPath path;
        try {
            path = el.getCorrespondingResource().getFullPath();
        } catch (JavaModelException ex) {
            return null;
        }
        return removeLeadingProjectName(path.toOSString());
    }

    private static String removeLeadingProjectName(String path) {
        if (path.startsWith(File.separator)) {
            path = path.substring(1);
        }
        int i = path.indexOf(File.separator);
        if (i != -1) {
            path = path.substring(i + 1);
        }
        return path;
    }
}
