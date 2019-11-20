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
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IModuleDescription;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IRegion;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.eval.IEvaluationContext;

import com.salesforce.bazel.eclipse.classpath.BazelClasspathContainer;

public class MockIJavaProject implements IJavaProject {
    private static final String UOE_MSG = "MockIJavaProject is pay as you go, you have hit a method that is not implemented."; 

    private IProject iproject;
    private String name;
    private IClasspathEntry[] entries;
    private IClasspathEntry[] resolvedEntries;
    
    public MockIJavaProject(IProject iproject) {
        this.iproject = iproject;
        this.name = iproject.getName();
    }
    
    
    // IMPLEMENTED METHODS
    
    @Override
    public String getElementName() {
        return this.name;
    }

    @Override
    public IJavaProject getJavaProject() {
        return this;
    }
    
    @Override
    public IPath getPath() {
        return iproject.getLocation();
    }

    @Override
    public IProject getProject() {
        return iproject;
    }

    /*
     * CLASSPATH README 
     * - the "raw" classpath is the one set at the project level, and includes the Bazel Classpath Container as a single entry, the JRE as a single entry, etc
     * - the "resolved" classpath is the one where each raw entry contributes back the actual elements; for the Bazel classpath container it will contain
     *     an entry PER dependency for the project (e.g. slf4j-api, log4j, other Bazel workspace packages). Each resolved entry is known as a 'simple' entry.
     * - referenced entries are the ones written into the .classpath file, which seem to be the raw classpath at least for our use cases 
     */
    
    @Override
    public IClasspathEntry[] getRawClasspath() throws JavaModelException {
        if (entries == null) {
            System.err.println("Classpath entries are null for project "+iproject.getName());
        }
        return entries;
    }
    
    @Override
    public IClasspathEntry[] getResolvedClasspath(boolean ignoreUnresolvedEntry) throws JavaModelException {
        if (this.resolvedEntries == null) {
            
            // TODO this should be done during setRawClasspath but that is causing timing issues. But right now the mock framework is
            // not consistent with real Eclipse so we should investigate
            
            try {
              BazelClasspathContainer classpathContainer = new BazelClasspathContainer(iproject, this);
              this.resolvedEntries = classpathContainer.getClasspathEntries();
            } catch (Exception anyE) {
              throw new IllegalStateException(anyE);
            }
        }
        return this.resolvedEntries;
    }

    @Override
    public IClasspathEntry[] getReferencedClasspathEntries() throws JavaModelException {
        return entries;
    }

    @Override
    public void setRawClasspath(IClasspathEntry[] entries, IProgressMonitor monitor) throws JavaModelException {
        if (entries == null) {
            throw new IllegalArgumentException("Bazel Eclipse Feature is setting the classpath as null for project "+iproject.getName());
        }
        this.entries = entries;
        
        // TODO this causes timing issues during import so disabled for now (just getting lucky in real eclipse?)
        // in real Eclipse, setting the raw classpath will trigger a callstack that ultimately calls JavaProject.resolveClasspath() which
        // will trigger all the raw classpath entries to compute their actual items. For Bazel Classpath Container, this means it processes
        // the Bazel aspect that describes the dependencies.
//        try {
//            BazelClasspathContainer classpathContainer = new BazelClasspathContainer(iproject, this);
//            this.resolvedEntries = classpathContainer.getClasspathEntries();
//        } catch (Exception anyE) {
//            throw new IllegalStateException(anyE);
//        }
    }



    
    // UNIMPLEMENTED METHODS
    // Please move implemented methods, in alphabetical order, above this line if you implement a method.

    @Override
    public IJavaElement[] getChildren() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean hasChildren() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean exists() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IJavaElement getAncestor(int ancestorType) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String getAttachedJavadoc(IProgressMonitor monitor) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IResource getCorrespondingResource() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public int getElementType() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String getHandleIdentifier() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IJavaModel getJavaModel() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IOpenable getOpenable() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IJavaElement getParent() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IJavaElement getPrimaryElement() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IResource getResource() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public ISchedulingRule getSchedulingRule() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IResource getUnderlyingResource() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isReadOnly() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isStructureKnown() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void close() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String findRecommendedLineSeparator() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IBuffer getBuffer() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean hasUnsavedChanges() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isConsistent() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isOpen() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void makeConsistent(IProgressMonitor progress) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void open(IProgressMonitor progress) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void save(IProgressMonitor progress, boolean force) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IClasspathEntry decodeClasspathEntry(String encodedEntry) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String encodeClasspathEntry(IClasspathEntry classpathEntry) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IJavaElement findElement(IPath path) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IJavaElement findElement(IPath path, WorkingCopyOwner owner) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IJavaElement findElement(String bindingKey, WorkingCopyOwner owner) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPackageFragment findPackageFragment(IPath path) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPackageFragmentRoot findPackageFragmentRoot(IPath path) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPackageFragmentRoot[] findPackageFragmentRoots(IClasspathEntry entry) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPackageFragmentRoot[] findUnfilteredPackageFragmentRoots(IClasspathEntry entry) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IType findType(String fullyQualifiedName) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IType findType(String fullyQualifiedName, IProgressMonitor progressMonitor) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IType findType(String fullyQualifiedName, WorkingCopyOwner owner) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IType findType(String fullyQualifiedName, WorkingCopyOwner owner, IProgressMonitor progressMonitor)
            throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IType findType(String packageName, String typeQualifiedName) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IType findType(String packageName, String typeQualifiedName, IProgressMonitor progressMonitor)
            throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IType findType(String packageName, String typeQualifiedName, WorkingCopyOwner owner)
            throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IType findType(String packageName, String typeQualifiedName, WorkingCopyOwner owner,
            IProgressMonitor progressMonitor) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IModuleDescription findModule(String moduleName, WorkingCopyOwner owner) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPackageFragmentRoot[] getAllPackageFragmentRoots() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public Object[] getNonJavaResources() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String getOption(String optionName, boolean inheritJavaCoreOptions) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public Map<String, String> getOptions(boolean inheritJavaCoreOptions) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPath getOutputLocation() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPackageFragmentRoot getPackageFragmentRoot(String externalLibraryPath) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPackageFragmentRoot getPackageFragmentRoot(IResource resource) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPackageFragmentRoot[] getPackageFragmentRoots() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPackageFragmentRoot[] getPackageFragmentRoots(IClasspathEntry entry) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPackageFragment[] getPackageFragments() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IModuleDescription getModuleDescription() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String[] getRequiredProjectNames() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean hasBuildState() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean hasClasspathCycle(IClasspathEntry[] entries) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isOnClasspath(IJavaElement element) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isOnClasspath(IResource resource) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IEvaluationContext newEvaluationContext() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public ITypeHierarchy newTypeHierarchy(IRegion region, IProgressMonitor monitor) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public ITypeHierarchy newTypeHierarchy(IRegion region, WorkingCopyOwner owner, IProgressMonitor monitor)
            throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public ITypeHierarchy newTypeHierarchy(IType type, IRegion region, IProgressMonitor monitor)
            throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public ITypeHierarchy newTypeHierarchy(IType type, IRegion region, WorkingCopyOwner owner, IProgressMonitor monitor)
            throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPath readOutputLocation() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IClasspathEntry[] readRawClasspath() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setOption(String optionName, String optionValue) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setOptions(Map<String, String> newOptions) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setOutputLocation(IPath path, IProgressMonitor monitor) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setRawClasspath(IClasspathEntry[] entries, IPath outputLocation, boolean canModifyResources,
            IProgressMonitor monitor) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setRawClasspath(IClasspathEntry[] entries, boolean canModifyResources, IProgressMonitor monitor)
            throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setRawClasspath(IClasspathEntry[] entries, IClasspathEntry[] referencedEntries, IPath outputLocation,
            IProgressMonitor monitor) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setRawClasspath(IClasspathEntry[] entries, IPath outputLocation, IProgressMonitor monitor)
            throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IClasspathEntry getClasspathEntryFor(IPath path) throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public Set<String> determineModulesOfProjectsWithNonEmptyClasspath() throws JavaModelException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

}
