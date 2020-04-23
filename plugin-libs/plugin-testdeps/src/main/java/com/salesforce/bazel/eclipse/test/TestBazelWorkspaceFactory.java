package com.salesforce.bazel.eclipse.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Utility class to generate a Bazel workspace and other artifacts on the filesystem.
 * As of this writing, the workspace won't actually build, it just fakes it enough
 * to fool the Eclipse import/classpath logic that scans the filesystem looking for 
 * particular files.
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
     * @param descriptor the model by which the workspace will be built
     */
    public TestBazelWorkspaceFactory(TestBazelWorkspaceDescriptor descriptor) {
        this.workspaceDescriptor = descriptor;
    }
    
    /**
     * Builds the test workspace on disk using the descriptor provided in the constructor.
     */
    public TestBazelWorkspaceFactory build() throws Exception {
        
        // Create the outputbase structure
        createOutputBaseStructure();
        
        // Create the Workspace structure
        File projectsDir = new File(this.workspaceDescriptor.workspaceRootDirectory, "projects");
        projectsDir.mkdir();
        File libsDir = new File(projectsDir, "libs");
        libsDir.mkdir();
        String libsRelativePath = "projects/libs";
        
        // make the WORKSPACE file
        File workspaceFile = new File(this.workspaceDescriptor.workspaceRootDirectory, this.workspaceDescriptor.workspaceFilename);
        try {
            workspaceFile.createNewFile();
        } catch (Exception anyE) {
            System.err.println("Could not create the WORKSPACE file for the test Bazel workspace at location: "+workspaceFile.getAbsolutePath());
            anyE.printStackTrace();
            throw anyE;
        }
        
        // make the test runner jar file, just in case a project in this workspace uses it (see ImplicitDependencyHelper)
        File testRunnerDir = new File(this.workspaceDescriptor.dirBazelBin, "external/bazel_tools/tools/jdk/_ijar/TestRunner/external/remote_java_tools_linux/java_tools");
        testRunnerDir.mkdirs();
        File testRunnerJar = new File(testRunnerDir, "Runner_deploy-ijar.jar");
        try {
            testRunnerJar.createNewFile();
            System.out.println("TESTRUNNER: created at: "+testRunnerJar.getAbsolutePath());
        } catch (Exception anyE) {
            System.err.println("Could not create the TestRunner jar file for the test Bazel workspace at location: "+testRunnerJar.getAbsolutePath());
            anyE.printStackTrace();
            throw anyE;
        }
        boolean explicitJavaTestDeps = "true".equals(this.workspaceDescriptor.commandOptions.get("explicit_java_test_deps"));
        
        String previousJavaLibTarget = null;
        String previousAspectFilePath = null;
        for (int i=0; i<this.workspaceDescriptor.numberJavaPackages; i++) {
            String packageName = "javalib"+i;
            String packageRelativePath = libsRelativePath+"/"+packageName;
            File javaPackageDir = new File(libsDir, packageName);
            javaPackageDir.mkdir();
            
            // we will be collecting locations of Aspect json files for this package
            Set<String> packageAspectFiles = new TreeSet<>();
            
            // create the BUILD file
            File buildFile = new File(javaPackageDir, this.workspaceDescriptor.buildFilename);
            buildFile.createNewFile();
            TestJavaRuleCreator.createJavaBuildFile(this.workspaceDescriptor.commandOptions, buildFile, packageName, i);
            
            // main source
            List<String> sourceFiles = new ArrayList<>();
            String srcMainPath = "src/main/java/com/salesforce/fruit"+i;
            File javaSrcMainDir = new File(javaPackageDir, srcMainPath);
            javaSrcMainDir.mkdirs();
            // Apple.java
            File javaFile1 = new File(javaSrcMainDir, "Apple"+i+".java");
            javaFile1.createNewFile();
            String appleSrc = packageRelativePath+"/"+srcMainPath+"/Apple"+i+".java";
            sourceFiles.add(appleSrc);
            // Banana.java
            File javaFile2 = new File(javaSrcMainDir, "Banana"+i+".java");
            javaFile2.createNewFile();
            String bananaSrc = packageRelativePath+"/"+srcMainPath+"/Banana"+i+".java";
            sourceFiles.add(bananaSrc);
            
            // main resources
            String srcMainResourcesPath = "src/main/resources";
            File javaSrcMainResourcesDir = new File(javaPackageDir, srcMainResourcesPath);
            javaSrcMainResourcesDir.mkdirs();
            File resourceFile = new File(javaSrcMainResourcesDir, "main.properties");
            resourceFile.createNewFile();

            // main fruit source aspect
            String extraDep = previousJavaLibTarget != null ? "    \"//"+previousJavaLibTarget+"\",\n" : null;
            String aspectFilePath_mainsource = TestAspectFileCreator.createJavaAspectFile(this.workspaceDescriptor.outputBaseDirectory, packageRelativePath, 
                packageName, packageName, extraDep, sourceFiles, true, explicitJavaTestDeps);
            packageAspectFiles.add(aspectFilePath_mainsource);
            
            // add aspects for maven jars (just picked a couple of typical maven jars to use)
            String aspectFilePath_slf4j = TestAspectFileCreator.createJavaAspectFileForMavenJar(this.workspaceDescriptor.outputBaseDirectory, "org_slf4j_slf4j_api", "slf4j-api-1.7.25");
            packageAspectFiles.add(aspectFilePath_slf4j);
            createFakeExternalJars(this.workspaceDescriptor.outputBaseDirectory, "org_slf4j_slf4j_api", "slf4j-api-1.7.25");
            String aspectFilePath_guava = TestAspectFileCreator.createJavaAspectFileForMavenJar(this.workspaceDescriptor.outputBaseDirectory, "com_google_guava_guava", "guava-20.0");
            packageAspectFiles.add(aspectFilePath_guava);
            createFakeExternalJars(this.workspaceDescriptor.outputBaseDirectory, "com_google_guava_guava", "guava-20.0");

            // test source
            List<String> testSourceFiles = new ArrayList<>();
            String srcTestPath = "src/test/java/com/salesforce/fruit"+i;
            File javaSrcTestDir = new File(javaPackageDir, srcTestPath);
            javaSrcTestDir.mkdirs();
            File javaTestFile1 = new File(javaSrcTestDir, "Apple"+i+"Test.java");
            javaTestFile1.createNewFile();
            String appleTestSrc = packageRelativePath+"/"+srcTestPath+"/Apple"+i+"Test.java";
            testSourceFiles.add(appleTestSrc);
            File javaTestFile2 = new File(javaSrcTestDir, "Banana"+i+"Test.java");
            javaTestFile2.createNewFile();
            String bananaTestSrc = packageRelativePath+"/"+srcTestPath+"/Banana"+i+"Test.java";
            testSourceFiles.add(bananaTestSrc);
            
            // test fruit source aspect
            String aspectFilePath_testsource = TestAspectFileCreator.createJavaAspectFile(this.workspaceDescriptor.outputBaseDirectory, libsRelativePath+"/"+packageName, 
                packageName, packageName, null, testSourceFiles, false, explicitJavaTestDeps);
            packageAspectFiles.add(aspectFilePath_testsource);
            
            // test resources
            String srcTestResourcesPath = "src/test/resources";
            File javaSrcTestResourcesDir = new File(javaPackageDir, srcTestResourcesPath);
            javaSrcTestResourcesDir.mkdirs();
            File testResourceFile = new File(javaSrcTestResourcesDir, "test.properties");
            testResourceFile.createNewFile();
            
            // add aspects for test maven jars if we have explicit java test deps mode enabled
            if (explicitJavaTestDeps) {
                String aspectFilePath_junit = TestAspectFileCreator.createJavaAspectFileForMavenJar(this.workspaceDescriptor.outputBaseDirectory, "junit_junit", "junit-4.12");
                packageAspectFiles.add(aspectFilePath_junit);
                createFakeExternalJars(this.workspaceDescriptor.outputBaseDirectory, "junit_junit", "junit-4.12");
                String aspectFilePath_hamcrest = TestAspectFileCreator.createJavaAspectFileForMavenJar(this.workspaceDescriptor.outputBaseDirectory, "org_hamcrest_hamcrest_core", "hamcrest-core-1.3");
                packageAspectFiles.add(aspectFilePath_hamcrest);
                createFakeExternalJars(this.workspaceDescriptor.outputBaseDirectory, "org_hamcrest_hamcrest_core", "hamcrest-core-1.3");
            }
            
            // we chain the libs together to test inter project deps
            // add the previous aspect file
            if (previousAspectFilePath != null) {
                packageAspectFiles.add(previousAspectFilePath);
            }
            // now save off our current lib target to add to the next
            previousJavaLibTarget = packageRelativePath+":"+packageName;
            previousAspectFilePath = aspectFilePath_mainsource;
            
            // write fake jar files to the filesystem for this project
            createFakeProjectJars(packageRelativePath, packageName);
            
            // finish
            this.workspaceDescriptor.createdPackages.put(packageName, javaPackageDir);
            this.workspaceDescriptor.aspectFileSets.put(packageRelativePath, packageAspectFiles);
        }
        
        for (int i=0; i<this.workspaceDescriptor.numberGenrulePackages; i++) {
            String packageName = "genrulelib"+i;
            File genruleLib = new File(libsDir, packageName);
            genruleLib.mkdir();
            File buildFile = new File(genruleLib, this.workspaceDescriptor.buildFilename);
            buildFile.createNewFile();
            createGenruleBuildFile(buildFile, packageName, i);
            
            File shellScript = new File(genruleLib, "gocrazy"+i+".sh");
            shellScript.createNewFile();
            
            this.workspaceDescriptor.createdPackages.put(packageName, genruleLib);
        }        
        
        return this;
    }
    
    
    // OUTPUT BASE
    
    /**
     * When you do a 'bazel info' you will see the list of important directories located in the output_base directory.
     * This method creates this structure of directories.
     */
    public void createOutputBaseStructure() {
        this.workspaceDescriptor.dirOutputBaseExternal = new File(this.workspaceDescriptor.outputBaseDirectory, "external");
        this.workspaceDescriptor.dirOutputBaseExternal.mkdirs();
        this.workspaceDescriptor.dirExecRootParent = new File(this.workspaceDescriptor.outputBaseDirectory, "execroot"); // [outputbase]/execroot
        this.workspaceDescriptor.dirExecRootParent.mkdirs();
        this.workspaceDescriptor.dirExecRoot = new File(this.workspaceDescriptor.dirExecRootParent, workspaceDescriptor.workspaceName); // [outputbase]/execroot/test_workspace 
        this.workspaceDescriptor.dirExecRoot.mkdirs();
        this.workspaceDescriptor.dirOutputPath = new File(this.workspaceDescriptor.dirExecRoot, "bazel-out"); // [outputbase]/execroot/test_workspace/bazel-out
        this.workspaceDescriptor.dirOutputPath.mkdirs();
        this.workspaceDescriptor.dirOutputPathPlatform = new File(this.workspaceDescriptor.dirOutputPath, "darwin-fastbuild"); // [outputbase]/execroot/test_workspace/bazel-out/darwin-fastbuild
        this.workspaceDescriptor.dirOutputPathPlatform.mkdirs();
        
        this.workspaceDescriptor.dirBazelBin = new File(this.workspaceDescriptor.dirOutputPathPlatform, "bin"); // [outputbase]/execroot/test_workspace/bazel-out/darwin-fastbuild/bin
        this.workspaceDescriptor.dirBazelBin.mkdirs();
        this.workspaceDescriptor.dirBazelTestLogs = new File(this.workspaceDescriptor.dirOutputPathPlatform, "testlogs"); // [outputbase]/execroot/test_workspace/bazel-out/darwin-fastbuild/testlogs
        this.workspaceDescriptor.dirBazelTestLogs.mkdirs();
        
    }
    

    // GENRULE
    
    public static void createGenruleBuildFile(File buildFile, String projectName, int projectIndex) throws Exception {
        try (PrintStream out = new PrintStream(new FileOutputStream(buildFile))) {
            
            String buildFileContents = createGenRule(projectName);
            out.print(buildFileContents);
        } 
    }
    
    private static String createGenRule(String projectName) {
        StringBuffer sb = new StringBuffer();
        sb.append("genrule(\n   name=\"");
        sb.append(projectName);
        sb.append("\",\n");
        sb.append("   tools = \"gocrazy.sh\",\n");
        sb.append("   cmd = \"./$(location gocrazy.sh) abc\",\n");
        sb.append("   outs = \"bigmess.txt\",\n");
        sb.append(")");
        return sb.toString();
    }
    
    private void createFakeExternalJars(File dirOutputBase, String foldername, String jarname) throws IOException {
        File fakeJar = new File(dirOutputBase, "external/"+foldername+"/jar/"+jarname+".jar");
        fakeJar.createNewFile();
        File fakeSourceJar = new File(dirOutputBase, "external/"+foldername+"/jar/"+jarname+"-sources.jar");
        fakeSourceJar.createNewFile();
    }
    
    private void createFakeProjectJars(String packageRelativePath, String packageName) throws IOException {
        File packageBinDir = new File(this.workspaceDescriptor.dirBazelBin, packageRelativePath);
        packageBinDir.mkdirs();
        
        String jar = "lib"+packageName+".jar";
        File fakeJar = new File(packageBinDir, jar);
        fakeJar.createNewFile();
        System.out.println("Created fake jar file: "+fakeJar.getCanonicalPath());
        
        String interfacejar = "lib"+packageName+"-hjar.jar";
        fakeJar = new File(packageBinDir, interfacejar);
        fakeJar.createNewFile();
        
        String sourcejar = "lib"+packageName+"-src.jar";
        fakeJar = new File(packageBinDir, sourcejar);
        fakeJar.createNewFile();
        
        String testjar = "lib"+packageName+"-test.jar";
        fakeJar = new File(packageBinDir, testjar);
        fakeJar.createNewFile();
        
        String testsourcejar = "lib"+packageName+"-test-src.jar";
        fakeJar = new File(packageBinDir, testsourcejar);
        fakeJar.createNewFile();
    }
}
