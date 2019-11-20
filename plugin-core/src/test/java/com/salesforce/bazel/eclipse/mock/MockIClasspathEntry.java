package com.salesforce.bazel.eclipse.mock;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;

public class MockIClasspathEntry implements IClasspathEntry {
    private static final String UOE_MSG = "MockIClasspathEntry is pay as you go, you have hit a method that is not implemented."; 

    private final int entryKind;
    private final IPath path;
    
    // TODO need to test behaviors related to inclusion/exclusion patterns, right now we assume they aren't set, which is the default behavior
    private IPath[] exclusionPatterns = new IPath[] {};
    private IPath[] inclusionPatterns = new IPath[] {};
    
    /*
     * Kinds
     * CPE_LIBRARY = 1;
     * CPE_PROJECT = 2;
     * CPE_SOURCE = 3;
     * CPE_VARIABLE = 4;
     * CPE_CONTAINER = 5;
     */
    
    public MockIClasspathEntry(int ekind, IPath path) {
        this.entryKind = ekind;
        this.path = path;
    }
    
    @Override
    public int getEntryKind() {
        return this.entryKind;
    }

    @Override
    public IPath[] getExclusionPatterns() {
        return exclusionPatterns;
    }

    @Override
    public IPath[] getInclusionPatterns() {
        return inclusionPatterns;
    }

    @Override
    public IPath getPath() {
        return path;
    }



    // UNIMPLEMENTED METHODS
    // Please move implemented methods, in alphabetical order, above this line if you implement a method.
    
    @Override
    public int getContentKind() {
        throw new UnsupportedOperationException(UOE_MSG);
    }
    
    @Override
    public boolean combineAccessRules() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IAccessRule[] getAccessRules() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IClasspathAttribute[] getExtraAttributes() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPath getOutputLocation() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPath getSourceAttachmentPath() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPath getSourceAttachmentRootPath() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IClasspathEntry getReferencingEntry() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isExported() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IClasspathEntry getResolvedEntry() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

}
