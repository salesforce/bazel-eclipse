package com.salesforce.bazel.eclipse.model;

import java.io.File;

public class BazelWorkspace {

    // COLLABORATORS
    
    /**
     * The location on disk for the workspace.
     */
    private final File bazelWorkspaceRootDirectory;
    
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
    private String operatingSystem;

    /**
     * The OS identifier used in file system constructs: darwin, linux, windows
     */
    private String operatingSystemFoldername;

    
    // CTORS AND INITIALIZERS
    
    public BazelWorkspace(File bazelWorkspaceRootDirectory, OperatingEnvironmentDetectionStrategy osEnvStrategy) {
        this.bazelWorkspaceRootDirectory = bazelWorkspaceRootDirectory;
        this.operatingSystem = osEnvStrategy.getOperatingSystemName();
        this.operatingSystemFoldername = osEnvStrategy.getOperatingSystemDirectoryName(this.operatingSystem);
    }
    
    public void setBazelWorkspaceMetadataStrategy(BazelWorkspaceMetadataStrategy metadataStrategy) {
        this.metadataStrategy = metadataStrategy;
    }
    
    // GETTERS AND SETTERS    
    

    public File getBazelWorkspaceRootDirectory() {
        return this.bazelWorkspaceRootDirectory;
    }

    public boolean hasBazelWorkspaceRootDirectory() {
        return this.bazelWorkspaceRootDirectory != null;
    }

    public File getBazelExecRootDirectory() {
        if (this.bazelExecRootDirectory == null && metadataStrategy != null) {
            this.bazelExecRootDirectory = metadataStrategy.getBazelWorkspaceExecRoot();
        }
        return this.bazelExecRootDirectory;
    }

    public File getBazelOutputBaseDirectory() {
        if (this.bazelOutputBaseDirectory == null && metadataStrategy != null) {
            this.bazelOutputBaseDirectory = metadataStrategy.getBazelWorkspaceOutputBase();
        }
        return this.bazelOutputBaseDirectory;
    }

    public File getBazelBinDirectory() {
        if (this.bazelBinDirectory == null && metadataStrategy != null) {
            this.bazelBinDirectory = metadataStrategy.getBazelWorkspaceBin();
        }
        return this.bazelBinDirectory;
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public String getOperatingSystemFoldername() {
        return operatingSystemFoldername;
    }

}
