package com.salesforce.bazel.sdk.workspace.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.path.FSPathHelper;

/**
 * Utility class to generate a Bazel workspace and other artifacts on the filesystem. As of this writing, the workspace
 * won't actually build, it just fakes it enough to fool the Eclipse import/classpath logic that scans the filesystem
 * looking for particular files.
 * <p>
 * There is a separate test layer in the command plugin that simulates Bazel command executions.
 */
public class TestBazelWorkspaceFactory {

    public TestBazelWorkspaceDescriptor workspaceDescriptor;

    // incremental state; as we build projects we make dependency references to the previous one
    private String previousJavaLibTarget = null;
    private String previousAspectFilePath = null;

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

        // make the test runner jar file, just in case a project in this workspace uses it (see ImplicitDependencyHelper)
        String testRunnerPath = FSPathHelper
                .osSeps("external/bazel_tools/tools/jdk/_ijar/TestRunner/external/remote_java_tools_linux/java_tools"); // $SLASH_OK
        File testRunnerDir = new File(workspaceDescriptor.dirBazelBin, testRunnerPath);
        testRunnerDir.mkdirs();
        File testRunnerJar = new File(testRunnerDir, "Runner_deploy-ijar.jar");
        try {
            testRunnerJar.createNewFile();
            System.out.println("TESTRUNNER: created at: " + testRunnerJar.getAbsolutePath());
        } catch (Exception anyE) {
            System.err.println("Could not create the TestRunner jar file for the test Bazel workspace at location: "
                    + testRunnerJar.getAbsolutePath());
            anyE.printStackTrace();
            throw anyE;
        }
        boolean explicitJavaTestDeps = workspaceDescriptor.testOptions.explicitJavaTestDeps;

        boolean doCreateNestedWorkspace = workspaceDescriptor.testOptions.addFakeNestedWorkspace;
        for (int i = 0; i < workspaceDescriptor.testOptions.numberOfJavaPackages; i++) {

            // Do the heavy lifting to fully simulate a Java package with source, test source code
            String packageName = "javalib" + i;
            File javaPackageDir = new File(libsDir, packageName);
            createFakeJavaPackage(packageName, libsRelativeBazelPath, javaPackageDir, i, explicitJavaTestDeps, true);

            // simulate a nested workspace to make sure we just ignore it for now (see BEF issue #25)
            // this nested workspace appears in this java package, because in Bazel nested workspaces
            // can be placed anywhere
            if (doCreateNestedWorkspace) {
                // we just do this once per workspace, so we disable this flag when we do it
                doCreateNestedWorkspace = false;
                createFakeNestedWorkspace(javaPackageDir, explicitJavaTestDeps);
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

    // JAVA

    private void createFakeJavaPackage(String packageName, String packageRelativePath, File javaPackageDir, int index,
            boolean explicitJavaTestDeps, boolean trackState) throws Exception {
        String packageRelativeBazelPath = packageRelativePath + "/" + packageName; // $SLASH_OK bazel path
        String packageRelativeFilePath = FSPathHelper.osSeps(packageRelativeBazelPath);
        javaPackageDir.mkdir();

        // create the catalog entries
        TestBazelPackageDescriptor packageDescriptor = new TestBazelPackageDescriptor(workspaceDescriptor,
            packageRelativeBazelPath, packageName, javaPackageDir, trackState);

        // we will be collecting locations of Aspect json files for this package
        Set<String> packageAspectFiles = new TreeSet<>();

        // create the BUILD file
        File buildFile = new File(javaPackageDir, workspaceDescriptor.buildFilename);
        buildFile.createNewFile();
        TestJavaRuleCreator.createJavaBuildFile(workspaceDescriptor, buildFile, packageDescriptor);

        // main source
        List<String> sourceFiles = new ArrayList<>();
        String srcMainRoot1 = "src/main";
        String srcMainRoot2 = "src/main";
        if (workspaceDescriptor.testOptions.nonStandardJavaLayout_enabled) {
            srcMainRoot1 = "source/dev";
            srcMainRoot2 = "source/dev";
            if (workspaceDescriptor.testOptions.nonStandardJavaLayout_multipledirs) {
                // not only is the project using non standard layout, it has multiple source directories
                srcMainRoot2 = "source/dev2";
            }
        }

        String srcMainPath1 = FSPathHelper.osSeps(srcMainRoot1 + "/java/com/salesforce/fruit" + index); // $SLASH_OK
        String srcMainPath2 = FSPathHelper.osSeps(srcMainRoot2 + "/java/com/salesforce/fruit" + index); // $SLASH_OK

        File javaSrcMainDir1 = new File(javaPackageDir, srcMainPath1);
        File javaSrcMainDir2 = javaSrcMainDir1;
        javaSrcMainDir1.mkdirs();
        if (workspaceDescriptor.testOptions.nonStandardJavaLayout_multipledirs) {
            javaSrcMainDir2 = new File(javaPackageDir, srcMainPath2);
            javaSrcMainDir2.mkdirs();
        }

        // Apple.java
        String classname1 = "Apple" + index;
        String javaPackageName = "com.salesforce.fruit" + index;
        File javaFile1 = new File(javaSrcMainDir1, classname1);
        TestJavaFileCreator.createJavaSourceFile(javaFile1, javaPackageName, classname1);
        System.out.println("Created java file: " + javaFile1.getAbsolutePath());
        String appleSrc =
                FSPathHelper.osSeps(packageRelativeBazelPath + "/" + srcMainPath1 + "/" + classname1 + ".java"); // $SLASH_OK
        sourceFiles.add(appleSrc);

        // Banana.java
        String classname2 = "Banana" + index;
        File javaFile2 = new File(javaSrcMainDir2, classname2 + ".java");
        TestJavaFileCreator.createJavaSourceFile(javaFile2, javaPackageName, classname2);
        String bananaSrc =
                FSPathHelper.osSeps(packageRelativeBazelPath + "/" + srcMainPath2 + "/" + classname2 + ".java"); // $SLASH_OK
        sourceFiles.add(bananaSrc);

        // main resources
        String srcMainResourcesPath = FSPathHelper.osSeps(srcMainRoot1 + "/resources"); // $SLASH_OK
        File javaSrcMainResourcesDir = new File(javaPackageDir, srcMainResourcesPath);
        javaSrcMainResourcesDir.mkdirs();
        File resourceFile = new File(javaSrcMainResourcesDir, "main.properties");
        resourceFile.createNewFile();

        // main fruit source aspect
        List<String> extraDeps = new ArrayList<>();
        if (previousJavaLibTarget != null) {
            extraDeps.add(previousJavaLibTarget);
        }
        String aspectFilePath_mainsource = TestAspectFileCreator.createJavaAspectFile(
            workspaceDescriptor.outputBaseDirectory, packageRelativeBazelPath, packageName, packageName, extraDeps,
            sourceFiles, true, explicitJavaTestDeps);
        packageAspectFiles.add(aspectFilePath_mainsource);

        // add aspects for maven jars (just picked a couple of typical maven jars to use)
        String aspectFilePath_slf4j = TestAspectFileCreator.createJavaAspectFileForMavenJar(
            workspaceDescriptor.outputBaseDirectory, "org_slf4j_slf4j_api", "slf4j-api-1.7.25");
        packageAspectFiles.add(aspectFilePath_slf4j);
        createFakeExternalJars(workspaceDescriptor.outputBaseDirectory, "org_slf4j_slf4j_api", "slf4j-api-1.7.25");
        String aspectFilePath_guava = TestAspectFileCreator.createJavaAspectFileForMavenJar(
            workspaceDescriptor.outputBaseDirectory, "com_google_guava_guava", "guava-20.0");
        packageAspectFiles.add(aspectFilePath_guava);
        createFakeExternalJars(workspaceDescriptor.outputBaseDirectory, "com_google_guava_guava", "guava-20.0");

        // test source
        List<String> testSourceFiles = new ArrayList<>();
        String srcTestRoot1 = "src/test";
        String srcTestRoot2 = "src/test";
        if (workspaceDescriptor.testOptions.nonStandardJavaLayout_enabled) {
            srcTestRoot1 = "source/test";
            srcTestRoot2 = "source/test";
            if (workspaceDescriptor.testOptions.nonStandardJavaLayout_multipledirs) {
                // not only is the project using non standard layout, it has multiple source directories
                srcTestRoot2 = "source/test2";
            }
        }

        String srcTestPath1 = FSPathHelper.osSeps(srcTestRoot1 + "/java/com/salesforce/fruit" + index); // $SLASH_OK
        String srcTestPath2 = FSPathHelper.osSeps(srcTestRoot2 + "/java/com/salesforce/fruit" + index); // $SLASH_OK
        File javaSrcTestDir1 = new File(javaPackageDir, srcTestPath1);
        File javaSrcTestDir2 = javaSrcTestDir1;
        javaSrcTestDir1.mkdirs();
        if (workspaceDescriptor.testOptions.nonStandardJavaLayout_multipledirs) {
            javaSrcTestDir2 = new File(javaPackageDir, srcTestPath2);
            javaSrcTestDir2.mkdirs();
        }

        String tclassname1 = "Apple" + index + "Test";
        File javaTestFile1 = new File(javaSrcTestDir1, tclassname1 + ".java");
        TestJavaFileCreator.createJavaSourceFile(javaTestFile1, javaPackageName, tclassname1);
        String appleTestSrc =
                FSPathHelper.osSeps(packageRelativeBazelPath + "/" + srcTestPath1 + "/Apple" + index + "Test.java"); // $SLASH_OK
        testSourceFiles.add(appleTestSrc);

        String tclassname2 = "Banana" + index + "Test";
        File javaTestFile2 = new File(javaSrcTestDir2, tclassname2 + ".java");
        TestJavaFileCreator.createJavaSourceFile(javaTestFile2, javaPackageName, tclassname2);
        String bananaTestSrc =
                FSPathHelper.osSeps(packageRelativeBazelPath + "/" + srcTestPath2 + "/Banana" + index + "Test.java"); // $SLASH_OK
        testSourceFiles.add(bananaTestSrc);

        // test fruit source aspect
        String aspectFilePath_testsource = TestAspectFileCreator.createJavaAspectFile(
            workspaceDescriptor.outputBaseDirectory, packageRelativePath + "/" + packageName, packageName, // $SLASH_OK: bazel path
            packageName, null, testSourceFiles, false, explicitJavaTestDeps);
        packageAspectFiles.add(aspectFilePath_testsource);

        // test resources
        String srcTestResourcesPath = FSPathHelper.osSeps(srcTestRoot1 + "/resources"); // $SLASH_OK
        File javaSrcTestResourcesDir = new File(javaPackageDir, srcTestResourcesPath);
        javaSrcTestResourcesDir.mkdirs();
        File testResourceFile = new File(javaSrcTestResourcesDir, "test.properties");
        testResourceFile.createNewFile();

        // add aspects for test maven jars if we have explicit java test deps mode enabled
        if (explicitJavaTestDeps) {
            String aspectFilePath_junit = TestAspectFileCreator.createJavaAspectFileForMavenJar(
                workspaceDescriptor.outputBaseDirectory, "junit_junit", "junit-4.12");
            packageAspectFiles.add(aspectFilePath_junit);
            createFakeExternalJars(workspaceDescriptor.outputBaseDirectory, "junit_junit", "junit-4.12");
            String aspectFilePath_hamcrest = TestAspectFileCreator.createJavaAspectFileForMavenJar(
                workspaceDescriptor.outputBaseDirectory, "org_hamcrest_hamcrest_core", "hamcrest-core-1.3");
            packageAspectFiles.add(aspectFilePath_hamcrest);
            createFakeExternalJars(workspaceDescriptor.outputBaseDirectory, "org_hamcrest_hamcrest_core",
                    "hamcrest-core-1.3");
        }

        // write fake jar files to the filesystem for this project
        createFakeProjectJars(packageRelativeFilePath, packageName);

        // preserve created data for the package in the descriptor
        workspaceDescriptor.aspectFileSets.put(packageRelativeBazelPath, packageAspectFiles);
        workspaceDescriptor.createdMainSourceFilesForPackages.put(packageRelativeBazelPath, sourceFiles);
        workspaceDescriptor.createdTestSourceFilesForPackages.put(packageRelativeBazelPath, testSourceFiles);

        if (trackState) {
            // we chain the libs together to test inter project deps
            // we normally want to keep track of all the packages we have created, but in some test cases
            // we create Java packages that we don't expect to import (e.g. in a nested workspace that isn't
            // imported) in such cases trackState will be false

            // add the previous aspect file (I think this is unnecessary)
            if (previousAspectFilePath != null) {
                packageAspectFiles.add(previousAspectFilePath);
            }
            // now save off our current lib target to add to the next
            previousJavaLibTarget = packageRelativeBazelPath + BazelLabel.BAZEL_COLON + packageName;
            previousAspectFilePath = aspectFilePath_mainsource;
        }
    }

    private void createFakeExternalJars(File dirOutputBase, String foldername, String jarname) throws IOException {
        String fakeJarPath = FSPathHelper.osSeps("external/" + foldername + "/jar/" + jarname + ".jar"); // $SLASH_OK
        File fakeJar = new File(dirOutputBase, fakeJarPath);
        fakeJar.createNewFile();

        String fakeSourceJarPath =
                FSPathHelper.osSeps("external/" + foldername + "/jar/" + jarname + "-sources.jar"); // $SLASH_OK
        File fakeSourceJar = new File(dirOutputBase, fakeSourceJarPath);
        fakeSourceJar.createNewFile();
    }

    private void createFakeProjectJars(String packageRelativePath, String packageName) throws IOException {
        File packageBinDir = new File(workspaceDescriptor.dirBazelBin, packageRelativePath);
        packageBinDir.mkdirs();

        String jar = "lib" + packageName + ".jar";
        File fakeJar = new File(packageBinDir, jar);
        fakeJar.createNewFile();
        System.out.println("Created fake jar file: " + fakeJar.getCanonicalPath());

        String interfacejar = "lib" + packageName + "-hjar.jar";
        fakeJar = new File(packageBinDir, interfacejar);
        fakeJar.createNewFile();

        String sourcejar = "lib" + packageName + "-src.jar";
        fakeJar = new File(packageBinDir, sourcejar);
        fakeJar.createNewFile();

        String testjar = "lib" + packageName + "-test.jar";
        fakeJar = new File(packageBinDir, testjar);
        fakeJar.createNewFile();

        String testsourcejar = "lib" + packageName + "-test-src.jar";
        fakeJar = new File(packageBinDir, testsourcejar);
        fakeJar.createNewFile();
    }

    /**
     * Creates a nested workspace with a Java packages in it.
     */
    private void createFakeNestedWorkspace(File parentDir, boolean explicitJavaTestDeps) throws Exception {
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
        createFakeJavaPackage(packageName, packageRelativePath, nestedJavaPackage, 99, explicitJavaTestDeps,
            false);
    }

}
