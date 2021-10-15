package com.salesforce.bazel.eclipse.component;

import java.io.File;

import com.salesforce.bazel.eclipse.activator.Activator;
import com.salesforce.bazel.eclipse.utils.BazelCompilerUtils;
import com.salesforce.bazel.sdk.aspect.BazelAspectLocation;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.command.CommandBuilder;
import com.salesforce.bazel.sdk.console.CommandConsoleFactory;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.workspace.RealOperatingEnvironmentDetectionStrategy;

public class EclipseBazelComponentFacade {

    private static EclipseBazelComponentFacade instance;

    private final OperatingEnvironmentDetectionStrategy osDetectionStrategy;
    private BazelWorkspace bazelWorkspace;
    /**
     * Facade that enables the plugin to execute the bazel command line tool outside of a workspace
     */
    private BazelCommandManager bazelCommandManager;
    /**
     * Runs bazel commands in the loaded workspace.
     */
    private BazelWorkspaceCommandRunner bazelWorkspaceCommandRunner;

    private EclipseBazelComponentFacade() {
        osDetectionStrategy = new RealOperatingEnvironmentDetectionStrategy();
        bazelWorkspace = null;
    }

    public static synchronized EclipseBazelComponentFacade getInstance() {
        if (instance == null) {
            instance = new EclipseBazelComponentFacade();
        }
        return instance;
    }

    public OperatingEnvironmentDetectionStrategy getOsDetectionStrategy() {
        return osDetectionStrategy;
    }

    /**
     * Returns the location on disk where the Bazel workspace is located. There must be a WORKSPACE file in this
     * location. Prior to importing/opening a Bazel workspace, this location will be null
     */
    public File getBazelWorkspaceRootDirectory() {
        return bazelWorkspace.getBazelWorkspaceRootDirectory();
    }

    /**
     * Returns the unique instance of {@link BazelCommandManager}, the facade enables the plugin to execute the bazel
     * command line tool.
     */
    public BazelCommandManager getBazelCommandManager() {
        return bazelCommandManager;
    }

    public void setCommandManager(BazelAspectLocation aspectLocation, CommandBuilder commandBuilder,
            CommandConsoleFactory consoleFactory) {
        File bazelPathFile = new File(BazelCompilerUtils.getBazelPath());
        bazelCommandManager = new BazelCommandManager(aspectLocation, commandBuilder, consoleFactory, bazelPathFile);
    }

    /**
     * Sets the location on disk where the Bazel workspace is located. There must be a WORKSPACE file in this location.
     * Changing this location is a big deal, so use this method only during setup/import.
     */
    public void setBazelWorkspaceRootDirectory(String workspaceName, File rootDirectory) {
        File workspaceFile = new File(rootDirectory, "WORKSPACE");
        if (!workspaceFile.exists()) {
            workspaceFile = new File(rootDirectory, "WORKSPACE.bazel");
            if (!workspaceFile.exists()) {
                new IllegalArgumentException();
                Activator.getDefault()
                        .logError("Bazel workspace directory could not be set the as there is no WORKSPACE file here: "
                                + rootDirectory.getAbsolutePath());
                return;
            }
        }
        bazelWorkspace = new BazelWorkspace(workspaceName, rootDirectory,
                EclipseBazelComponentFacade.getInstance().getOsDetectionStrategy());
        BazelWorkspaceCommandRunner commandRunner = getWorkspaceCommandRunner();
        bazelWorkspace.setBazelWorkspaceMetadataStrategy(commandRunner);
        bazelWorkspace.setBazelWorkspaceCommandRunner(commandRunner);
    }

    /**
     * Once the workspace is set, the workspace command runner is available. Otherwise returns null
     */
    public BazelWorkspaceCommandRunner getWorkspaceCommandRunner() {
        if (bazelWorkspaceCommandRunner == null) {
            if (bazelWorkspace == null) {
                return null;
            }
            if (bazelWorkspace.hasBazelWorkspaceRootDirectory()) {
                bazelWorkspaceCommandRunner = bazelCommandManager.getWorkspaceCommandRunner(bazelWorkspace);
            }
        }
        return bazelWorkspaceCommandRunner;
    }

    /**
     * Returns the model abstraction for the Bazel workspace
     */
    public BazelWorkspace getBazelWorkspace() {
        return bazelWorkspace;
    }

    /**
     * Has the Bazel workspace location been imported/loaded? This is a good sanity check before doing any operation
     * related to Bazel or Bazel Java projects.
     */
    public boolean hasBazelWorkspaceRootDirectory() {
        return bazelWorkspace.hasBazelWorkspaceRootDirectory();
    }
}
