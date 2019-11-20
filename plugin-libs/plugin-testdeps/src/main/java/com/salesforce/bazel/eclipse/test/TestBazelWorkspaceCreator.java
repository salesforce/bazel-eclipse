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
 * to fool the Eclipse import/classpath logic.
 * 
 * @author plaird
 */
public class TestBazelWorkspaceCreator {
    
    private File workspaceRootDirectory = null;
    private File workspaceBinDirectory = null;

    private int numberJavaPackages = 0;
    private int numberGenrulePackages = 0;
    
    public Map<String, File> createdPackages = new TreeMap<>();
    // map of package path (projects/libs/javalib0) to the set of absolute paths for the aspect files for the package and deps
    public Map<String, Set<String>> aspectFileSets= new TreeMap<>();
    
    public TestBazelWorkspaceCreator(File workspaceRootDirectory, File workspaceBinDirectory) {
        this.workspaceRootDirectory = workspaceRootDirectory;
        this.workspaceBinDirectory = workspaceBinDirectory;
    }
    
    
    public TestBazelWorkspaceCreator javaPackages(int count) {
        numberJavaPackages = count;
        return this;
    }

    public TestBazelWorkspaceCreator genrulePackages(int count) {
        numberGenrulePackages = count;
        return this;
    }
    
    public TestBazelWorkspaceCreator build() throws Exception {
        
        File projectsDir = new File(workspaceRootDirectory, "projects");
        projectsDir.mkdir();
        File libsDir = new File(projectsDir, "libs");
        libsDir.mkdir();
        String libsRelativePath = "projects/libs";
        
        // make the WORKSPACE file
        File workspaceFile = new File(workspaceRootDirectory, "WORKSPACE");
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
            String aspectFilePath_mainsource = TestAspectFileCreator.createJavaAspectFile(workspaceBinDirectory, packageRelativePath, packageName, packageName, 
                extraDep, sourceFiles, true);
            packageAspectFiles.add(aspectFilePath_mainsource);
            
            // add aspects for maven jars
            String aspectFilePath_slf4j = TestAspectFileCreator.createJavaAspectFileForMavenJar(workspaceBinDirectory, "org_slf4j_slf4j_api", "slf4j-api-1.7.25");
            packageAspectFiles.add(aspectFilePath_slf4j);
            String aspectFilePath_guava = TestAspectFileCreator.createJavaAspectFileForMavenJar(workspaceBinDirectory, "com_google_guava_guava", "guava-20.0");
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
            String aspectFilePath_testsource = TestAspectFileCreator.createJavaAspectFile(workspaceBinDirectory, libsRelativePath+"/"+packageName, packageName, packageName, 
                null, testSourceFiles, false);
            packageAspectFiles.add(aspectFilePath_testsource);
            
            // add aspects for test maven jars
            String aspectFilePath_junit = TestAspectFileCreator.createJavaAspectFileForMavenJar(workspaceBinDirectory, "junit_junit", "junit-4.12");
            packageAspectFiles.add(aspectFilePath_junit);
            String aspectFilePath_hamcrest = TestAspectFileCreator.createJavaAspectFileForMavenJar(workspaceBinDirectory, "org_hamcrest_hamcrest_core", "hamcrest-core-1.3");
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
