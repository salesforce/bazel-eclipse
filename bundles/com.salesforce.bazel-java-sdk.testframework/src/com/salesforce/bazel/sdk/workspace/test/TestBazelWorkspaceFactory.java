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
 * Framework entry class to generate a Bazel workspace and other artifacts on the filesystem. As of this writing, the
 * workspace won't actually build, it just fakes it enough to fool the Eclipse import/classpath logic that scans the
 * filesystem looking for particular files. There is a separate test layer in the BEF command plugin that simulates
 * Bazel command executions.
 * <p>
 * This class is designed to be subclassed for more exotic test cases which need more customized Bazel packages. See the
 * protected methods for ideas on where to subclass this to provide your own logic.
 * <p>
 * Warning: in some contexts our tests run in parallel, so make sure to avoid any static variables in this framework
 * otherwise you can have tests writing files into the wrong test workspace.
 */
public class TestBazelWorkspaceFactory {

    public TestBazelWorkspaceDescriptor workspaceDescriptor;

    protected File packageParentDir; // File object that points to the place to write packages (e.g. /home/mbenioff/ws/projects/libs)
    protected String packageParentDirBazelRelPath; //  projects/libs (separator is always slash)

    protected TestJavaPackageFactory javaPackageFactory = new TestJavaPackageFactory();

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
     * <p>
     * If you need to create a test workspace with unique features, subclassing this class and swapping out the
     * implementation of one or more of the methods below will be the right solution.
     * <p>
     * Information from the build will be written back into the descriptor. Major errors will throw a RuntimeException
     * and expect the test to fail.
     */
    public void build() throws Exception {

        // Create the outputbase structure
        createOutputBaseStructure();

        // Create the Workspace structure (e.g. //projects/libs directory)
        createWorkspaceStructure();

        // make the WORKSPACE file
        createWorkspaceFile();

        // set this variable before we begin because the default logic is to set this to false
        // after one nested workspace is created (if enabled by the descriptor)
        doCreateNestedWorkspace = workspaceDescriptor.testOptions.addFakeNestedWorkspace;

        // JAVA
        int numJavaPackages = workspaceDescriptor.testOptions.numberOfJavaPackages;
        if (numJavaPackages > 0) {
            buildJavaWorkspaceElements();
            buildJavaPackages();
        }

        // GENRULE
        int numGenrulePackages = workspaceDescriptor.testOptions.numberGenrulePackages;
        if (numGenrulePackages > 0) {
            buildGenruleWorkspaceElements();
            buildGenrulePackages();
        }
    }

    // STANDARD WORKSPACE FILES

    /**
     * Hook for creating directories on the file system where the packages will go.
     */
    protected void createWorkspaceStructure() throws Exception {
        File projectsDir = new File(workspaceDescriptor.workspaceRootDirectory, "projects");
        projectsDir.mkdir();
        packageParentDir = new File(projectsDir, "libs");
        packageParentDir.mkdir();
        packageParentDirBazelRelPath = "projects/libs";
    }

    /**
     * Hook for creating the WORKSPACE file (and any additional files you want)
     */
    protected void createWorkspaceFile() throws Exception {
        File workspaceFile =
                new File(workspaceDescriptor.workspaceRootDirectory, workspaceDescriptor.workspaceFilename);
        try {
            // default impl here is just create an empty file
            workspaceFile.createNewFile();
        } catch (Exception anyE) {
            System.err.println("Could not create the WORKSPACE file for the test Bazel workspace at location: "
                    + workspaceFile.getAbsolutePath());
            anyE.printStackTrace();
            throw anyE;
        }
    }

    /**
     * When you do a 'bazel info' you will see the list of important directories located in the output_base directory.
     * This method creates this structure of directories.
     */
    protected void createOutputBaseStructure() throws Exception {
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

    // JAVA

    /**
     * Subclassable hook for creating all of the Java elements of the workspace.
     *
     * @param packageParentDir
     *            the //projects/libs directory on the file system, where you are expected to place the Bazel packages
     * @param packageParentDirBazelRelPath
     *            the relative path for the Bazel label (not File separated) for the Bazel packages (e.g. projects/libs)
     */
    protected void buildJavaWorkspaceElements() throws Exception {
        // creates the maven_install artifacts (actual jar files, and aspect files) for the workspace
        TestJavaWorkspaceCreator.createMavenInstallJars(workspaceDescriptor);

        // depending on configuration of the workspace, we may need to write out the TestRunner jar file
        TestJavaWorkspaceCreator.createTestRunner(workspaceDescriptor);
    }

    /**
     * Subclassable hook for creating all of the Java packages.
     *
     * @param packageParentDir
     *            the //projects/libs directory on the file system, where you are expected to place the Bazel packages
     * @param packageParentDirBazelRelPath
     *            the relative path for the Bazel label (not File separated) for the Bazel packages (e.g. projects/libs)
     */
    protected void buildJavaPackages() throws Exception {
        int numJavaPackages = workspaceDescriptor.testOptions.numberOfJavaPackages;

        for (int i = 0; i < numJavaPackages; i++) {

            // Do the heavy lifting to fully simulate a Java package with source, test source code
            String packageName = "javalib" + i;
            File javaPackageDir = new File(packageParentDir, packageName);
            javaPackageFactory.createJavaPackage(workspaceDescriptor, packageName, packageParentDirBazelRelPath,
                javaPackageDir, i, true);

            // in some cases, a test may want to create a nested workspace in one or more enclosing packages
            maybeBuildNestedWorkspace(javaPackageDir);
        }
    }

    // GENRULE

    /**
     * Subclassable hook for creating all of the Genrule elements of the workspace.
     *
     * @param packageParentDir
     *            the //projects/libs directory on the file system, where you are expected to place the Bazel packages
     * @param packageParentDirBazelRelPath
     *            the relative path for the Bazel label (not File separated) for the Bazel packages (e.g. projects/libs)
     */
    protected void buildGenruleWorkspaceElements() throws Exception {
        // Genrule usually does not require anything loaded into the WORKSPACE
    }

    /**
     * Subclassable hook for creating all of the Genrule packages.
     *
     * @param packageParentDir
     *            the //projects/libs directory on the file system, where you are expected to place the Bazel packages
     * @param packageParentDirBazelRelPath
     *            the relative path for the Bazel label (not File separated) for the Bazel packages (e.g. projects/libs)
     */
    protected void buildGenrulePackages() throws Exception {
        for (int i = 0; i < workspaceDescriptor.testOptions.numberGenrulePackages; i++) {
            String packageName = "genrulelib" + i;
            String packageRelativeBazelPath = packageParentDirBazelRelPath + "/" + packageName; // $SLASH_OK bazel path
            File genruleLib = new File(packageParentDir, packageName);
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
    }

    public static void createGenruleBuildFile(File buildFile, TestBazelPackageDescriptor packageDescriptor)
            throws Exception {
        try (PrintStream out = new PrintStream(new FileOutputStream(buildFile))) {
            String buildFileContents = createGenRule(packageDescriptor.packageName);
            new TestBazelTargetDescriptor(packageDescriptor, packageDescriptor.packageName, "genrule");

            out.print(buildFileContents);
        }
    }

    protected static String createGenRule(String packageName) {
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

    // NESTED WORKSPACES

    protected boolean doCreateNestedWorkspace = false;

    protected void maybeBuildNestedWorkspace(File enclosingPackageDir) throws Exception {

        // simulate a nested workspace to make sure we just ignore it for now (see BEF issue #25)
        // this nested workspace appears in the enclosing package, because in Bazel nested workspaces
        // can be placed anywhere. the default logic here in this method (subclass if you need something else)
        // is to create only one nested workspace, so set the state variable to false after the first

        if (doCreateNestedWorkspace) {
            // we just do this once per workspace, so we disable this flag when we do it
            doCreateNestedWorkspace = false;
            createNestedWorkspace(enclosingPackageDir);
        }
    }

    /**
     * Creates a nested workspace with a Java packages in it.
     */
    protected void createNestedWorkspace(File parentDir) throws Exception {
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
        File nestedJavaPackageDir = new File(nestedLibDir, packageName);

        // trackState is a little hard to explain, but it is used a means for the mocking framework
        // to chain packages together as dependencies of each other. since this nested workspace will
        // not be used (we dont support that yet), we do not want it to be used by other packages as a dep
        boolean trackState = false;

        // creates a java package in the libs dir of the nested workspace
        javaPackageFactory.createJavaPackage(workspaceDescriptor, packageName, packageRelativePath,
            nestedJavaPackageDir, 99, trackState);
    }
}
