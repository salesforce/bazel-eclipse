package com.salesforce.bazel.eclipse.mock;

import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.JavaCore;
import org.mockito.Mockito;

import com.salesforce.bazel.eclipse.BazelNature;
import com.salesforce.bazel.eclipse.BazelPluginActivator;

/**
 * Convenience factory for create Mockito mock iProjects.
 * 
 * @author plaird
 */
public class MockIProjectFactory {
        
    public IProject buildIProject(MockIProjectDescriptor bom) {
        IProject mockProject = Mockito.mock(IProject.class);
        Mockito.when(mockProject.getProject()).thenReturn(mockProject);
        
        Mockito.when(mockProject.getName()).thenReturn(bom.name);
        IPath relativePathInWorkspace = new Path(bom.relativePathToEclipseProjectDirectoryFromWorkspaceRoot+bom.name);
        Mockito.when(mockProject.getFullPath()).thenReturn(relativePathInWorkspace);
        IPath absolutePath = new Path(bom.absolutePathToEclipseProjectDirectory);
        Mockito.when(mockProject.getLocation()).thenReturn(absolutePath);
        Mockito.when(mockProject.getWorkspace()).thenReturn(BazelPluginActivator.getResourceHelper().getEclipseWorkspace());
        
        // lifecycle
        Mockito.when(mockProject.exists()).thenReturn(bom.exists);
        Mockito.when(mockProject.isOpen()).thenReturn(bom.isOpen);
        
        // description
        IProjectDescription description = new MockIProjectDescription();
        try {
            Mockito.when(mockProject.getDescription()).thenReturn(description);
        } catch (Exception anyE) {}

        // natures
        // note that there are two ways to ask for the natures, one is project.getNature() and the other is via the project description
        // which makes this convoluted
        if (bom.hasBazelNature) {
            bom.customNatures.put(BazelNature.BAZEL_NATURE_ID, new BazelNature());
        }
        if (bom.hasJavaNature) {
            bom.customNatures.put(JavaCore.NATURE_ID, Mockito.mock(IProjectNature.class));
        }
        for (String natureId : bom.customNatures.keySet()) {
            try {
                Mockito.when(mockProject.getNature(Mockito.eq(natureId))).thenReturn(bom.customNatures.get(natureId));
            } catch (Exception anyE) {}
        }
        description.setNatureIds(bom.customNatures.keySet().toArray(new String[] {}));
        
        // files and folders
        MockIFolder projectFolder = new MockIFolder(mockProject);
        Mockito.when(mockProject.getFolder(Mockito.anyString())).thenReturn(projectFolder);
        IFile mockFile = Mockito.mock(IFile.class);
        Mockito.when(mockFile.exists()).thenReturn(false);
        Mockito.when(mockProject.getFile(Mockito.anyString())).thenReturn(mockFile);
        
        return mockProject;
    }
    
    public IProject buildGenericIProject(String projectName, String absolutePathToEclipseWorkspace, 
            String absolutePathToBazelPackage) {
        MockIProjectDescriptor bom = new MockIProjectDescriptor(projectName);
        
        // normally the apple-api Eclipse project will be located as a top level directory in the Eclipse workspace directory
        bom.absolutePathToEclipseProjectDirectory = absolutePathToEclipseWorkspace+"/"+projectName;
        bom.hasBazelNature = false;
        bom.hasJavaNature = false;
        
        return buildIProject(bom);
    }

    public IProject buildJavaBazelIProject(String projectName, String absolutePathToEclipseWorkspace, 
            String absolutePathToBazelPackage) throws Exception {
        MockIProjectDescriptor bom = new MockIProjectDescriptor(projectName);
        
        // normally the apple-api Eclipse project will be located as a top level directory in the Eclipse workspace directory
        bom.absolutePathToEclipseProjectDirectory = absolutePathToEclipseWorkspace+"/"+projectName;
        
        bom.absolutePathToBazelPackageDirectory = absolutePathToBazelPackage;
        
        bom.hasBazelNature = true;
        bom.hasJavaNature = true;
        
        return buildIProject(bom);
    }
    
    /**
     * Fill out your BOM here. I decided not to do a fluent API since I think the 
     * BOMs will be highly standardized.
     * @author plaird
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
         * @param projectName
         */
        public MockIProjectDescriptor(String projectName) {
            this.name = projectName;
        }
    }
}
