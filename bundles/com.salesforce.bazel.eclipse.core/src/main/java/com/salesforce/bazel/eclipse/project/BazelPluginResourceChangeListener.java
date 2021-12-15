/**
 * Copyright (c) 2021, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.eclipse.project;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;

import com.salesforce.bazel.eclipse.BazelNature;
import com.salesforce.bazel.eclipse.component.EclipseBazelWorkspaceContext;
import com.salesforce.bazel.sdk.logging.LogHelper;

/**
 * Global change listener for Bazel plugin for Eclipse Workspaces.
 */
public class BazelPluginResourceChangeListener implements IResourceChangeListener {
    static final LogHelper LOG = LogHelper.log(BazelPluginResourceChangeListener.class);

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        IResource resource = event.getResource();
        if (resource == null) {
            return;
        }

        if (resource instanceof IProject) {
            IProject project = (IProject) resource;
            String name = project.getName();
            if (name.startsWith(BazelNature.BAZELWORKSPACE_PROJECT_BASENAME)
                    && (event.getType() == IResourceChangeEvent.PRE_DELETE)) {
                LOG.info("User is deleting the Bazel Workspace project {} from the Eclipse workspace", name);
                EclipseBazelWorkspaceContext.getInstance().resetBazelWorkspace();
            }
        }
    }

}
