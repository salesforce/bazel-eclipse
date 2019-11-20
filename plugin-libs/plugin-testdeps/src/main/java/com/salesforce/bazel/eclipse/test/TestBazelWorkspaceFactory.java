package com.salesforce.bazel.eclipse.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
    
    public String workspaceName = "test_workspace";

    // directories
    public final File dirWorkspaceRoot; // provided by test, will contain WORKSPACE and subdirs will have .java files and BUILD files
    public final File dirOutputBase;    // provided by test
    public File dirOutputBaseExternal;  // [outputbase]/external
    public File dirExecRootParent;      // [outputbase]/execroot
    public File dirExecRoot;            // [outputbase]/execroot/test_workspace
    public File dirOutputPath;          // [outputbase]/execroot/test_workspace/bazel-out
    public File dirOutputPathPlatform;  // [outputbase]/execroot/test_workspace/bazel-out/darwin-fastbuild
    public File dirBazelBin;            // [outputbase]/execroot/test_workspace/bazel-out/darwin-fastbuild/bin
    public File dirBazelTestLogs;       // [outputbase]/execroot/test_workspace/bazel-out/darwin-fastbuild/testlogs

    private int numberJavaPackages = 0;
    private int numberGenrulePackages = 0;
    
    public Map<String, File> createdPackages = new TreeMap<>();
    // map of package path (projects/libs/javalib0) to the set of absolute paths for the aspect files for the package and deps
    public Map<String, Set<String>> aspectFileSets= new TreeMap<>();
    
    /**
     * Locations to write the assets for the simulated workspace. Both locations should be empty,
     * and the directories must exist.
     * 
     * @param workspaceRootDirectory  where the workspace files will be, this includes the WORKSPACE file and .java files
     * @param outputBaseDirectory  this is where simulated output is located, like generated .json aspect files
     */
    public TestBazelWorkspaceFactory(File workspaceRootDirectory, File outputBaseDirectory) {
        this.dirWorkspaceRoot = workspaceRootDirectory;
        this.dirOutputBase = outputBaseDirectory;
    }

    /**
     * Locations to write the assets for the simulated workspace. Both locations should be empty,
     * and the directories must exist.
     * 
     * @param workspaceRootDirectory  where the workspace files will be, this includes the WORKSPACE file and .java files
     * @param outputBaseDirectory  this is where simulated output is located, like generated .json aspect files
     * @param workspaceName  underscored name of workspace, will appear in directory paths in outputBase
     */
    public TestBazelWorkspaceFactory(File workspaceRootDirectory, File outputBaseDirectory, String workspaceName) {
        this.dirWorkspaceRoot = workspaceRootDirectory;
        this.dirOutputBase = outputBaseDirectory;
        this.workspaceName = workspaceName;
    }

    
    public TestBazelWorkspaceFactory javaPackages(int count) {
        numberJavaPackages = count;
        return this;
    }

    public TestBazelWorkspaceFactory genrulePackages(int count) {
        numberGenrulePackages = count;
        return this;
    }
    
    public TestBazelWorkspaceFactory build() throws Exception {
        
        // Create the outputbase structure
        createOutputBaseStructure();
        
        // Create the Workspace structure
        File projectsDir = new File(dirWorkspaceRoot, "projects");
        projectsDir.mkdir();
        File libsDir = new File(projectsDir, "libs");
        libsDir.mkdir();
        String libsRelativePath = "projects/libs";
        
        // make the WORKSPACE file
        File workspaceFile = new File(dirWorkspaceRoot, "WORKSPACE");
        try {
            workspaceFile.createNewFile();
        } catch (Exception anyE) {
            System.err.println("Could not create the WORKSPACE file for the test Bazel workspace at location: "+workspaceFile.getAbsolutePath());
            anyE.printStackTrace();
            throw anyE;
        }
        
        String previousJavaLibTarget = null;
        String previousAspectFilePath = null;
        for (int i=0; i<numberJavaPackages; i++) {
            String packageName = "javalib"+i;
            String packageRelativePath = libsRelativePath+"/"+packageName;
            File javaLib = new File(libsDir, packageName);
            javaLib.mkdir();
            
            // we will be collecting locations of Aspect json files for this package
            Set<String> packageAspectFiles = new TreeSet<>();
            
            // create the BUILD file
            File buildFile = new File(javaLib, "BUILD");
            buildFile.createNewFile();
            TestJavaRuleCreator.createJavaBuildFile(buildFile, packageName, i);
            
            // main source
            List<String> sourceFiles = new ArrayList<>();
            String srcMainPath = "src/main/java/com/salesforce/fruit"+i;
            File javaSrcMainDir = new File(javaLib, srcMainPath);
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
            
            // main fruit source aspect
            String extraDep = previousJavaLibTarget != null ? "    \"//"+previousJavaLibTarget+"\",\n" : null;
            String aspectFilePath_mainsource = TestAspectFileCreator.createJavaAspectFile(dirOutputBase, packageRelativePath, 
                packageName, packageName, extraDep, sourceFiles, true);
            packageAspectFiles.add(aspectFilePath_mainsource);
            
            // add aspects for maven jars (just picked a couple of typical maven jars to use)
            String aspectFilePath_slf4j = TestAspectFileCreator.createJavaAspectFileForMavenJar(dirOutputBase, "org_slf4j_slf4j_api", "slf4j-api-1.7.25");
            packageAspectFiles.add(aspectFilePath_slf4j);
            String aspectFilePath_guava = TestAspectFileCreator.createJavaAspectFileForMavenJar(dirOutputBase, "com_google_guava_guava", "guava-20.0");
            packageAspectFiles.add(aspectFilePath_guava);

            // test source
            List<String> testSourceFiles = new ArrayList<>();
            String srcTestPath = "src/test/java/com/salesforce/fruit"+i;
            File javaSrcTestDir = new File(javaLib, srcTestPath);
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
            String aspectFilePath_testsource = TestAspectFileCreator.createJavaAspectFile(dirOutputBase, libsRelativePath+"/"+packageName, packageName, packageName, 
                null, testSourceFiles, false);
            packageAspectFiles.add(aspectFilePath_testsource);
            
            // add aspects for test maven jars
            String aspectFilePath_junit = TestAspectFileCreator.createJavaAspectFileForMavenJar(dirOutputBase, "junit_junit", "junit-4.12");
            packageAspectFiles.add(aspectFilePath_junit);
            String aspectFilePath_hamcrest = TestAspectFileCreator.createJavaAspectFileForMavenJar(dirOutputBase, "org_hamcrest_hamcrest_core", "hamcrest-core-1.3");
            packageAspectFiles.add(aspectFilePath_hamcrest);

            // we chain the libs together to test inter project deps
            // add the previous aspect file
            if (previousAspectFilePath != null) {
                packageAspectFiles.add(previousAspectFilePath);
            }
            // now save off our current lib target to add to the next
            previousJavaLibTarget = packageRelativePath+":"+packageName;
            previousAspectFilePath = aspectFilePath_mainsource;
            
            // finish
            createdPackages.put(packageName, javaLib);
            aspectFileSets.put(packageRelativePath, packageAspectFiles);
        }
        
        for (int i=0; i<numberGenrulePackages; i++) {
            String packageName = "genrulelib"+i;
            File genruleLib = new File(libsDir, packageName);
            genruleLib.mkdir();
            File buildFile = new File(genruleLib, "BUILD");
            buildFile.createNewFile();
            createGenruleBuildFile(buildFile, packageName, i);
            
            File shellScript = new File(genruleLib, "gocrazy"+i+".sh");
            shellScript.createNewFile();
            
            createdPackages.put(packageName, genruleLib);
        }        
        
        return this;
    }
    
    
    // OUTPUT BASE
    
    /**
     * When you do a 'bazel info' you will see the list of important directories located in the output_base directory.
     * This method creates this structure of directories.
     */
    public void createOutputBaseStructure() {
        dirOutputBaseExternal = new File(dirOutputBase, "external");
        dirOutputBaseExternal.mkdirs();
        dirExecRootParent = new File(dirOutputBase, "execroot"); // [outputbase]/execroot
        dirExecRootParent.mkdirs();
        dirExecRoot = new File(dirExecRootParent, workspaceName); // [outputbase]/execroot/test_workspace 
        dirExecRoot.mkdirs();
        dirOutputPath = new File(dirExecRoot, "bazel-out"); // [outputbase]/execroot/test_workspace/bazel-out
        dirOutputPath.mkdirs();
        dirOutputPathPlatform = new File(dirOutputPath, "darwin-fastbuild"); // [outputbase]/execroot/test_workspace/bazel-out/darwin-fastbuild
        dirOutputPathPlatform.mkdirs();
        
        dirBazelBin = new File(dirOutputPathPlatform, "bin"); // [outputbase]/execroot/test_workspace/bazel-out/darwin-fastbuild/bin
        dirBazelBin.mkdirs();
        dirBazelTestLogs = new File(dirOutputPathPlatform, "testlogs"); // [outputbase]/execroot/test_workspace/bazel-out/darwin-fastbuild/testlogs
        dirBazelTestLogs.mkdirs();
        
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
}
