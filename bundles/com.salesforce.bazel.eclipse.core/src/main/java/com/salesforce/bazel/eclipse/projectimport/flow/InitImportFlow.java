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
import java.util.Objects;

import org.eclipse.core.runtime.SubMonitor;

import com.salesforce.bazel.eclipse.BazelPluginActivator;
import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.eclipse.component.EclipseBazelWorkspaceContext;
import com.salesforce.bazel.eclipse.preferences.BazelPreferenceKeys;
import com.salesforce.bazel.eclipse.projectimport.ProjectImporterFactory;
import com.salesforce.bazel.eclipse.runtime.api.PreferenceStoreHelper;
import com.salesforce.bazel.eclipse.runtime.api.ResourceHelper;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandOptions;
import com.salesforce.bazel.sdk.lang.jvm.JavaLanguageLevelHelper;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.project.BazelProjectManager;
import com.salesforce.bazel.sdk.project.structure.ProjectStructureStrategy;
import com.salesforce.bazel.sdk.workspace.BazelWorkspaceScanner;

/**
 * Import initialization type work.
 */
public class InitImportFlow extends AbstractImportFlowStep {
    private static final LogHelper LOG = LogHelper.log(InitImportFlow.class);

    public InitImportFlow(BazelCommandManager commandManager, BazelProjectManager projectManager,
            ResourceHelper resourceHelper) {
        super(commandManager, projectManager, resourceHelper);
    }

    @Override
    public String getProgressText() {
        return "Preparing Bazel Eclipse for import.";
    }

    @Override
    public void assertContextState(ImportContext ctx) {
        Objects.requireNonNull(ctx.getSelectedBazelPackages());
        Objects.requireNonNull(ctx.getBazelWorkspaceRootPackageInfo());
    }

    @Override
    public void run(ImportContext ctx, SubMonitor progressMonitor) {
        if (ProjectImporterFactory.importInProgress.get()) {
            throw new IllegalStateException(
                    "A previous import failed without cleaning up properly. Please restart Eclpse before attempting another import. Please also consider filing an issue, thank you.");
        }
        ProjectImporterFactory.importInProgress.set(true);

        File bazelWorkspaceRootDirectory = initContext(ctx);

        validateInitialState(ctx, bazelWorkspaceRootDirectory);

        BazelWorkspace bazelWorkspace = initBazelWorkspace(bazelWorkspaceRootDirectory);

        initWorkspaceOptions(ctx, bazelWorkspace);

        warmupCaches(bazelWorkspace);
    }

    @Override
    public void finish(ImportContext ctx) {
        ProjectImporterFactory.importInProgress.set(false);
    }

    private static void warmupCaches(BazelWorkspace bazelWorkspace) {
        // these are cached - initialize them now so we do not incur the cost of determining these locations
        // later when creating projects
        bazelWorkspace.getBazelOutputBaseDirectory();
        bazelWorkspace.getBazelExecRootDirectory();
    }

    private static void validateInitialState(ImportContext ctx, File importBazelWorkspaceRootDirectory) {
        BazelWorkspace existingBazelWorkspace = getExistingBazelWorkspace();

        if (existingBazelWorkspace != null) {
            File existingBazelWorkspaceRootDirectory = existingBazelWorkspace.getBazelWorkspaceRootDirectory();
            if (!importBazelWorkspaceRootDirectory.equals(existingBazelWorkspaceRootDirectory)) {
                // we do not currently support importing two Bazel Workspaces into an Eclipse workspace
                // https://github.com/salesforce/bazel-eclipse/issues/25
                throw new IllegalStateException(
                        "Bazel Eclipse currently does not support importing multiple Bazel workspaces into a single Eclipse workspace.");
            }
        }
    }

    private static void initWorkspaceOptions(ImportContext ctx, BazelWorkspace bazelWorkspace) {
        // get the Workspace options (.bazelrc)
        // note that this ends up running bazel (bazel test --announce_rc)
        BazelWorkspaceCommandOptions options = bazelWorkspace.getBazelWorkspaceCommandOptions();
        // determine the Java levels
        String javacoptString = options.getContextualOption("build", "javacopt");
        int sourceLevel = JavaLanguageLevelHelper.getSourceLevelAsInt(javacoptString);
        ctx.setJavaLanguageLevel(sourceLevel);

        // we support pluggable project structure strategies to optimize import performance
        // this will use knowledge of common patterns like Maven to quickly locate source directories
        // but if a strategy gets this wrong it can cause problems
        PreferenceStoreHelper resourceHelper = ComponentContext.getInstance().getPreferenceStoreHelper();
        boolean enabledStructureStrategies =
                resourceHelper.getBoolean(BazelPreferenceKeys.PROJECTSTRUCTUREOPTIMIZATIONS_PREF_NAME);
        if (!enabledStructureStrategies) {
            LOG.warn(
                "The pluggable project structure strategies are disabled which is not the default, but is sometimes disabled by a user to workaround an issue.");
        }
        ProjectStructureStrategy.toggleEnableNonrequiredStrategies(enabledStructureStrategies);

    }

    private static File initContext(ImportContext ctx) {
        BazelPackageLocation bazelWorkspaceRootPackageInfo = ctx.getBazelWorkspaceRootPackageInfo();
        File bazelWorkspaceRootDirectory =
                FSPathHelper.getCanonicalFileSafely(bazelWorkspaceRootPackageInfo.getWorkspaceRootDirectory());
        ctx.init(bazelWorkspaceRootDirectory, ComponentContext.getInstance().getProjectManager(),
            ComponentContext.getInstance().getResourceHelper(), ComponentContext.getInstance().getBazelCommandManager());
        return bazelWorkspaceRootDirectory;
    }

    private static BazelWorkspace initBazelWorkspace(File bazelWorkspaceRootDirectory) {
        BazelWorkspace bazelWorkspace = EclipseBazelWorkspaceContext.getInstance().getBazelWorkspace();
        boolean isInitialImport = bazelWorkspace == null;
        String bazelWorkspaceName = null;
        if (isInitialImport) {
            bazelWorkspaceName = BazelWorkspaceScanner.getBazelWorkspaceName(bazelWorkspaceRootDirectory.getName());

            // Many collaborators need the Bazel workspace directory location, so we stash it in an accessible global location
            // currently we only support one Bazel workspace in an Eclipse workspace
            BazelPluginActivator.getInstance().setBazelWorkspaceRootDirectory(bazelWorkspaceName,
                bazelWorkspaceRootDirectory);
        } else {
            bazelWorkspaceName = bazelWorkspace.getName();
        }

        bazelWorkspace = getExistingBazelWorkspace();

        return bazelWorkspace;
    }

    // TEST INFRA
    // With everything static, it makes it difficult to mock
    // TODO make import Flow testable/mockable

    // for tests only
    static BazelWorkspace existingBazelWorkspace = null;

    /**
     * Returns the BazelWorkspace that has already been imported into the Eclipse workspace, if one exists. This method
     * exists to make unit tests possible.
     */
    protected static BazelWorkspace getExistingBazelWorkspace() {
        if (existingBazelWorkspace != null) {
            return existingBazelWorkspace;
        }
        return EclipseBazelWorkspaceContext.getInstance().getBazelWorkspace();
    }
}
