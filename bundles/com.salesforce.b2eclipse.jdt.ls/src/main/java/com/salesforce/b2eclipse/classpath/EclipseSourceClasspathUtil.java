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
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.salesforce.b2eclipse.classpath;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import com.salesforce.b2eclipse.BazelJdtPlugin;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.eclipse.runtime.api.JavaCoreHelper;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.project.structure.ProjectStructure;
import com.salesforce.bazel.sdk.util.SimplePerfRecorder;

/**
 * Utility class for constructing the Source file classpath based on the structure of the source code on the file
 * system.
 */
public final class EclipseSourceClasspathUtil {
    private static final LogHelper LOG = LogHelper.log(EclipseSourceClasspathUtil.class);

    public static final String STANDARD_VM_CONTAINER_PREFIX = "org.eclipse.jdt.launching.JRE_CONTAINER/"
            + "org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-";

    /**
     * Function for constructing the Source file classpath based on the structure of the source code on the file system,
     * and setting that classpath into the passed IJavaProject. This method will also ensure that the source directories
     * are linked into the Eclipse project.
     *
     * @param bazelWorkspacePath
     *            the IPath object that points to the root directory of the Bazel workspace
     * @param bazelPackageFSPath
     *            the relative path from the Bazel WORKSPACE root, to the Bazel Package being processed
     * @param structure
     *            object containing the relative paths for the source file directories
     * @param javaProject
     *            the IJavaProject instance that is associated with the Bazel package
     * @param javaLanguageLevel
     *            the workspace Java language level
     */
    public static void createClasspath(IPath bazelWorkspacePath, String bazelPackageFSPath,
            ProjectStructure structure, IJavaProject javaProject, int javaLanguageLevel)
                    throws CoreException {
        List<IClasspathEntry> classpathEntries = new LinkedList<>();
        JavaCoreHelper javaCoreHelper = BazelJdtPlugin.getJavaCoreHelper();

        long startTimeMS = System.currentTimeMillis();
        buildSourceClasspathEntries(bazelWorkspacePath, javaProject, bazelPackageFSPath,
            structure.mainSourceDirFSPaths, false,
            classpathEntries);
        buildSourceClasspathEntries(bazelWorkspacePath, javaProject, bazelPackageFSPath,
            structure.testSourceDirFSPaths, true,
            classpathEntries);
        SimplePerfRecorder.addTime("import_createprojects_sourcecp_1", startTimeMS);

        startTimeMS = System.currentTimeMillis();
        IClasspathEntry bazelClasspathContainerEntry = javaCoreHelper
                .newContainerEntry(new Path(BazelClasspathContainer.CONTAINER_NAME));
        classpathEntries.add(bazelClasspathContainerEntry);

        // add in a JDK to the classpath
        String jdk;
        if (javaLanguageLevel < 9) {
            jdk = STANDARD_VM_CONTAINER_PREFIX + "1." + javaLanguageLevel;
        } else {
            jdk = STANDARD_VM_CONTAINER_PREFIX + javaLanguageLevel;
        }
        IClasspathEntry jdkEntry = javaCoreHelper.newContainerEntry(new Path(jdk));
        classpathEntries.add(jdkEntry);
        SimplePerfRecorder.addTime("import_createprojects_sourcecp_2", startTimeMS);

        // set the classpath into the passed javaProject
        startTimeMS = System.currentTimeMillis();
        IClasspathEntry[] newClasspath = classpathEntries.toArray(new IClasspathEntry[classpathEntries.size()]);
        javaProject.setRawClasspath(newClasspath, null);
        SimplePerfRecorder.addTime("import_createprojects_sourcecp_3", startTimeMS);
    }

    private static void buildSourceClasspathEntries(IPath bazelWorkspacePath, IJavaProject javaProject,
            String bazelPackageFSPath, List<String> pkgSrcPaths, boolean isTestSource,
            List<IClasspathEntry> classpathEntries) {
        ResourceHelper resourceHelper = BazelJdtPlugin.getResourceHelper();
        JavaCoreHelper javaCoreHelper = BazelJdtPlugin.getJavaCoreHelper();

        IPath outputDir = null; // null is a legal value, it means use the default
        if (isTestSource) {
            outputDir = new Path(javaProject.getPath().toOSString() + File.separatorChar + "testbin");
        }

        for (String path : pkgSrcPaths) {
            IPath realSourceDir = Path.fromOSString(bazelWorkspacePath + File.separator + path);

            // create the intermediate IFolders to descend down to the source directory. This function will stop
            // before creating the IFolder for the last directory in the path.
            IFolder projectSourceFolder =
                    createIFoldersForRelativePath(javaProject.getProject(), bazelPackageFSPath, path);

            // Now that the intermediate IFolders are created, we can create an Eclipse link to the actual source directory
            try {
                resourceHelper.createFolderLink(projectSourceFolder, realSourceDir, IResource.REPLACE, null);
            } catch (Exception anyE) {
                // this can happen in degenerate cases such as source directory is the root of the project
                LOG.error("could not link {} directory into source classpath for package {}", anyE, bazelPackageFSPath,
                    path);
                continue;
            }

            // add the created path to the classpath as a Source cp entry
            IPath sourceDir = projectSourceFolder.getFullPath();
            IClasspathEntry sourceClasspathEntry = javaCoreHelper.newSourceEntry(sourceDir, outputDir, isTestSource);
            classpathEntries.add(sourceClasspathEntry);
        }

    }

    /**
     * Creates the path of Eclipse IFolders to show the hierarchy descending down to a source directory. Stops before
     * creating the last directory in the path, and returns the final IFolder created.
     */
    private static IFolder createIFoldersForRelativePath(IProject project, String bazelPackageFSPath,
            String sourceDirFSPath) {

        if (!sourceDirFSPath.startsWith(bazelPackageFSPath)) {
            // TODO reconsider blowing up import for this, perhaps alert the user and then soldier on here
            throw new IllegalStateException("src code path " + sourceDirFSPath
                + " expected to be under bazel package path " + bazelPackageFSPath);
        }

        IFolder currentFolder = null;
        if (sourceDirFSPath.equals(bazelPackageFSPath)) {
            currentFolder = project.getFolder(sourceDirFSPath);
        } else {
            String sourceDirectoryPath =
                    FSPathHelper.osSeps(sourceDirFSPath.substring(bazelPackageFSPath.length() + 1)); // +1 for '/'
            String[] pathComponents = sourceDirectoryPath.split(FSPathHelper.osSepRegex());
            for (int i = 0; i < pathComponents.length; i++) {
                String pathComponent = pathComponents[i];
                if (currentFolder == null) {
                    currentFolder = project.getFolder(pathComponent);
                } else {
                    currentFolder = currentFolder.getFolder(pathComponent);
                }
                if (i < (pathComponents.length - 1)) {
                    // don't create the last folder - that happens as part of "linking" it after this method is called
                    // see the calling codepoint for more details
                    createIFolder(currentFolder);
                }
            }
        }
        return currentFolder;
    }

    private static void createIFolder(IFolder folder) {
        if (folder.exists()) {
            return;
        }
        try {
            boolean force = false; // "a flag controlling how to deal with resources that are not in sync with the local file system"
            boolean local = true; // "a flag controlling whether or not the folder will be local after the creation"
            folder.create(force, local, null);
        } catch (CoreException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private EclipseSourceClasspathUtil() {
        throw new IllegalArgumentException("Not meant to be instantiated");
    }

}
