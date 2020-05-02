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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchDelegate;

public class MockILaunchConfiguration implements ILaunchConfiguration {
    private static final String UOE_MSG = "MockILaunchConfiguration is pay as you go, you have hit a method that is not implemented.";
    public Map<String, Object> attributes = new TreeMap<>();

    // IMPLEMENTED METHODS

    @Override
    public boolean getAttribute(String attributeName, boolean defaultValue) throws CoreException {
        return (Boolean) attributes.getOrDefault(attributeName, defaultValue);
    }

    @Override
    public int getAttribute(String attributeName, int defaultValue) throws CoreException {
        return (Integer) attributes.getOrDefault(attributeName, defaultValue);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getAttribute(String attributeName, List<String> defaultValue) throws CoreException {
        return (List<String>) attributes.getOrDefault(attributeName, defaultValue);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<String> getAttribute(String attributeName, Set<String> defaultValue) throws CoreException {
        return (Set<String>) attributes.getOrDefault(attributeName, defaultValue);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, String> getAttribute(String attributeName, Map<String, String> defaultValue)
            throws CoreException {
        return (Map<String, String>) attributes.getOrDefault(attributeName, defaultValue);
    }

    @Override
    public String getAttribute(String attributeName, String defaultValue) throws CoreException {
        return (String) attributes.getOrDefault(attributeName, defaultValue);
    }

    @Override
    public Map<String, Object> getAttributes() throws CoreException {
        return attributes;
    }

    @Override
    public boolean hasAttribute(String attributeName) throws CoreException {
        return attributes.containsKey(attributeName);
    }


    // UNIMPLEMENTED METHODS
    // Please move implemented methods, in alphabetical order, above this line if you implement a method.

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean contentsEqual(ILaunchConfiguration configuration) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public ILaunchConfigurationWorkingCopy copy(String name) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void delete() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void delete(int flag) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean exists() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String getCategory() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IFile getFile() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IPath getLocation() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IResource[] getMappedResources() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String getMemento() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public Set<String> getModes() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public ILaunchDelegate getPreferredDelegate(Set<String> modes) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public ILaunchConfigurationType getType() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public ILaunchConfigurationWorkingCopy getWorkingCopy() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isLocal() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isMigrationCandidate() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isWorkingCopy() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public ILaunch launch(String mode, IProgressMonitor monitor) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public ILaunch launch(String mode, IProgressMonitor monitor, boolean build) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public ILaunch launch(String mode, IProgressMonitor monitor, boolean build, boolean register) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void migrate() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean supportsMode(String mode) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isReadOnly() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public ILaunchConfiguration getPrototype() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isAttributeModified(String attribute) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isPrototype() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public Collection<ILaunchConfiguration> getPrototypeChildren() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public int getKind() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public Set<String> getPrototypeVisibleAttributes() throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setPrototypeAttributeVisibility(String attribute, boolean visible) throws CoreException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

}
