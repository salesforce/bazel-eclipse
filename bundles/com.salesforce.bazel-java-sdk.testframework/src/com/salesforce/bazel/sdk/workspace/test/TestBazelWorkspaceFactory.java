package com.salesforce.bazel.sdk.workspace.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.salesforce.bazel.sdk.path.BazelPathHelper;

/**
 * Utility class to generate a Bazel workspace and other artifacts on the filesystem. As of this writing, the workspace
 * won't actually build, it just fakes it enough to fool the Eclipse import/classpath logic that scans the filesystem
 * looking for particular files.
 * <p>
 * There is a separate test layer in the command plugin that simulates Bazel command executions.
 *
 * @author plaird
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

        // make the test runner jar file, just in case a project in this workspace uses it (see ImplicitDependencyHelper)
        String testRunnerPath = BazelPathHelper
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
        boolean explicitJavaTestDeps = "true".equals(workspaceDescriptor.testOptions.get("EXPLICIT_JAVA_TEST_DEPS"));

        String previousJavaLibTarget = null;
        String previousAspectFilePath = null;
        for (int i = 0; i < workspaceDescriptor.numberJavaPackages; i++) {
            String packageName = "javalib" + i;
            String packageRelativeBazelPath = libsRelativeBazelPath + "/" + packageName; // $SLASH_OK bazel path
            String packageRelativeFilePath = BazelPathHelper.osSeps(packageRelativeBazelPath);
            File javaPackageDir = new File(libsDir, packageName);
            javaPackageDir.mkdir();

            // create the catalog entries
            TestBazelPackageDescriptor packageDescriptor = new TestBazelPackageDescriptor(workspaceDescriptor,
                    packageRelativeBazelPath, packageName, javaPackageDir);

            // we will be collecting locations of Aspect json files for this package
            Set<String> packageAspectFiles = new TreeSet<>();

            // create the BUILD file
            File buildFile = new File(javaPackageDir, workspaceDescriptor.buildFilename);
            buildFile.createNewFile();
            TestJavaRuleCreator.createJavaBuildFile(workspaceDescriptor, buildFile, packageDescriptor);

            // main source
            List<String> sourceFiles = new ArrayList<>();
            String srcMainPath = BazelPathHelper.osSeps("src/main/java/com/salesforce/fruit" + i); // $SLASH_OK
            File javaSrcMainDir = new File(javaPackageDir, srcMainPath);
            javaSrcMainDir.mkdirs();
            // Apple.java
            File javaFile1 = new File(javaSrcMainDir, "Apple" + i + ".java");
            javaFile1.createNewFile();
            String appleSrc =
                    BazelPathHelper.osSeps(packageRelativeBazelPath + "/" + srcMainPath + "/Apple" + i + ".java"); // $SLASH_OK
            sourceFiles.add(appleSrc);
            // Banana.java
            File javaFile2 = new File(javaSrcMainDir, "Banana" + i + ".java");
            javaFile2.createNewFile();
            String bananaSrc =
                    BazelPathHelper.osSeps(packageRelativeBazelPath + "/" + srcMainPath + "/Banana" + i + ".java"); // $SLASH_OK
            sourceFiles.add(bananaSrc);

            // main resources
            String srcMainResourcesPath = BazelPathHelper.osSeps("src/main/resources"); // $SLASH_OK
            File javaSrcMainResourcesDir = new File(javaPackageDir, srcMainResourcesPath);
            javaSrcMainResourcesDir.mkdirs();
            File resourceFile = new File(javaSrcMainResourcesDir, "main.properties");
            resourceFile.createNewFile();

            // main fruit source aspect
            String extraDep = previousJavaLibTarget != null ? "    \"//" + previousJavaLibTarget + "\",\n" : null; // $SLASH_OK: bazel path
            String aspectFilePath_mainsource = TestAspectFileCreator.createJavaAspectFile(
                workspaceDescriptor.outputBaseDirectory, packageRelativeBazelPath, packageName, packageName, extraDep,
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
            String srcTestPath = BazelPathHelper.osSeps("src/test/java/com/salesforce/fruit" + i); // $SLASH_OK
            File javaSrcTestDir = new File(javaPackageDir, srcTestPath);
            javaSrcTestDir.mkdirs();
            File javaTestFile1 = new File(javaSrcTestDir, "Apple" + i + "Test.java");
            javaTestFile1.createNewFile();
            String appleTestSrc =
                    BazelPathHelper.osSeps(packageRelativeBazelPath + "/" + srcTestPath + "/Apple" + i + "Test.java"); // $SLASH_OK
            testSourceFiles.add(appleTestSrc);
            File javaTestFile2 = new File(javaSrcTestDir, "Banana" + i + "Test.java");
            javaTestFile2.createNewFile();
            String bananaTestSrc =
                    BazelPathHelper.osSeps(packageRelativeBazelPath + "/" + srcTestPath + "/Banana" + i + "Test.java"); // $SLASH_OK
            testSourceFiles.add(bananaTestSrc);

            // test fruit source aspect
            String aspectFilePath_testsource = TestAspectFileCreator.createJavaAspectFile(
                workspaceDescriptor.outputBaseDirectory, libsRelativeBazelPath + "/" + packageName, packageName, // $SLASH_OK: bazel path
                packageName, null, testSourceFiles, false, explicitJavaTestDeps);
            packageAspectFiles.add(aspectFilePath_testsource);

            // test resources
            String srcTestResourcesPath = BazelPathHelper.osSeps("src/test/resources"); // $SLASH_OK
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

            // we chain the libs together to test inter project deps
            // add the previous aspect file
            if (previousAspectFilePath != null) {
                packageAspectFiles.add(previousAspectFilePath);
            }
            // now save off our current lib target to add to the next
            previousJavaLibTarget = packageRelativeBazelPath + BazelPathHelper.BAZEL_COLON + packageName;
            previousAspectFilePath = aspectFilePath_mainsource;

            // write fake jar files to the filesystem for this project
            createFakeProjectJars(packageRelativeFilePath, packageName);

            // finish
            workspaceDescriptor.aspectFileSets.put(packageRelativeBazelPath, packageAspectFiles);
        }

        for (int i = 0; i < workspaceDescriptor.numberGenrulePackages; i++) {
            String packageName = "genrulelib" + i;
            String packageRelativeBazelPath = libsRelativeBazelPath + "/" + packageName; // $SLASH_OK bazel path
            File genruleLib = new File(libsDir, packageName);
            genruleLib.mkdir();

            // create the catalog entries
            TestBazelPackageDescriptor packageDescriptor = new TestBazelPackageDescriptor(workspaceDescriptor,
                    packageRelativeBazelPath, packageName, genruleLib);

            File buildFile = new File(genruleLib, workspaceDescriptor.buildFilename);
            buildFile.createNewFile();
            createGenruleBuildFile(buildFile, packageDescriptor);

            File shellScript = new File(genruleLib, "gocrazy" + i + ".sh");
            if (!BazelPathHelper.isUnix) {
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
        if (BazelPathHelper.isUnix) {
            sb.append("   cmd = \"./$(location gocrazy.sh) abc\",\n"); // $SLASH_OK
        } else {
            sb.append("   cmd = \"./$(location gocrazy.cmd) abc\",\n");
        }
        sb.append("   outs = \"bigmess.txt\",\n"); // $SLASH_OK: escape char
        sb.append(")");
        return sb.toString();
    }

    private void createFakeExternalJars(File dirOutputBase, String foldername, String jarname) throws IOException {
        String fakeJarPath = BazelPathHelper.osSeps("external/" + foldername + "/jar/" + jarname + ".jar"); // $SLASH_OK
        File fakeJar = new File(dirOutputBase, fakeJarPath);
        fakeJar.createNewFile();

        String fakeSourceJarPath =
                BazelPathHelper.osSeps("external/" + foldername + "/jar/" + jarname + "-sources.jar"); // $SLASH_OK
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
}
