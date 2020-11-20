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
 */
package com.salesforce.bazel.eclipse.projectimport.flow;

import java.util.Objects;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;

import com.salesforce.bazel.eclipse.builder.BazelBuilder;
import com.salesforce.bazel.sdk.logging.LogHelper;

/**
 * Configures the builders for each project.
 */
public class SetupProjectBuildersFlow implements ImportFlow {

    static final LogHelper LOG = LogHelper.log(SetupProjectBuildersFlow.class);

    @Override
    public void assertContextState(ImportContext ctx) {
        Objects.requireNonNull(ctx.getAllImportedProjects());
    }

    @Override
    public void run(ImportContext ctx) {
        for (IProject project : ctx.getImportedProjects()) {
            // this may throw if the user has deleted the .project file on disk while the project is open for import
            // but it will try to recover so we should catch now instead of allowing the entire flow to fail.
            // "The project description file (.project) for 'Bazel Workspace (simplejava)' was missing.  This file contains important information about the project.
            //  A new project description file has been created, but some information about the project may have been lost."
            try {
                setBuilder(project);
            } catch (CoreException e) {
                LOG.error(e.getMessage(), e);
            }
        }
    }

    private static void setBuilder(IProject project) throws CoreException {
        IProjectDescription projectDescription = project.getDescription();
        ICommand buildCommand = projectDescription.newCommand();
        buildCommand.setBuilderName(BazelBuilder.BUILDER_NAME);
        projectDescription.setBuildSpec(new ICommand[] {buildCommand});
        project.setDescription(projectDescription, null);
    }

}
