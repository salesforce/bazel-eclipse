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
 * 
 */
package com.salesforce.bazel.eclipse.mock;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;

public class MockIClasspathEntry implements IClasspathEntry {
    private static final String UOE_MSG =
            "MockIClasspathEntry is pay as you go, you have hit a method that is not implemented.";

    private final int entryKind;
    private final IPath sourcePath;
    private IPath outputLocation;

    // TODO need to test behaviors related to inclusion/exclusion patterns, right now we assume they aren't set, which is the default behavior
    private IPath[] exclusionPatterns = new IPath[] {};
    private IPath[] inclusionPatterns = new IPath[] {};

    private List<IClasspathAttribute> extraAttributes = new ArrayList<>();

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
        this.sourcePath = path;
    }

    public void addExtraAttribute(IClasspathAttribute attr) {
        this.extraAttributes.add(attr);
    }

    public void setOutputLocation(IPath out) {
        this.outputLocation = out;
    }

    // API

    @Override
    public int getEntryKind() {
        return this.entryKind;
    }

    @Override
    public IPath[] getExclusionPatterns() {
        return exclusionPatterns;
    }

    @Override
    public IClasspathAttribute[] getExtraAttributes() {
        return this.extraAttributes.toArray(new IClasspathAttribute[] {});
    }

    @Override
    public IPath[] getInclusionPatterns() {
        return inclusionPatterns;
    }

    @Override
    public IPath getOutputLocation() {
        return this.outputLocation;
    }

    @Override
    public IPath getPath() {
        return sourcePath;
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
