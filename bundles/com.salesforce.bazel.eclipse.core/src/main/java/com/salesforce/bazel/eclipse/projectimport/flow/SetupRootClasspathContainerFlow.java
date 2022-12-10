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

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;

import com.salesforce.bazel.eclipse.classpath.BazelGlobalSearchClasspathContainer;
import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.component.EclipseBazelWorkspaceContext;
import com.salesforce.bazel.eclipse.runtime.impl.EclipseWorkProgressMonitor;
import com.salesforce.bazel.sdk.index.CodeIndexReporter;
import com.salesforce.bazel.sdk.index.jvm.JvmCodeIndex;
import com.salesforce.bazel.sdk.index.jvm.JvmCodeIndexer;
import com.salesforce.bazel.sdk.index.jvm.JvmCodeIndexerOptions;
import com.salesforce.bazel.sdk.lang.jvm.external.BazelExternalJarRuleManager;
import com.salesforce.bazel.sdk.model.BazelWorkspace;

/**
 * Configures the classpath container for the special root project.
 */
public class SetupRootClasspathContainerFlow implements ImportFlow {
    // until we have a formal feature for providing dep reports, we only print to stdout
    // we disable this by default for release builds
    private static final boolean PRINT_INDEX_REPORT = true;
    
    @Override
    public String getProgressText() {
        return "Configuring the root project classpath.";
    }

    @Override
    public void assertContextState(ImportContext ctx) {
        Objects.requireNonNull(ctx.getRootProject());
    }

    @Override
    public void run(ImportContext ctx, SubMonitor progressSubMonitor) throws CoreException {
        IClasspathEntry cpe = ComponentContext.getInstance().getJavaCoreHelper()
                .newContainerEntry(new Path(BazelGlobalSearchClasspathContainer.CONTAINER_NAME));
        IProject rootProject = ctx.getRootProject();
        IJavaProject javaProject = ComponentContext.getInstance().getJavaCoreHelper().getJavaProjectForProject(rootProject);
        javaProject.setRawClasspath(new IClasspathEntry[] { cpe }, null);

        // trigger the load of the global search index
        if (ComponentContext.getInstance().getConfigurationManager().isGlobalClasspathSearchEnabled()) {
            BazelWorkspace bazelWorkspace = EclipseBazelWorkspaceContext.getInstance().getBazelWorkspace();
            BazelExternalJarRuleManager externalJarManager = ComponentContext.getInstance().getBazelExternalJarRuleManager();
            List<File> additionalJarLocations = BazelGlobalSearchClasspathContainer.loadAdditionalLocations();

            JvmCodeIndexer indexer = new JvmCodeIndexer();
            JvmCodeIndexerOptions indexerOptions = JvmCodeIndexerOptions.buildJvmGlobalSearchOptions();
            
            // this might take a while if it hasn't been computed yet
            JvmCodeIndex index = indexer.buildWorkspaceIndex(bazelWorkspace, externalJarManager, indexerOptions, additionalJarLocations,
                new EclipseWorkProgressMonitor(progressSubMonitor));
            
            
            if (PRINT_INDEX_REPORT) {
                CodeIndexReporter codeIndexReport = new CodeIndexReporter(index);
                Map<String, String> options = new HashMap<>();
                options.put("suppressDeprecated", "true");
    
                List<String> rawList = codeIndexReport.buildArtifactReportAsCSV(options);
                System.out.println("Dependency Report:");
                for (String row : rawList) {
                    System.out.println(row);
                }
                
                
                List<String> ageHistogram = codeIndexReport.buildArtifactAgeHistogramReportAsCSV();
                System.out.println("Dependency Age Report:");
                for (String row : ageHistogram) {
                    System.out.println(row);
                }
            }
        }
    }
}
