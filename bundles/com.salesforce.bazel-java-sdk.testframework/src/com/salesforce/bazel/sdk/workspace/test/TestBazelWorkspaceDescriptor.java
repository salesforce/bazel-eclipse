package com.salesforce.bazel.sdk.workspace.test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Descriptor that describes the workspace to be built by the TestBazelWorkspaceFactory.
 */
public class TestBazelWorkspaceDescriptor {

    // INPUT FIELDS (the test specifies these)
    public File workspaceRootDirectory;
    public File outputBaseDirectory;
    public String workspaceName = "test_workspace";

    // names to use for the Bazel config files generated on disk
    public String workspaceFilename = "WORKSPACE"; // could also be WORKSPACE.bazel
    public String buildFilename = "BUILD"; // could also be BUILD.bazel

    // Instead of infinite parameters in the constructor, a bunch of options can be passed in via this map.
    // These get interpreted by various components of the Mock layer to alter mocking behavior.
    // See the TestOptions class for ways to find all the available options.
    public TestOptions testOptions = new TestOptions();

    // BUILT FIELDS (filled in after the workspace is built on disk)

    // computed directories
    public File dirOutputBaseExternal; // [outputbase]/external
    public File dirExecRootParent; // [outputbase]/execroot
    public File dirExecRoot; // [outputbase]/execroot/test_workspace
    public File dirOutputPath; // [outputbase]/execroot/test_workspace/bazel-out
    public File dirOutputPathPlatform; // [outputbase]/execroot/test_workspace/bazel-out/darwin-fastbuild
    public File dirBazelBin; // [outputbase]/execroot/test_workspace/bazel-out/darwin-fastbuild/bin
    public File dirBazelTestLogs; // [outputbase]/execroot/test_workspace/bazel-out/darwin-fastbuild/testlogs

    // WORKSPACE CATALOGS

    // map of package path (projects/libs/javalib0) to the directory containing the package on the file system
    public Map<String, TestBazelPackageDescriptor> createdPackages = new TreeMap<>();

    // map of package path (projects/libs/javalib0) to the list of source files (main and test) for the package
    public Map<String, List<String>> createdMainSourceFilesForPackages = new TreeMap<>();
    public Map<String, List<String>> createdTestSourceFilesForPackages = new TreeMap<>();

    public TestBazelPackageDescriptor getCreatedPackageByName(String packageName) {
        TestBazelPackageDescriptor desc = createdPackages.get(packageName);
        if (desc == null) {
            System.err.println("Test caused a package to be requested that does not exist: " + packageName);
        }
        return desc;
    }

    // map of target (projects/libs/javalib0:javalib0) to the package (projects/libs/javalib0)
    public Map<String, TestBazelTargetDescriptor> createdTargets = new TreeMap<>();

    // map of bazel package path (projects/libs/javalib0) to the set of absolute paths for the aspect files for the package and deps
    public Map<String, Set<String>> aspectFileSets = new TreeMap<>();

    // CTORS

    /**
     * Locations to write the assets for the simulated workspace. Both locations should be empty, and the directories
     * must exist.
     *
     * @param workspaceRootDirectory
     *            where the workspace files will be, this includes the WORKSPACE file and .java files
     * @param outputBaseDirectory
     *            this is where simulated output is located, like generated .json aspect files
     */
    public TestBazelWorkspaceDescriptor(File workspaceRootDirectory, File outputBaseDirectory) {
        this.workspaceRootDirectory = workspaceRootDirectory;
        this.outputBaseDirectory = outputBaseDirectory;
    }

    /**
     * Locations to write the assets for the simulated workspace. Both locations should be empty, and the directories
     * must exist.
     *
     * @param workspaceRootDirectory
     *            where the workspace files will be, this includes the WORKSPACE file and .java files
     * @param outputBaseDirectory
     *            this is where simulated output is located, like generated .json aspect files
     * @param workspaceName
     *            underscored name of workspace, will appear in directory paths in outputBase
     */
    public TestBazelWorkspaceDescriptor(File workspaceRootDirectory, File outputBaseDirectory, String workspaceName) {
        this.workspaceRootDirectory = workspaceRootDirectory;
        this.outputBaseDirectory = outputBaseDirectory;
        this.workspaceName = workspaceName;
    }

    // CONFIGURATION

    /**
     * List of options that allow you to create test workspaces with specific Mock features enabled. The features are
     * specific to each Mock*Command.
     */
    public TestBazelWorkspaceDescriptor testOptions(TestOptions options) {
        testOptions = options;

        if (options.useAltConfigFileNames) {
            workspaceFilename = "WORKSPACE.bazel";
            buildFilename = "BUILD.bazel";
        } else {
            workspaceFilename = "WORKSPACE";
            buildFilename = "BUILD";
        }

        return this;
    }

}
