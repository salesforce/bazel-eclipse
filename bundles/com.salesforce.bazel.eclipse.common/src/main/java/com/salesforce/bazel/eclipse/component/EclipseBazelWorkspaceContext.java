package com.salesforce.bazel.eclipse.component;

import java.io.File;

import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.model.BazelWorkspace;

public class EclipseBazelWorkspaceContext {
    private static final LogHelper LOG = LogHelper.log(EclipseBazelWorkspaceContext.class);

    private static EclipseBazelWorkspaceContext instance;
    /**
     * The Bazel workspace that is in scope. Currently, we only support one Bazel workspace in an Eclipse workspace.
     */
    private BazelWorkspace bazelWorkspace;
    /**
     * Runs bazel commands in the loaded workspace.
     */
    private BazelWorkspaceCommandRunner bazelWorkspaceCommandRunner;

    private EclipseBazelWorkspaceContext() {
        bazelWorkspace = null;
    }

    public static synchronized EclipseBazelWorkspaceContext getInstance() {
        if (instance == null) {
            instance = new EclipseBazelWorkspaceContext();
        }
        return instance;
    }

    /**
     * Returns the location on disk where the Bazel workspace is located. There must be a WORKSPACE file in this
     * location. Prior to importing/opening a Bazel workspace, this location will be null
     */
    public File getBazelWorkspaceRootDirectory() {
        return bazelWorkspace.getBazelWorkspaceRootDirectory();
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
        bazelWorkspace =
                new BazelWorkspace(workspaceName, rootDirectory, ComponentContext.getInstance().getOsStrategy());
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
                bazelWorkspaceCommandRunner = ComponentContext.getInstance().getBazelCommandManager()
                        .getWorkspaceCommandRunner(getBazelWorkspace());
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
     * 
     * @deprecated this method is an architectural issue - we need a better way of closing/refreshing a Bazel workspace
     */
    @Deprecated
    public void resetBazelWorkspace() {
        // now forget about the workspace
        bazelWorkspace = null;
        bazelWorkspaceCommandRunner = null;
    }

}
