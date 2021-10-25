/**
 * Copyright (c) 2021, Salesforce.com, Inc. All rights reserved.
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
package com.salesforce.bazel.sdk.workspace.test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.salesforce.bazel.sdk.workspace.test.java.TestJarDescriptor;

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

    // list of workspace external jars (via maven_install, etc)
    public Set<TestJarDescriptor> externalJarDescriptors = new TreeSet<>();

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
