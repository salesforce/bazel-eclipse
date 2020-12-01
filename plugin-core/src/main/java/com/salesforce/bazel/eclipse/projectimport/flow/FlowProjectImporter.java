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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import com.salesforce.bazel.eclipse.projectimport.ProjectImporter;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.util.SimplePerfRecorder;
import com.salesforce.bazel.sdk.workspace.ProjectOrderResolver;

/**
 * Calls on ProjectImportFlow instances to drive project import.
 *
 * @author stoens
 * @since Fall 2020
 */
public class FlowProjectImporter implements ProjectImporter {

    private final BazelPackageLocation bazelWorkspaceRootPackageInfo;
    private final List<BazelPackageLocation> selectedBazelPackages;
    private final ProjectOrderResolver projectOrderResolver;
    private final ImportFlow[] flows;

    public FlowProjectImporter(ImportFlow[] flows,
                            BazelPackageLocation bazelWorkspaceRootPackageInfo,
                            List<BazelPackageLocation> selectedBazelPackages,
                            ProjectOrderResolver projectOrderResolver)
    {
        this.flows = Objects.requireNonNull(flows);
        this.bazelWorkspaceRootPackageInfo = Objects.requireNonNull(bazelWorkspaceRootPackageInfo);
        this.selectedBazelPackages = Objects.requireNonNull(selectedBazelPackages);
        this.projectOrderResolver = Objects.requireNonNull(projectOrderResolver);
    }

    @Override
    public List<IProject> run(IProgressMonitor progressMonitor) {
        ImportContext ctx = new ImportContext(bazelWorkspaceRootPackageInfo, selectedBazelPackages, projectOrderResolver);
        SimplePerfRecorder.reset();
        long startTimeMillis = System.currentTimeMillis();
        runFlows(ctx, progressMonitor);
        finishFlows(ctx);
        SimplePerfRecorder.addTime("import_total", startTimeMillis);
        SimplePerfRecorder.logResults();
        return ctx.getAllImportedProjects();
    }

    private void runFlows(ImportContext ctx, IProgressMonitor progressMonitor) {
        SubMonitor subMonitor = SubMonitor.convert(progressMonitor, flows.length);
        for (int i = 0; i < flows.length; i++) {
            ImportFlow flow = flows[i];
            long startTimeMillis = System.currentTimeMillis();
            flow.assertContextState(ctx);
            try {
                subMonitor.setTaskName(flow.getProgressText());
                subMonitor.setWorkRemaining(flows.length - i + flow.getTotalWorkTicks(ctx));
                flow.run(ctx, subMonitor);
                subMonitor.worked(1);
            } catch (Throwable th) {
                th.printStackTrace();
                // this needs to be handled better - generally, error handing is still a mess
                // this could call a cleanup method on each Flow instance already processed if we
                // need to undo work done so far
                // https://github.com/salesforce/bazel-eclipse/issues/193
                throw new RuntimeException(th);
            }
            SimplePerfRecorder.addTime("import_" + flow.getClass().getSimpleName(), startTimeMillis);
        }
    }

    private void finishFlows(ImportContext ctx) {
        for (ImportFlow flow : reversed(flows)) {
            flow.finish(ctx);
        }
    }

    private static ImportFlow[] reversed(ImportFlow[] flows) {
        List<ImportFlow> l = new ArrayList<>(Arrays.asList(flows));
        Collections.reverse(l);
        return l.toArray(new ImportFlow[l.size()]);
    }
}
