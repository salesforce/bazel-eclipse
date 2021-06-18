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
 */
package com.salesforce.bazel.eclipse.projectimport.flow;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.classpath.BazelClasspathContainer;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.util.BazelPathHelper;
import com.salesforce.bazel.sdk.util.SimplePerfRecorder;

/**
 * Configures the classpath container for each project.
 */
public class SetupClasspathContainersFlow implements ImportFlow {
    private static final LogHelper LOG = LogHelper.log(SetupClasspathContainersFlow.class);

    private static final String STANDARD_VM_CONTAINER_PREFIX = "org.eclipse.jdt.launching.JRE_CONTAINER/"
            + "org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-";

    @Override
    public String getProgressText() {
        return "Configuring classpath containers";
    }

    @Override
    public void assertContextState(ImportContext ctx) {
        Objects.requireNonNull(ctx.getBazelWorkspaceRootDirectory());
        Objects.requireNonNull(ctx.getImportedProjects());
    }

    @Override
    public int getTotalWorkTicks(ImportContext ctx) {
        return ctx.getImportedProjects().size();
    }

    @Override
    public void run(ImportContext ctx, SubMonitor progressSubMonitor) throws CoreException {
        Path bazelWorkspaceRootDirectory = new Path(ctx.getBazelWorkspaceRootDirectory().getAbsolutePath());
        List<IProject> importedProjects = ctx.getImportedProjects();
        for (IProject project : importedProjects) {
            BazelPackageLocation packageLocation = ctx.getPackageLocationForProject(project);
            EclipseProjectStructureInspector inspector = new EclipseProjectStructureInspector(packageLocation);
            String packageFSPath = packageLocation.getBazelPackageFSRelativePath();
            IJavaProject javaProject = BazelPluginActivator.getJavaCoreHelper().getJavaProjectForProject(project);
            createClasspath(bazelWorkspaceRootDirectory, packageFSPath, inspector.getPackageSourceCodeFSPaths(),
                javaProject, ctx.getJavaLanguageLevel());
            progressSubMonitor.worked(1);
        }
    }

    // bazelPackageFSPath: the relative path from the Bazel WORKSPACE root, to the  Bazel Package being processed
    // packageSourceCodeFSRelativePaths: the relative paths from the WORKSPACE root to the Java Source directories
    // where the Java Package structure starts
    private static void createClasspath(IPath bazelWorkspacePath, String bazelPackageFSPath,
            List<String> packageSourceCodeFSRelativePaths, IJavaProject eclipseProject, int javaLanguageLevel)
                    throws CoreException {
        List<IClasspathEntry> classpathEntries = new LinkedList<>();
        ResourceHelper resourceHelper = BazelPluginActivator.getResourceHelper();

        long startTimeMS = System.currentTimeMillis();
        for (String path : packageSourceCodeFSRelativePaths) {
            IPath realSourceDir = Path.fromOSString(bazelWorkspacePath + File.separator + path);
            IFolder projectSourceFolder =
                    createFoldersForRelativePackagePath(eclipseProject.getProject(), bazelPackageFSPath, path);
            try {
                resourceHelper.createFolderLink(projectSourceFolder, realSourceDir, IResource.NONE, null);
            } catch (Exception anyE) {
                // this can happen in degenerate cases such as source directory is the root of the project
                LOG.error("error creating classpath", anyE);
                continue;
            }

            IPath outputDir = null; // null is a legal value, it means use the default
            boolean isTestSource = false;
            if (path.endsWith(BazelPathHelper.osSeps("src/test/java"))) { // NON_CONFORMING PROJECT SUPPORT, $SLASH_OK
                isTestSource = true;
                outputDir = new Path(eclipseProject.getPath().toOSString() + File.separatorChar + "testbin");
            }

            IPath sourceDir = projectSourceFolder.getFullPath();
            IClasspathEntry sourceClasspathEntry =
                    BazelPluginActivator.getJavaCoreHelper().newSourceEntry(sourceDir, outputDir, isTestSource);
            classpathEntries.add(sourceClasspathEntry);
        }
        SimplePerfRecorder.addTime("import_createprojects_cp_1", startTimeMS);

        startTimeMS = System.currentTimeMillis();
        IClasspathEntry bazelClasspathContainerEntry = BazelPluginActivator.getJavaCoreHelper()
                .newContainerEntry(new Path(BazelClasspathContainer.CONTAINER_NAME));
        classpathEntries.add(bazelClasspathContainerEntry);
        SimplePerfRecorder.addTime("import_createprojects_cp_2", startTimeMS);

        // add in a JDK to the classpath
        String jdk;
        if (javaLanguageLevel < 9) {
            jdk = STANDARD_VM_CONTAINER_PREFIX + "1." + javaLanguageLevel;
        } else {
            jdk = STANDARD_VM_CONTAINER_PREFIX + javaLanguageLevel;
        }

        classpathEntries.add(BazelPluginActivator.getJavaCoreHelper().newContainerEntry(new Path(jdk)));
        IClasspathEntry[] newClasspath = classpathEntries.toArray(new IClasspathEntry[classpathEntries.size()]);
        startTimeMS = System.currentTimeMillis();
        eclipseProject.setRawClasspath(newClasspath, null);
        SimplePerfRecorder.addTime("import_createprojects_cp_3", startTimeMS);
    }

    private static IFolder createFoldersForRelativePackagePath(IProject project, String bazelPackageFSPath,
            String packageSourceCodeFSRelativePath) {

        if (!packageSourceCodeFSRelativePath.startsWith(bazelPackageFSPath)) {
            throw new IllegalStateException("src code path " + packageSourceCodeFSRelativePath
                + " expected to be under bazel package path " + bazelPackageFSPath);
        }

        IFolder currentFolder = null;
        if (packageSourceCodeFSRelativePath.equals(bazelPackageFSPath)) {
            currentFolder = project.getFolder(packageSourceCodeFSRelativePath);
        } else {
            String sourceDirectoryPath =
                    BazelPathHelper.osSeps(packageSourceCodeFSRelativePath.substring(bazelPackageFSPath.length() + 1)); // +1 for '/'
            String[] pathComponents = sourceDirectoryPath.split(BazelPathHelper.osSepRegex());
            for (int i = 0; i < pathComponents.length; i++) {
                String pathComponent = pathComponents[i];
                if (currentFolder == null) {
                    currentFolder = project.getFolder(pathComponent);
                } else {
                    currentFolder = currentFolder.getFolder(pathComponent);
                }
                if (i < (pathComponents.length - 1)) {
                    // don't create the last folder - that happens as part of "linking" it
                    create(currentFolder);
                }
            }
        }
        return currentFolder;
    }

    private static void create(IFolder folder) {
        if (folder.exists()) {
            return;
        }
        try {
            folder.create(false, true, null);
        } catch (CoreException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
