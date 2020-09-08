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

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.ISourceLocator;

public class MockILaunch implements ILaunch {
    private static final String UOE_MSG =
            "MockILaunch is pay as you go, you have hit a method that is not implemented.";
    private ILaunchConfiguration launchConfig;
    private Map<String, String> attributes = new TreeMap<>();
    @SuppressWarnings("unused")
    private IProcess process;

    public MockILaunch(ILaunchConfiguration launchConfig) {
        this.launchConfig = launchConfig;
    }

    // IMPLEMENTED METHODS

    @Override
    public void addProcess(IProcess process) {
        this.process = process;
    }

    @Override
    public String getAttribute(String key) {
        return this.attributes.get(key);
    }

    @Override
    public ILaunchConfiguration getLaunchConfiguration() {
        return this.launchConfig;
    }

    @Override
    public void setAttribute(String key, String value) {
        this.attributes.put(key, value);
    }

    // UNIMPLEMENTED METHODS
    // Please move implemented methods, in alphabetical order, above this line if you implement a method.

    @Override
    public boolean canTerminate() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean isTerminated() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void terminate() throws DebugException {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public Object[] getChildren() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IDebugTarget getDebugTarget() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IProcess[] getProcesses() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public IDebugTarget[] getDebugTargets() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void addDebugTarget(IDebugTarget target) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void removeDebugTarget(IDebugTarget target) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void removeProcess(IProcess process) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public ISourceLocator getSourceLocator() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public void setSourceLocator(ISourceLocator sourceLocator) {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public String getLaunchMode() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

    @Override
    public boolean hasChildren() {
        throw new UnsupportedOperationException(UOE_MSG);
    }

}
