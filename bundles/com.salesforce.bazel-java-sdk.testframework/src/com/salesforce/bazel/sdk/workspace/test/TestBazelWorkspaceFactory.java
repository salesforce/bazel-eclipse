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
import java.io.FileOutputStream;
import java.io.PrintStream;

import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.workspace.test.java.TestJavaPackageFactory;
import com.salesforce.bazel.sdk.workspace.test.java.TestJavaWorkspaceCreator;

/**
 * Utility class to generate a Bazel workspace and other artifacts on the filesystem. As of this writing, the workspace
 * won't actually build, it just fakes it enough to fool the Eclipse import/classpath logic that scans the filesystem
 * looking for particular files.
 * <p>
 * There is a separate test layer in the command plugin that simulates Bazel command executions.
 * <p>
 * Warning: in some contexts our tests run in parallel, so make sure to avoid any static variables in this framework
 * otherwise you can have tests writing files into the wrong test workspace.
 */
public class TestBazelWorkspaceFactory {

    public TestBazelWorkspaceDescriptor workspaceDescriptor;

    // CTORS

    /**
     * Constructor
     *
     * @param descriptor
     *            the model by which the workspace will be built
     */
    public TestBazelWorkspaceFactory(TestBazelWorkspaceDescriptor descriptor) {
        workspaceDescriptor = descriptor;
    }

    /**
     * Builds the test workspace on disk using the descriptor provided in the constructor.
     */
    public TestBazelWorkspaceFactory build() throws Exception {

        // Create the outputbase structure
        createOutputBaseStructure();

        // Create the Workspace structure
        File projectsDir = new File(workspaceDescriptor.workspaceRootDirectory, "projects");
        projectsDir.mkdir();
        File libsDir = new File(projectsDir, "libs");
        libsDir.mkdir();
        String libsRelativeBazelPath = "projects/libs"; // $SLASH_OK bazel path

        // make the WORKSPACE file
        File workspaceFile =
                new File(workspaceDescriptor.workspaceRootDirectory, workspaceDescriptor.workspaceFilename);
        try {
            workspaceFile.createNewFile();
        } catch (Exception anyE) {
            System.err.println("Could not create the WORKSPACE file for the test Bazel workspace at location: "
                    + workspaceFile.getAbsolutePath());
            anyE.printStackTrace();
            throw anyE;
        }

        boolean explicitJavaTestDeps = workspaceDescriptor.testOptions.explicitJavaTestDeps;
        boolean doCreateJavaImport = workspaceDescriptor.testOptions.addJavaImport;
        boolean doCreateNestedWorkspace = workspaceDescriptor.testOptions.addFakeNestedWorkspace;
        boolean doCreateJavaBinary = workspaceDescriptor.testOptions.addJavaBinary;

        int numJavaPackages = workspaceDescriptor.testOptions.numberOfJavaPackages;
        if (numJavaPackages > 0) {

            TestJavaWorkspaceCreator.createMavenInstallJars(workspaceDescriptor);

            if (!explicitJavaTestDeps) {
                // make the test runner jar file, because this workspace uses implicit deps (see ImplicitDependencyHelper)
                String testRunnerPath = FSPathHelper.osSeps(
                        "external/bazel_tools/tools/jdk/_ijar/TestRunner/external/remote_java_tools_linux/java_tools"); // $SLASH_OK
                File testRunnerDir = new File(workspaceDescriptor.dirBazelBin, testRunnerPath);
                testRunnerDir.mkdirs();
                File testRunnerJar = new File(testRunnerDir, "Runner_deploy-ijar.jar");
                try {
                    testRunnerJar.createNewFile();
                    System.out.println("TESTRUNNER: created at: " + testRunnerJar.getAbsolutePath());
                } catch (Exception anyE) {
                    System.err.println(
                        "Could not create the TestRunner jar file for the test Bazel workspace at location: "
                                + testRunnerJar.getAbsolutePath());
                    anyE.printStackTrace();
                    throw anyE;
                }
            }

            TestJavaPackageFactory javaPackageFactory = new TestJavaPackageFactory();
            for (int i = 0; i < numJavaPackages; i++) {

                // Do the heavy lifting to fully simulate a Java package with source, test source code
                String packageName = "javalib" + i;
                File javaPackageDir = new File(libsDir, packageName);
                javaPackageFactory.createJavaPackage(workspaceDescriptor, packageName, libsRelativeBazelPath,
                    javaPackageDir, i, explicitJavaTestDeps, doCreateJavaImport, doCreateJavaBinary, true);

                // simulate a nested workspace to make sure we just ignore it for now (see BEF issue #25)
                // this nested workspace appears in this java package, because in Bazel nested workspaces
                // can be placed anywhere
                if (doCreateNestedWorkspace) {
                    // we just do this once per workspace, so we disable this flag when we do it
                    doCreateNestedWorkspace = false;
                    createFakeNestedWorkspace(javaPackageDir, explicitJavaTestDeps, doCreateJavaImport);
                }

            }
        }

        for (int i = 0; i < workspaceDescriptor.testOptions.numberGenrulePackages; i++) {
            String packageName = "genrulelib" + i;
            String packageRelativeBazelPath = libsRelativeBazelPath + "/" + packageName; // $SLASH_OK bazel path
            File genruleLib = new File(libsDir, packageName);
            genruleLib.mkdir();

            // create the catalog entries
            TestBazelPackageDescriptor packageDescriptor = new TestBazelPackageDescriptor(workspaceDescriptor,
                packageRelativeBazelPath, packageName, genruleLib, true);

            File buildFile = new File(genruleLib, workspaceDescriptor.buildFilename);
            buildFile.createNewFile();
            createGenruleBuildFile(buildFile, packageDescriptor);

            File shellScript = new File(genruleLib, "gocrazy" + i + ".sh");
            if (!FSPathHelper.isUnix) {
                shellScript = new File(genruleLib, "gocrazy" + i + ".cmd");
            }
            shellScript.createNewFile();
        }

        return this;
    }

    // OUTPUT BASE

    /**
     * When you do a 'bazel info' you will see the list of important directories located in the output_base directory.
     * This method creates this structure of directories.
     */
    public void createOutputBaseStructure() {
        workspaceDescriptor.dirOutputBaseExternal = new File(workspaceDescriptor.outputBaseDirectory, "external");
        workspaceDescriptor.dirOutputBaseExternal.mkdirs();
        workspaceDescriptor.dirExecRootParent = new File(workspaceDescriptor.outputBaseDirectory, "execroot"); // [outputbase]/execroot
        workspaceDescriptor.dirExecRootParent.mkdirs();
        workspaceDescriptor.dirExecRoot =
                new File(workspaceDescriptor.dirExecRootParent, workspaceDescriptor.workspaceName); // [outputbase]/execroot/test_workspace
        workspaceDescriptor.dirExecRoot.mkdirs();
        workspaceDescriptor.dirOutputPath = new File(workspaceDescriptor.dirExecRoot, "bazel-out"); // [outputbase]/execroot/test_workspace/bazel-out
        workspaceDescriptor.dirOutputPath.mkdirs();
        workspaceDescriptor.dirOutputPathPlatform = new File(workspaceDescriptor.dirOutputPath, "darwin-fastbuild"); // [outputbase]/execroot/test_workspace/bazel-out/darwin-fastbuild
        workspaceDescriptor.dirOutputPathPlatform.mkdirs();

        workspaceDescriptor.dirBazelBin = new File(workspaceDescriptor.dirOutputPathPlatform, "bin"); // [outputbase]/execroot/test_workspace/bazel-out/darwin-fastbuild/bin
        workspaceDescriptor.dirBazelBin.mkdirs();

        workspaceDescriptor.dirBazelTestLogs = new File(workspaceDescriptor.dirOutputPathPlatform, "testlogs"); // [outputbase]/execroot/test_workspace/bazel-out/darwin-fastbuild/testlogs
        workspaceDescriptor.dirBazelTestLogs.mkdirs();

    }

    // GENRULE

    public static void createGenruleBuildFile(File buildFile, TestBazelPackageDescriptor packageDescriptor)
            throws Exception {
        try (PrintStream out = new PrintStream(new FileOutputStream(buildFile))) {
            String buildFileContents = createGenRule(packageDescriptor.packageName);
            new TestBazelTargetDescriptor(packageDescriptor, packageDescriptor.packageName, "genrule");

            out.print(buildFileContents);
        }
    }

    private static String createGenRule(String packageName) {
        StringBuffer sb = new StringBuffer();
        sb.append("genrule(\n   name=\""); // $SLASH_OK: escape char
        sb.append(packageName);
        sb.append("\",\n"); // $SLASH_OK: line continue
        sb.append("   tools = \"gocrazy.sh\",\n"); // $SLASH_OK: escape char
        if (FSPathHelper.isUnix) {
            sb.append("   cmd = \"./$(location gocrazy.sh) abc\",\n"); // $SLASH_OK
        } else {
            sb.append("   cmd = \"./$(location gocrazy.cmd) abc\",\n");
        }
        sb.append("   outs = \"bigmess.txt\",\n"); // $SLASH_OK: escape char
        sb.append(")");
        return sb.toString();
    }

    /**
     * Creates a nested workspace with a Java packages in it.
     */
    private void createFakeNestedWorkspace(File parentDir, boolean explicitJavaTestDeps, boolean addJavaImport)
            throws Exception {
        File nestedWorkspaceDir = new File(parentDir, "nested-workspace");
        nestedWorkspaceDir.mkdir();
        File nestedWorkspaceFile = new File(nestedWorkspaceDir, "WORKSPACE");
        nestedWorkspaceFile.createNewFile();
        File nestedWorkspaceBuildFile = new File(nestedWorkspaceDir, "BUILD");
        nestedWorkspaceBuildFile.createNewFile();

        String packageRelativePath = "libs";
        String packageName = "nestedJavaLib";
        File nestedLibDir = new File(nestedWorkspaceDir, packageRelativePath);
        nestedLibDir.mkdir();
        File nestedJavaPackage = new File(nestedLibDir, packageName);

        TestJavaPackageFactory javaPackageFactory = new TestJavaPackageFactory();
        javaPackageFactory.createJavaPackage(workspaceDescriptor, packageName, packageRelativePath,
            nestedJavaPackage, 99, explicitJavaTestDeps, addJavaImport, false, false);
    }
}
