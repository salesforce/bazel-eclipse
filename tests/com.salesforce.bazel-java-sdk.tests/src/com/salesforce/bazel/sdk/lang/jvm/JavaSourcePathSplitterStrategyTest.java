package com.salesforce.bazel.sdk.lang.jvm;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.path.SplitSourcePath;
import com.salesforce.bazel.sdk.workspace.test.TestJavaFileCreator;

public class JavaSourcePathSplitterStrategyTest {

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Test
    public void testHappyPath() throws Exception {
        File appleDir = tmpDir.newFolder("apple");
        File sourceDir = new File(appleDir, "source");
        File javaDir = new File(sourceDir, "java");
        File comDir = new File(javaDir, "com");
        File sfdcDir = new File(comDir, "salesforce");
        File fooDir = new File(sfdcDir, "foo");
        fooDir.mkdirs();
        File barClass = new File(fooDir, "Bar.java");
        TestJavaFileCreator.createJavaSourceFile(barClass, "com.salesforce.foo", "Bar");

        JavaSourcePathSplitterStrategy splitter = new JavaSourcePathSplitterStrategy();
        String sourcePath = FSPathHelper.osSeps("source/java/com/salesforce/foo/Bar.java");
        SplitSourcePath split = splitter.splitSourcePath(appleDir, sourcePath);

        assertEquals(FSPathHelper.osSeps("source/java"), split.sourceDirectoryPath);
        assertEquals(FSPathHelper.osSeps("com/salesforce/foo/Bar.java"), split.filePath);
    }

    @Test
    public void testMismatchPackage() throws Exception {
        File appleDir = tmpDir.newFolder("apple");
        File sourceDir = new File(appleDir, "source");
        File javaDir = new File(sourceDir, "java");
        File comDir = new File(javaDir, "com");
        File sfdcDir = new File(comDir, "salesforce");
        File fooDir = new File(sfdcDir, "foo");
        fooDir.mkdirs();
        File barClass = new File(fooDir, "Bar.java");

        // package in the file is c.s.wrong, but the file path is c.s.foo
        TestJavaFileCreator.createJavaSourceFile(barClass, "com.salesforce.wrong", "Bar");

        JavaSourcePathSplitterStrategy splitter = new JavaSourcePathSplitterStrategy();
        String sourcePath = FSPathHelper.osSeps("source/java/com/salesforce/foo/Bar.java");
        SplitSourcePath split = splitter.splitSourcePath(appleDir, sourcePath);

        // splitter just takes entire file path as the source dir path
        assertEquals(FSPathHelper.osSeps("source/java/com/salesforce/foo"), split.sourceDirectoryPath);
        assertEquals("Bar.java", split.filePath);
    }

    @Test
    public void testDefaultPackage() throws Exception {
        File appleDir = tmpDir.newFolder("apple");
        File sourceDir = new File(appleDir, "source");
        File javaDir = new File(sourceDir, "java");
        javaDir.mkdirs();
        File barClass = new File(javaDir, "Bar.java");

        // package in the file is c.s.wrong, but the file path is c.s.foo
        TestJavaFileCreator.createJavaSourceFileInDefaultPackage(barClass, "Bar");

        JavaSourcePathSplitterStrategy splitter = new JavaSourcePathSplitterStrategy();
        SplitSourcePath split =
                splitter.splitSourcePath(appleDir, "source" + File.separator + "java" + File.separator + "Bar.java");

        assertEquals("source" + File.separator + "java", split.sourceDirectoryPath);
        assertEquals("Bar.java", split.filePath);
    }
}
