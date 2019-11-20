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
package com.salesforce.bazel.eclipse.runtime;

import org.eclipse.core.runtime.IProgressMonitor;

import com.salesforce.bazel.eclipse.abstractions.WorkProgressMonitor;

/**
 * Implementation of Eclipse agnostic WorkProgressMonitor interface that delegates to an Eclipse IProgressMonitor
 * implementation.
 * <p>
 * To create a no-op monitor, pass null to the constructor.
 */
public class EclipseWorkProgressMonitor implements WorkProgressMonitor {

    private final IProgressMonitor eclipseMonitor;
    private boolean isCanceled = false;

    /**
     * Creates an abstraction wrapper around an Eclipse IProgressMonitor.
     *
     * @param eclipseMonitor
     *            the inner monitor, if null this monitor will be a no-op monitor
     */
    public EclipseWorkProgressMonitor(IProgressMonitor eclipseMonitor) {
        this.eclipseMonitor = eclipseMonitor;
    }

    /**
     * No-op progress monitor. Use this only for tests.
     */
    public EclipseWorkProgressMonitor() {
        eclipseMonitor = null;
    }
    
    @Override
    public void beginTask(String name, int totalWork) {
        if (this.eclipseMonitor != null) {
            this.eclipseMonitor.beginTask(name, totalWork);
        }
    }

    @Override
    public void done() {
        if (this.eclipseMonitor != null) {
            this.eclipseMonitor.done();
        }
    }

    @Override
    public boolean isCanceled() {
        if (this.eclipseMonitor != null) {
            return this.eclipseMonitor.isCanceled();
        }
        return isCanceled;
    }

    @Override
    public void setCanceled(boolean value) {
        if (this.eclipseMonitor != null) {
            this.eclipseMonitor.setCanceled(value);
        } else {
            isCanceled = value;
        }
    }

    @Override
    public void subTask(String name) {
        if (this.eclipseMonitor != null) {
            this.eclipseMonitor.subTask(name);
        }
    }

    @Override
    public void worked(int work) {
        if (this.eclipseMonitor != null) {
            this.eclipseMonitor.worked(work);
        }
    }

}
