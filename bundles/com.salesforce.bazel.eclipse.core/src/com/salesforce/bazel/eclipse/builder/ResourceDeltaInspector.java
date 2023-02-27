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
 *
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.salesforce.bazel.eclipse.builder;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.sdk.util.BazelConstants;

/**
 * Convenience methods for looking into a IResourceDelta.
 */
public class ResourceDeltaInspector {

    private static class ChangedResourceVisitor implements IResourceDeltaVisitor {

        private final Collection<String> filenameNeedles;
        private final Collection<IResource> matchingResources;

        private ChangedResourceVisitor(Collection<String> filenameNeedles, Collection<IResource> matchingResources) {
            this.filenameNeedles = filenameNeedles;
            this.matchingResources = matchingResources;
        }

        @Override
        public boolean visit(IResourceDelta delta) throws CoreException {
            if ((delta.getKind() == IResourceDelta.CHANGED) && ((delta.getFlags() & IResourceDelta.CONTENT) != 0)) {
                var resource = delta.getResource();
                if ((resource.getType() == IResource.FILE) && filenameNeedles.contains(resource.getName())) {
                    matchingResources.add(resource);
                    return false; // stop visiting
                }
            }
            return true;
        }
    }

    private static Logger LOG = LoggerFactory.getLogger(ResourceDeltaInspector.class);

    public static boolean deltaHasChangedBuildFiles(IResourceDelta delta) {
        return hasChangedFiles(delta, BazelConstants.BUILD_FILE_NAMES);
    }

    private static boolean hasChangedFiles(IResourceDelta delta, Collection<String> filenameNeedles) {
        if (delta == null) {
            throw new IllegalArgumentException("Field delta cannot be null.");
        }
        try {
            Collection<IResource> matchingResources = new ArrayList<>();
            delta.accept(new ChangedResourceVisitor(filenameNeedles, matchingResources));
            return !matchingResources.isEmpty();
        } catch (CoreException ex) {
            LOG.error("Error while inspecting IResourceDelta", ex);
            return false;
        }
    }
}
