package com.salesforce.bazel.eclipse.component;

import java.io.File;
import java.lang.invoke.MethodHandles;

import org.apache.commons.lang3.StringUtils;

import com.salesforce.bazel.eclipse.utils.BazelCompilerUtils;
import com.salesforce.bazel.sdk.aspect.BazelAspectLocation;
import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.command.CommandBuilder;
import com.salesforce.bazel.sdk.console.CommandConsoleFactory;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.workspace.RealOperatingEnvironmentDetectionStrategy;

public class EclipseBazelComponentFacade {
    private static final LogHelper LOG = LogHelper.log(MethodHandles.lookup().lookupClass());

    private static EclipseBazelComponentFacade instance;

    private final OperatingEnvironmentDetectionStrategy osDetectionStrategy;
    /**
     * The Bazel workspace that is in scope. Currently, we only support one Bazel workspace in an Eclipse workspace.
     */
    private BazelWorkspace bazelWorkspace;
    /**
     * Facade that enables the plugin to execute the bazel command line tool outside of a workspace
     */
    private BazelCommandManager bazelCommandManager;
    /**
     * Runs bazel commands in the loaded workspace.
     */
    private BazelWorkspaceCommandRunner bazelWorkspaceCommandRunner;

    private String bazelExecutablePath;

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
            CommandConsoleFactory consoleFactory, String filePath) {
        String path = StringUtils.isNotBlank(filePath) ? filePath : BazelCompilerUtils.getBazelPath();
        File bazelPathFile = new File(path);
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
                LOG.error("Bazel workspace directory could not be set the as there is no WORKSPACE file here: {}",
                    rootDirectory.getAbsolutePath());
                return;
            }
        }
        bazelWorkspace = new BazelWorkspace(workspaceName, rootDirectory,
                EclipseBazelComponentFacade.getInstance().getOsDetectionStrategy());
        BazelWorkspaceCommandRunner commandRunner = getWorkspaceCommandRunner();
        getBazelWorkspace().setBazelWorkspaceMetadataStrategy(commandRunner);
        getBazelWorkspace().setBazelWorkspaceCommandRunner(commandRunner);
    }

    /**
     * Once the workspace is set, the workspace command runner is available. Otherwise returns null
     */
    public BazelWorkspaceCommandRunner getWorkspaceCommandRunner() {
        if (bazelWorkspaceCommandRunner == null) {
            if (getBazelWorkspace() == null) {
                return null;
            }
            if (getBazelWorkspace().hasBazelWorkspaceRootDirectory()) {
                bazelWorkspaceCommandRunner = bazelCommandManager.getWorkspaceCommandRunner(getBazelWorkspace());
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
        return getBazelWorkspace().hasBazelWorkspaceRootDirectory();
    }

    /**
     * User is deleting the Bazel Workspace project from the Eclipse workspace. Do what we can here. To reset back to
     * initial state, but hard to guarantee that this will be perfect. If the user does NOT also delete the Bazel
     * workspace code projects, there could be trouble.
     */
    public void resetBazelWorkspace() {
        // now forget about the workspace
        bazelWorkspace = null;
        bazelWorkspaceCommandRunner = null;
    }

    public String getBazelExecutablePath() {
        return bazelExecutablePath;
    }

    public void setBazelExecutablePath(String bazelExecutablePath) {
        this.bazelExecutablePath = bazelExecutablePath;
    }

}
