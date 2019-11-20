package com.salesforce.bazel.eclipse.mock;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.mockito.Mockito;

/**
 * Produces mock IFile instances. The IFile interface is enormous, so we only mock what we need and let
 * Mockito do the rest.
 */
public class MockIFileFactory {
    
    public static IFile createMockIFile(boolean exists) {
        IFile mockIFile = Mockito.mock(IFile.class);
        
        Mockito.when(mockIFile.exists()).thenReturn(exists);
        
        return mockIFile;
    }
    
    public static IFile createMockIFile(boolean exists, IProject project, IPath location) {
        IFile mockIFile = Mockito.mock(IFile.class);
        
        Mockito.when(mockIFile.exists()).thenReturn(exists);
        Mockito.when(mockIFile.getProject()).thenReturn(project);
        Mockito.when(mockIFile.getLocation()).thenReturn(location);
        
        return mockIFile;
    }
}
