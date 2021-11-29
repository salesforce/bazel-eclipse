package com.salesforce.bazel.sdk.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandOptions;
import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.logging.LogHelper;
import com.salesforce.bazel.sdk.workspace.BazelWorkspaceMetadataStrategy;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;

public class BazelWorkspace {
    private static final LogHelper LOG = LogHelper.log(BazelWorkspace.class);

    // DATA

    /**
     * The location on disk for the workspace.
     */
    private final File bazelWorkspaceRootDirectory;

    /**
     * Workspace name, as assigned in the WORKSPACE file or computed from directory name
     */
    private final String name;

    // COLLABORATORS

    /**
     * Bazel command runner for this workspace
     */
    private BazelWorkspaceCommandRunner commandRunner;

    /**
     * Strategy delegate that can compute the data for file paths
     */
    private BazelWorkspaceMetadataStrategy metadataStrategy;

    // COMPUTED DATA

    /**
     * The internal location on disk for Bazel's 'execroot' for this workspace. E.g.
     * <i>/private/var/tmp/_bazel_plaird/edb34c7f4bfffeb66012c4fc6aaab239/execroot/bazel_demo_simplejava</i>
     * <p>
     * Determined by running this command line: <i>bazel info execution_root</i>
     */
    private File bazelExecRootDirectory;

    /**
     * The internal location on disk for Bazel's 'output base' for this workspace. E.g.
     * <i>/private/var/tmp/_bazel_plaird/edb34c7f4bfffeb66012c4fc6aaab239</i>
     * <p>
     * Determined by running this command line: <i>bazel info output_base</i>
     */
    private File bazelOutputBaseDirectory;

    /**
     * The internal location on disk for Bazel's 'output path' for this workspace. E.g.
     * <i>/private/var/tmp/_bazel_plaird/edb34c7f4bfffeb66012c4fc6aaab239/execroot/bazel_demo_simplejava/bazel-out</i>
     * <p>
     * Determined by running this command line: <i>bazel info output_path</i>
     */
    private File bazelOutputPathDirectory;

    /**
     * The internal location on disk for Bazel's 'bazel-bin' for this workspace. E.g.
     * <i>/private/var/tmp/_bazel_plaird/f521799c9882dcc6330b57416b13ba81/execroot/bazel_eclipse_feature/bazel-out/darwin-fastbuild/bin</i>
     * <p>
     * Determined by running this command line: <i>bazel info bazel-bin</i>
     */
    private File bazelBinDirectory;

    /**
     * The operating system running Bazel and our BEF: osx, linux, windows
     * https://github.com/bazelbuild/bazel/blob/c35746d7f3708acb0d39f3082341de0ff09bd95f/src/main/java/com/google/devtools/build/lib/util/OS.java#L21
     */
    private final String operatingSystem;

    /**
     * The OS identifier used in file system constructs: darwin, linux, windows
     */
    private final String operatingSystemFoldername;

    /**
     * List of Bazel command options that apply for all workspace commands (i.e. from .bazelrc)
     */
    private BazelWorkspaceCommandOptions commandOptions;

    // CTORS AND INITIALIZERS

    public BazelWorkspace(String name, File bazelWorkspaceRootDirectory,
            OperatingEnvironmentDetectionStrategy osEnvStrategy, BazelWorkspaceMetadataStrategy metadataStrategy) {
        this(name, bazelWorkspaceRootDirectory, osEnvStrategy);
        this.metadataStrategy = metadataStrategy;
    }

    public BazelWorkspace(String name, File bazelWorkspaceRootDirectory,
            OperatingEnvironmentDetectionStrategy osEnvStrategy) {
        this.name = name;
        this.bazelWorkspaceRootDirectory = getCanonicalFileSafely(bazelWorkspaceRootDirectory);
        operatingSystem = osEnvStrategy.getOperatingSystemName();
        operatingSystemFoldername = osEnvStrategy.getOperatingSystemDirectoryName(operatingSystem);
    }

    public void setBazelWorkspaceCommandRunner(BazelWorkspaceCommandRunner runner) {
        commandRunner = runner;
    }

    public void setBazelWorkspaceMetadataStrategy(BazelWorkspaceMetadataStrategy metadataStrategy) {
        this.metadataStrategy = metadataStrategy;
    }

    /**
     * Resolve softlinks and other abstractions in the workspace path.
     */
    private File getCanonicalFileSafely(File directory) {
        if (directory == null) {
            return null;
        }
        try {
            directory = directory.getCanonicalFile();
        } catch (IOException ioe) {
            LOG.error("Error locating path [{}] on the file system", ioe, directory.getAbsolutePath());
        }
        return directory;
    }

    // GETTERS AND SETTERS

    public File getBazelWorkspaceRootDirectory() {
        return bazelWorkspaceRootDirectory;
    }

    public boolean hasBazelWorkspaceRootDirectory() {
        return bazelWorkspaceRootDirectory != null;
    }

    public String getName() {
        return name;
    }

    public File getBazelExecRootDirectory() {
        if ((bazelExecRootDirectory == null) && (metadataStrategy != null)) {
            bazelExecRootDirectory = metadataStrategy.computeBazelWorkspaceExecRoot();
            if (bazelExecRootDirectory != null) {
                bazelOutputPathDirectory = new File(bazelExecRootDirectory, "bazel-out");
            }
        }
        return bazelExecRootDirectory;
    }

    public File getBazelOutputBaseDirectory() {
        if ((bazelOutputBaseDirectory == null) && (metadataStrategy != null)) {
            bazelOutputBaseDirectory = metadataStrategy.computeBazelWorkspaceOutputBase();
        }
        return bazelOutputBaseDirectory;
    }

    public File getBazelOutputPathDirectory() {
        if (bazelOutputPathDirectory == null) {
            // since output path dir is a direct descendant of exec root, we just rely on that method to compute it
            bazelExecRootDirectory = getBazelExecRootDirectory();
        }
        return bazelOutputPathDirectory;
    }

    public List<String> getTargetsForBazelQuery(String query) {
        List<String> results = new ArrayList<String>();
        for (String line : metadataStrategy.computeBazelQuery(query)) {
            if (line.startsWith(BazelLabel.BAZEL_ROOT_SLASHES)) {
                // the trim() is essential here, on Windows, otherwise we end up with a trailing
                // /r character which is a carriage return
                results.add(line.trim());
            }
        }
        return results;
    }

    public File getBazelBinDirectory() {
        if ((bazelBinDirectory == null) && (metadataStrategy != null)) {
            bazelBinDirectory = metadataStrategy.computeBazelWorkspaceBin();
        }
        return bazelBinDirectory;
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public String getOperatingSystemFoldername() {
        return operatingSystemFoldername;
    }

    public BazelWorkspaceCommandRunner getBazelWorkspaceCommandRunner() {
        return commandRunner;
    }

    public BazelWorkspaceCommandOptions getBazelWorkspaceCommandOptions() {
        if (commandOptions == null) {
            commandOptions = new BazelWorkspaceCommandOptions(this);
            metadataStrategy.populateBazelWorkspaceCommandOptions(commandOptions);
        }
        return commandOptions;
    }

    // EQUALITY
    // We are using location on the file system of the WORKSPACE file as the sole input into hashcode/equals

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((bazelWorkspaceRootDirectory == null) ? 0 : bazelWorkspaceRootDirectory.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BazelWorkspace other = (BazelWorkspace) obj;
        if (bazelWorkspaceRootDirectory == null) {
            if (other.bazelWorkspaceRootDirectory != null)
                return false;
        } else if (!bazelWorkspaceRootDirectory.equals(other.bazelWorkspaceRootDirectory))
            return false;
        return true;
    }

}
