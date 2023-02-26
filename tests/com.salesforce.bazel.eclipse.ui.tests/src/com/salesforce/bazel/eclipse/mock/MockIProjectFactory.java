/**
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.eclipse.mock;

import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.JavaCore;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.core.BazelCoreSharedContstants;
import com.salesforce.bazel.eclipse.core.model.BazelProject;
import com.salesforce.bazel.sdk.path.FSPathHelper;

/**
 * Convenience factory for create Mockito mock iProjects.
 */
public class MockIProjectFactory {

    public IProject buildIProject(MockIProjectDescriptor bom) {
        IProject mockProject = Mockito.mock(IProject.class);
        Mockito.when(mockProject.getProject()).thenReturn(mockProject);

        Mockito.when(mockProject.getName()).thenReturn(bom.name);
        IPath relativePathInWorkspace = new Path(bom.relativePathToEclipseProjectDirectoryFromWorkspaceRoot + bom.name);
        Mockito.when(mockProject.getFullPath()).thenReturn(relativePathInWorkspace);
        IPath absolutePath = new Path(bom.absolutePathToEclipseProjectDirectory);
        Mockito.when(mockProject.getLocation()).thenReturn(absolutePath);
        Mockito.when(mockProject.getWorkspace())
        .thenReturn(ComponentContext.getInstance().getResourceHelper().getEclipseWorkspace());
        try {
            Mockito.when(mockProject.getReferencedProjects()).thenReturn(new IProject[] {});
        } catch (Exception anyE) {}

        // lifecycle
        Mockito.when(mockProject.exists()).thenReturn(bom.exists);
        Mockito.when(mockProject.isOpen()).thenReturn(bom.isOpen);
        Mockito.when(mockProject.getType()).thenReturn(IResource.PROJECT);

        // description
        IProjectDescription description = new MockIProjectDescription();
        try {
            Mockito.when(mockProject.getDescription()).thenReturn(description);
        } catch (Exception anyE) {}

        // natures
        // note that there are two ways to ask for the natures, one is project.getNature() and the other is via the project description
        // which makes this convoluted
        if (bom.hasBazelNature) {
            bom.customNatures.put(BazelCoreSharedContstants.BAZEL_NATURE_ID, new BazelProject());
        }
        if (bom.hasJavaNature) {
            bom.customNatures.put(JavaCore.NATURE_ID, Mockito.mock(IProjectNature.class));
        }
        for (String natureId : bom.customNatures.keySet()) {
            try {
                Mockito.when(mockProject.getNature(ArgumentMatchers.eq(natureId)))
                .thenReturn(bom.customNatures.get(natureId));
            } catch (Exception anyE) {}
        }
        description.setNatureIds(bom.customNatures.keySet().toArray(new String[] {}));

        // files and folders
        MockIFolder projectFolder = new MockIFolder(mockProject);
        GetProjectFolderAnswer getProjectFolderAnswer = new GetProjectFolderAnswer(mockProject, projectFolder);
        Mockito.when(mockProject.getFolder(ArgumentMatchers.anyString())).then(getProjectFolderAnswer);
        IFile mockFile = Mockito.mock(IFile.class);
        Mockito.when(mockFile.exists()).thenReturn(false);
        Mockito.when(mockProject.getFile(ArgumentMatchers.anyString())).thenReturn(mockFile);

        return mockProject;
    }

    public IProject buildGenericIProject(String projectName, String absolutePathToEclipseWorkspace,
            String absolutePathToBazelPackage) {
        MockIProjectDescriptor bom = new MockIProjectDescriptor(projectName);

        // normally the apple-api Eclipse project will be located as a top level directory in the Eclipse workspace directory
        bom.absolutePathToEclipseProjectDirectory =
                FSPathHelper.osSeps(absolutePathToEclipseWorkspace + "/" + projectName); // $SLASH_OK
        bom.hasBazelNature = false;
        bom.hasJavaNature = false;

        return buildIProject(bom);
    }

    public IProject buildJavaBazelIProject(String projectName, String absolutePathToEclipseWorkspace,
            String absolutePathToBazelPackage) throws Exception {
        MockIProjectDescriptor bom = new MockIProjectDescriptor(projectName);

        // normally the apple-api Eclipse project will be located as a top level directory in the Eclipse workspace directory
        bom.absolutePathToEclipseProjectDirectory =
                FSPathHelper.osSeps(absolutePathToEclipseWorkspace + "/" + projectName); // $SLASH_OK

        bom.absolutePathToBazelPackageDirectory = absolutePathToBazelPackage;

        bom.hasBazelNature = true;
        bom.hasJavaNature = true;

        return buildIProject(bom);
    }

    /**
     * Fill out your BOM here. I decided not to do a fluent API since I think the BOMs will be highly standardized.
     *
     */
    public static class MockIProjectDescriptor {
        public String name;

        // relative path from the Eclipse workspace root directory to the eclipse project directory (typically "")
        public String relativePathToEclipseProjectDirectoryFromWorkspaceRoot = "";

        // absolute file system path to the Eclipse project directory (/home/joe/dev/MyEclipseWorkspace/fooProject)
        public String absolutePathToEclipseProjectDirectory = "";

        // absolute file system path to the Bazel package directory (/home/joe/dev/MyBazelWorkspace/projects/lib/apple-api)
        public String absolutePathToBazelPackageDirectory = "";

        // lifecycle
        public boolean exists = false;
        public boolean isOpen = false;

        // natures, choose from the standard natures, and add any additional ones to the custom map
        public boolean hasBazelNature = true;
        public boolean hasJavaNature = true;
        public Map<String, IProjectNature> customNatures = new TreeMap<>();

        /**
         * Creates a standard Bazel project with Java nature.
         *
         * @param projectName
         */
        public MockIProjectDescriptor(String projectName) {
            name = projectName;
        }
    }

    /**
     * This solves a complicated Mock case where the getFolder(String name) method is called on the IProject. We need to
     * dynamically create the child MockIFolder with the name that was passed.
     */
    private static class GetProjectFolderAnswer implements Answer<IFolder> {
        private final IProject project;
        private final MockIFolder projectFolder;

        public GetProjectFolderAnswer(IProject project, MockIFolder projectFolder) {
            this.project = project;
            this.projectFolder = projectFolder;
        }

        @Override
        public IFolder answer(InvocationOnMock invocation) throws Throwable {
            String queriedName = invocation.getArgument(0);
            IFolder folder = new MockIFolder(project, projectFolder, queriedName);

            return folder;
        }

    }
}
