package com.salesforce.bazel.sdk.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandOptions;

public class BazelWorkspace {
    
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
    
    /**
     * List of Bazel command options that apply for all workspace commands (i.e. from .bazelrc) 
     */
    private BazelWorkspaceCommandOptions commandOptions;

    
    // CTORS AND INITIALIZERS

    public BazelWorkspace(String name, File bazelWorkspaceRootDirectory, OperatingEnvironmentDetectionStrategy osEnvStrategy,
            BazelWorkspaceMetadataStrategy metadataStrategy) {
        this(name, bazelWorkspaceRootDirectory, osEnvStrategy);
        this.metadataStrategy = metadataStrategy;
    }

    public BazelWorkspace(String name, File bazelWorkspaceRootDirectory, OperatingEnvironmentDetectionStrategy osEnvStrategy) {
        this.name = name;
        this.bazelWorkspaceRootDirectory = getCanonicalFileSafely(bazelWorkspaceRootDirectory);
        this.operatingSystem = osEnvStrategy.getOperatingSystemName();
        this.operatingSystemFoldername = osEnvStrategy.getOperatingSystemDirectoryName(this.operatingSystem);
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
            ioe.printStackTrace();
        }
        return directory;
    }
    
    // GETTERS AND SETTERS    


    public File getBazelWorkspaceRootDirectory() {
        return this.bazelWorkspaceRootDirectory;
    }

    public boolean hasBazelWorkspaceRootDirectory() {
        return this.bazelWorkspaceRootDirectory != null;
    }
    
    public String getName() {
        return this.name;
    }

    public File getBazelExecRootDirectory() {
        if (this.bazelExecRootDirectory == null && metadataStrategy != null) {
            this.bazelExecRootDirectory = metadataStrategy.computeBazelWorkspaceExecRoot();
        }
        return this.bazelExecRootDirectory;
    }

    public File getBazelOutputBaseDirectory() {
        if (this.bazelOutputBaseDirectory == null && metadataStrategy != null) {
            this.bazelOutputBaseDirectory = metadataStrategy.computeBazelWorkspaceOutputBase();
        }
        return this.bazelOutputBaseDirectory;
    }
    
    public List<String> getTargetsForBazelQuery(String query) {
    	List<String> results = new ArrayList<String>();
    	for(String line: metadataStrategy.computeBazelQuery(query)) {
    		if (line.startsWith("//")) {
                results.add(line);
            }
    	}
    	return results;
    }

    public File getBazelBinDirectory() {
        if (this.bazelBinDirectory == null && metadataStrategy != null) {
            this.bazelBinDirectory = metadataStrategy.computeBazelWorkspaceBin();
        }
        return this.bazelBinDirectory;
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public String getOperatingSystemFoldername() {
        return operatingSystemFoldername;
    }

    public BazelWorkspaceCommandOptions getBazelWorkspaceCommandOptions() {
        if (this.commandOptions == null) {
            this.commandOptions = new BazelWorkspaceCommandOptions(this);
            metadataStrategy.populateBazelWorkspaceCommandOptions(this.commandOptions);
        }
        return this.commandOptions;
    }
}
