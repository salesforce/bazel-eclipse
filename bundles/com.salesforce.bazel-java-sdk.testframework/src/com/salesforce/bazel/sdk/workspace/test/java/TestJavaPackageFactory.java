package com.salesforce.bazel.sdk.workspace.test.java;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.workspace.test.TestAspectFileCreator;
import com.salesforce.bazel.sdk.workspace.test.TestBazelPackageDescriptor;
import com.salesforce.bazel.sdk.workspace.test.TestBazelWorkspaceDescriptor;
import com.salesforce.bazel.sdk.workspace.test.TestOptions;

/**
 * Factory method for creating a test Java package in a test workspace (see TestBazelWorkspaceFactory)
 * <p>
 * Warning: in some contexts our tests run in parallel, so make sure to avoid any static variables in this framework
 * otherwise you can have tests writing files into the wrong test workspace.
 */
public class TestJavaPackageFactory {
    // incremental state; as we build projects we make dependency references to the previous one
    private String previousJavaLibTarget = null;
    private String previousAspectFilePath = null;

    // JAVA

    public void createFakeJavaPackage(TestBazelWorkspaceDescriptor workspaceDescriptor, String packageName,
            String packageRelativePath, File javaPackageDir, int index, boolean explicitJavaTestDeps,
            boolean addJavaImport, boolean addJavaBinary, boolean trackState) throws Exception {
        String packageRelativeBazelPath = packageRelativePath + "/" + packageName; // $SLASH_OK bazel path
        String packageRelativeFilePath = FSPathHelper.osSeps(packageRelativeBazelPath);
        javaPackageDir.mkdir();

        // create the catalog entries
        TestBazelPackageDescriptor packageDescriptor = new TestBazelPackageDescriptor(workspaceDescriptor,
                packageRelativeBazelPath, packageName, javaPackageDir, trackState);

        // we will be collecting locations of Aspect json files for this package
        Set<String> packageAspectFiles = new TreeSet<>();

        // we optionally add additional deps to the main java_library, this collects those
        List<String> extraDeps = new ArrayList<>();

        // create the BUILD file
        File buildFile = new File(javaPackageDir, workspaceDescriptor.buildFilename);
        buildFile.createNewFile();
        TestJavaRuleCreator.createJavaBuildFile(workspaceDescriptor, buildFile, packageDescriptor);

        // java_import
        if (addJavaImport) {
            addJavaImport(workspaceDescriptor, packageName, javaPackageDir, packageRelativeBazelPath,
                packageRelativeFilePath, packageAspectFiles, extraDeps);
        }

        // main source
        String javaPackageName = "com.salesforce.fruit" + index;
        List<String> sourceFiles = addMainSourceFiles(workspaceDescriptor, index, javaPackageName, packageName,
            javaPackageDir, packageRelativeBazelPath, packageRelativeFilePath);

        // make this package depend on the library from the previously created package
        if (previousJavaLibTarget != null) {
            extraDeps.add(previousJavaLibTarget);
        }

        // common convention is to use the packageName for the java_library target name
        String javaLibraryTargetName = packageName;
        String aspectFilePath_mainsource_library = TestAspectFileCreator.createJavaLibraryAspectFile(
            workspaceDescriptor.outputBaseDirectory, packageRelativeBazelPath, packageName, javaLibraryTargetName,
            extraDeps, sourceFiles, true, explicitJavaTestDeps);
        packageAspectFiles.add(aspectFilePath_mainsource_library);

        // add maven jars as dependencies of the java library
        addMavenInstallJarsAsDeps(workspaceDescriptor, packageAspectFiles);

        // test source
        String javaTestTargetName = javaLibraryTargetName + "Test";
        List<String> testSourceFiles = addTestSource(workspaceDescriptor, index, javaPackageName, packageName,
            packageRelativePath, javaPackageDir, packageRelativeBazelPath, packageRelativeFilePath,
            javaLibraryTargetName, javaTestTargetName, explicitJavaTestDeps, packageAspectFiles);

        // add aspects for test maven jars if we have explicit java test deps mode enabled
        if (explicitJavaTestDeps) {
            addExplicitTestJars(workspaceDescriptor, packageAspectFiles);
        }

        // java_binary
        if (addJavaBinary) {
            addJavaBinary(workspaceDescriptor, packageRelativeBazelPath);
        }

        // write fake jar files to the filesystem for this project
        createFakeProjectJars(workspaceDescriptor, packageRelativeFilePath, packageName, javaLibraryTargetName,
            javaTestTargetName);

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
            previousJavaLibTarget = packageRelativeBazelPath + BazelLabel.BAZEL_COLON + javaLibraryTargetName;
            previousAspectFilePath = aspectFilePath_mainsource_library;
        }
    }

    // PACKAGE FEATURE ADDERS

    private List<String> addMainSourceFiles(TestBazelWorkspaceDescriptor workspaceDescriptor, int index,
            String javaPackageName, String packageName, File javaPackageDir, String packageRelativeBazelPath,
            String packageRelativeFilePath) throws Exception {
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
        File javaFile1 = new File(javaSrcMainDir1, classname1 + ".java");
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

        return sourceFiles;
    }

    private List<String> addTestSource(TestBazelWorkspaceDescriptor workspaceDescriptor, int index,
            String javaPackageName, String packageName, String packageRelativePath, File javaPackageDir,
            String packageRelativeBazelPath, String packageRelativeFilePath, String javaLibraryTargetName,
            String javaTestTargetName, boolean explicitJavaTestDeps, Set<String> packageAspectFiles) throws Exception {
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

        // test resources
        String srcTestResourcesPath = FSPathHelper.osSeps(srcTestRoot1 + "/resources"); // $SLASH_OK
        File javaSrcTestResourcesDir = new File(javaPackageDir, srcTestResourcesPath);
        javaSrcTestResourcesDir.mkdirs();
        File testResourceFile = new File(javaSrcTestResourcesDir, "test.properties");
        testResourceFile.createNewFile();

        // test fruit source aspect TODO why
        String aspectFilePath_testsource = TestAspectFileCreator.createJavaTestAspectFile(
            workspaceDescriptor.outputBaseDirectory, packageRelativePath + "/" + packageName, packageName, // $SLASH_OK: bazel path
            javaTestTargetName, null, testSourceFiles, false, explicitJavaTestDeps);
        packageAspectFiles.add(aspectFilePath_testsource);

        return testSourceFiles;
    }

    // TODO we only need to do this once per workspace
    private void addMavenInstallJarsAsDeps(TestBazelWorkspaceDescriptor workspaceDescriptor,
            Set<String> packageAspectFiles) throws Exception {
        TestJarDescriptor jarDescriptor = createFakeMavenInstallJar(workspaceDescriptor.outputBaseDirectory,
            "org_slf4j_slf4j_api", "org.slf4j", "slf4j-api", "1.7.25");
        String aspectFilePath_slf4j = TestAspectFileCreator
                .createAspectFileForMavenInstallJar(workspaceDescriptor.outputBaseDirectory, jarDescriptor);
        packageAspectFiles.add(aspectFilePath_slf4j);
        jarDescriptor = createFakeMavenInstallJar(workspaceDescriptor.outputBaseDirectory, "com_google_guava_guava",
            "com.google.guava", "guava", "20.0");
        String aspectFilePath_guava = TestAspectFileCreator
                .createAspectFileForMavenInstallJar(workspaceDescriptor.outputBaseDirectory, jarDescriptor);
        packageAspectFiles.add(aspectFilePath_guava);

    }

    private void addExplicitTestJars(TestBazelWorkspaceDescriptor workspaceDescriptor, Set<String> packageAspectFiles)
            throws Exception {
        TestJarDescriptor jarDescriptor = createFakeMavenInstallJar(workspaceDescriptor.outputBaseDirectory,
            "junit_junit", "junit", "junit", "4.12");
        String aspectFilePath_junit = TestAspectFileCreator
                .createAspectFileForMavenInstallJar(workspaceDescriptor.outputBaseDirectory, jarDescriptor);
        packageAspectFiles.add(aspectFilePath_junit);

        jarDescriptor = createFakeMavenInstallJar(workspaceDescriptor.outputBaseDirectory, "org_hamcrest_hamcrest_core",
            "org.hamcrest", "hamcrest-core", "1.3");
        String aspectFilePath_hamcrest = TestAspectFileCreator
                .createAspectFileForMavenInstallJar(workspaceDescriptor.outputBaseDirectory, jarDescriptor);
        packageAspectFiles.add(aspectFilePath_hamcrest);
    }

    private void addJavaImport(TestBazelWorkspaceDescriptor workspaceDescriptor, String packageName,
            File javaPackageDir, String packageRelativeBazelPath, String packageRelativeFilePath,
            Set<String> packageAspectFiles, List<String> extraDeps) throws Exception {
        // sometimes developers just stick a .jar file in the source code repo within the package
        // this is legal, and supported by Bazel with the java_import rule
        // this creates a local file PKG/libs/liborange-4.5.6.jar
        // we do this first because this lib will be a dependency to the main source below
        String packageLibsDirName = "importlibs";
        File importLibsDir = new File(javaPackageDir, packageLibsDirName);
        importLibsDir.mkdirs();

        String relativeImportLibsDir =
                FSPathHelper.osSeps(packageRelativeFilePath + FSPathHelper.UNIX_SLASH + packageLibsDirName);

        TestJarDescriptor jarDescriptor =
                createFakeImportJars(javaPackageDir, packageRelativeFilePath, packageLibsDirName, "orange", "4.5.6");
        String aspectFilePath_import = TestAspectFileCreator.createJavaAspectFileForImportLocalJar(
            workspaceDescriptor.outputBaseDirectory, packageRelativeBazelPath, relativeImportLibsDir, jarDescriptor);
        packageAspectFiles.add(aspectFilePath_import);
        extraDeps.add(":orange");

        // create an unused java_import (nothing depends on it) to see how that affects the classpath (it shouldn't)
        jarDescriptor =
                createFakeImportJars(javaPackageDir, packageRelativeFilePath, packageLibsDirName, "unused", "2.3.4");
        aspectFilePath_import = TestAspectFileCreator.createJavaAspectFileForImportLocalJar(
            workspaceDescriptor.outputBaseDirectory, packageRelativeBazelPath, relativeImportLibsDir, jarDescriptor);
        packageAspectFiles.add(aspectFilePath_import);

    }

    private void addJavaBinary(TestBazelWorkspaceDescriptor workspaceDescriptor, String packageRelativeBazelPath)
            throws Exception {
        File binaryDir = new File(workspaceDescriptor.dirBazelBin, FSPathHelper.osSeps(packageRelativeBazelPath));
        binaryDir.mkdirs();
        String binaryFilename = TestOptions.JAVA_BINARY_TARGET_NAME;
        if (!FSPathHelper.isUnix) {
            binaryFilename = TestOptions.JAVA_BINARY_TARGET_NAME + ".exe";
        }
        File javaBinaryFile = new File(binaryDir, binaryFilename);
        javaBinaryFile.createNewFile();
        System.out.println("Created fake java_binary file: " + javaBinaryFile.getCanonicalPath());
    }

    // FILE CREATORS

    /**
     * Creates the jars (bin, src) on the file system to be used as maven_install deps in java rules
     */
    public TestJarDescriptor createFakeMavenInstallJar(File dirOutputBase, String bazelName, String groupNameWithDots,
            String artifactName, String version) throws IOException {
        TestJarDescriptor jarDescriptor = new TestJarDescriptor();
        String combinedName = artifactName + "-" + version;
        jarDescriptor.bazelName = bazelName; // org_slf4j_slf4j_api

        jarDescriptor.isMaven = true;
        jarDescriptor.groupNameWithDots = groupNameWithDots;
        jarDescriptor.artifactName = artifactName;
        jarDescriptor.version = version;

        jarDescriptor.jarFileName = combinedName + ".jar";
        String folderPath = groupNameWithDots.replace("\\.", File.separator) + File.separator + artifactName
                + File.separator + version;

        // create the relative dir path from output base
        String maveninstallPath = FSPathHelper.osSeps("external/maven/v1/https/repo1.maven.org/maven2/");
        File jarDir = new File(dirOutputBase, maveninstallPath + folderPath);
        jarDir.mkdirs();

        // create the jar
        jarDescriptor.jarRelativePath =
                FSPathHelper.osSeps(maveninstallPath + folderPath + "/" + jarDescriptor.jarFileName); // $SLASH_OK
        File fakeJar = new File(dirOutputBase, jarDescriptor.jarRelativePath);
        fakeJar.createNewFile();
        jarDescriptor.jarAbsolutePath = fakeJar.getAbsolutePath();
        System.out.println("Created fake jar file: " + fakeJar.getCanonicalPath());

        // create the source jar
        jarDescriptor.srcJarRelativePath =
                FSPathHelper.osSeps(maveninstallPath + folderPath + "/" + combinedName + "-sources.jar"); // $SLASH_OK
        File fakeSourceJar = new File(dirOutputBase, jarDescriptor.srcJarRelativePath);
        fakeSourceJar.createNewFile();
        jarDescriptor.srcJarAbsolutePath = fakeSourceJar.getAbsolutePath();

        return jarDescriptor;
    }

    /**
     * Creates the jars (bin, src) on the file system to be used in a java_import target
     */
    private TestJarDescriptor createFakeImportJars(File javaPackageDir, String packageRelativePath,
            String libRelativePath, String artifactName, String version) throws IOException {
        TestJarDescriptor jarDescriptor = new TestJarDescriptor();
        jarDescriptor.bazelName = artifactName;
        String combinedName = artifactName + "-" + version;

        File libDir = new File(javaPackageDir, libRelativePath);

        jarDescriptor.jarFileName = combinedName + ".jar";
        File fakeJar = new File(libDir, jarDescriptor.jarFileName);
        fakeJar.createNewFile();
        jarDescriptor.jarAbsolutePath = fakeJar.getAbsolutePath();
        jarDescriptor.jarRelativePath =
                packageRelativePath + File.separator + libRelativePath + File.separator + jarDescriptor.jarFileName;
        System.out.println("Created fake import jar file: " + fakeJar.getCanonicalPath());

        jarDescriptor.srcJarFileName = combinedName + "-src.jar";
        fakeJar = new File(libDir, jarDescriptor.srcJarFileName);
        fakeJar.createNewFile();
        jarDescriptor.srcJarAbsolutePath = fakeJar.getAbsolutePath();
        jarDescriptor.srcJarRelativePath =
                packageRelativePath + File.separator + libRelativePath + File.separator + jarDescriptor.srcJarFileName;

        return jarDescriptor;
    }

    /**
     * Creates the jars (bin, src) on the file system as if they were built by java_library rule
     */
    public TestJarDescriptor createFakeProjectJars(TestBazelWorkspaceDescriptor workspaceDescriptor,
            String packageRelativePath, String packageName, String targetName, String testTargetName)
            throws IOException {
        File packageBinDir = new File(workspaceDescriptor.dirBazelBin, packageRelativePath);
        packageBinDir.mkdirs(); // execroot/bazel_demo_simplejava_mvninstall/bazel-out/darwin-fastbuild/bin/projects/services/fruit-salad-service/fruit-salad
        TestJarDescriptor jarDescriptor = new TestJarDescriptor();

        jarDescriptor.bazelName = packageName; // fruit-salad
        if (!packageName.equals(targetName)) {
            jarDescriptor.bazelName = packageName + BazelLabel.BAZEL_COLON + targetName; // fruit-salad:mylib
        }
        jarDescriptor.jarFileName = "lib" + targetName + ".jar"; // libfruit-salad
        File fakeJar = new File(packageBinDir, jarDescriptor.jarFileName);
        jarDescriptor.jarRelativePath =
                FSPathHelper.osSeps(packageRelativePath + FSPathHelper.UNIX_SLASH + jarDescriptor.jarFileName);
        fakeJar.createNewFile();
        jarDescriptor.jarAbsolutePath = fakeJar.getAbsolutePath();
        System.out.println("Created fake jar file: " + fakeJar.getCanonicalPath());

        String interfacejar = "lib" + targetName + "-hjar.jar";
        fakeJar = new File(packageBinDir, interfacejar);
        fakeJar.createNewFile();
        jarDescriptor.interfaceJarAbsolutePath = fakeJar.getAbsolutePath();

        String sourcejar = "lib" + targetName + "-src.jar";
        fakeJar = new File(packageBinDir, sourcejar);
        jarDescriptor.srcJarRelativePath =
                FSPathHelper.osSeps(packageRelativePath + FSPathHelper.UNIX_SLASH + sourcejar);
        fakeJar.createNewFile();
        jarDescriptor.srcJarAbsolutePath = fakeJar.getAbsolutePath();

        String testjar = "lib" + testTargetName + ".jar";
        fakeJar = new File(packageBinDir, testjar);
        fakeJar.createNewFile();
        jarDescriptor.testJarAbsolutePath = fakeJar.getAbsolutePath();

        String testsourcejar = "lib" + testTargetName + "-src.jar";
        fakeJar = new File(packageBinDir, testsourcejar);
        fakeJar.createNewFile();
        jarDescriptor.srcTestJarAbsolutePath = fakeJar.getAbsolutePath();

        return jarDescriptor;
    }

}
