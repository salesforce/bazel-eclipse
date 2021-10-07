package com.salesforce.bazel.sdk.project;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.salesforce.bazel.sdk.command.BazelWorkspaceCommandRunner;
import com.salesforce.bazel.sdk.graph.InMemoryPackageLocation;
import com.salesforce.bazel.sdk.init.JvmRuleInit;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.path.FSTree;
import com.salesforce.bazel.sdk.path.SplitSourcePath;
import com.salesforce.bazel.sdk.project.structure.BazelQueryProjectStructureStrategy;
import com.salesforce.bazel.sdk.project.structure.ProjectStructure;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.workspace.RealOperatingEnvironmentDetectionStrategy;

public class BazelQueryProjectStructureStrategyTest {
    private static BazelWorkspace bazelWorkspace;

    // we mock out our collaborators that use the command runner
    private final BazelWorkspaceCommandRunner nullCommandRunner = null;

    @ClassRule
    public static TemporaryFolder tmpDir = new TemporaryFolder();

    @BeforeClass
    public static void setup() throws Exception {
        // recognize .java files as source files
        JvmRuleInit.initialize();

        OperatingEnvironmentDetectionStrategy osStrategy = new RealOperatingEnvironmentDetectionStrategy();
        bazelWorkspace = new BazelWorkspace("test-ws", tmpDir.newFolder(), osStrategy);

    }

    @Test
    public void testHappyPath() {
        // to make this a unit test, we need to mock out the computations that would ordinarily involve collaborators
        // such as Bazel Query, filesystem scanning, and file parsing
        TestBazelQueryProjectStructureStrategy strategy = new TestBazelQueryProjectStructureStrategy();
        addSourcePathForTest(strategy, "source/main/java");
        addSourcePathForTest(strategy, "source/test/java");
        addMainResourcePathForTest(strategy, "projects/libs/apple/source/main/resources");
        addTestResourcePathForTest(strategy, "projects/libs/apple/source/test/resources");

        addSimQueryResult(strategy, "source/main/java/com/salesforce/apple/api/Apple.java");
        addSimQueryResult(strategy, "source/main/java/com/salesforce/apple/api/ApplePie.java");
        addSimQueryResult(strategy, "source/main/resources/fruit/kinds.properties");
        addSimQueryResult(strategy, "source/main/resources/recipes/pies.properties");
        addSimQueryResult(strategy, "source/main/resources/recipes/cakes.properties");
        addSimQueryResult(strategy, "source/main/resources/recipes/sauces.properties");
        addSimQueryResult(strategy, "source/test/java/com/salesforce/apple/api/AppleTest.java");
        addSimQueryResult(strategy, "source/test/java/com/salesforce/apple/api/ApplePieTest.java");
        addSimQueryResult(strategy, "source/test/resources/test.properties");

        // run the test
        String relPath = FSPathHelper.osSeps("projects/libs/apple");
        InMemoryPackageLocation packageLocation = new InMemoryPackageLocation(relPath);
        ProjectStructure structure = null;
        try {
            structure = strategy.doStructureAnalysis(bazelWorkspace, packageLocation, nullCommandRunner);
        } catch (Exception anyE) {
            anyE.printStackTrace();
        }

        // validate
        assertNotNull(structure);
        assertContains(structure.mainSourceDirFSPaths, "projects/libs/apple/source/main/java");
        assertContains(structure.mainSourceDirFSPaths, "projects/libs/apple/source/main/resources");
        assertContains(structure.testSourceDirFSPaths, "projects/libs/apple/source/test/java");
        assertContains(structure.testSourceDirFSPaths, "projects/libs/apple/source/test/resources");
    }

    @Test
    public void testMultipleSourceDirs() {
        // to make this a unit test, we need to mock out the computations that would ordinarily involve collaborators
        // such as Bazel Query, filesystem scanning, and file parsing
        TestBazelQueryProjectStructureStrategy strategy = new TestBazelQueryProjectStructureStrategy();
        addSourcePathForTest(strategy, "source/dev/java");
        addSourcePathForTest(strategy, "source/dev2/java");
        addSourcePathForTest(strategy, "source/test/java");
        addSourcePathForTest(strategy, "src/test/java");
        addMainResourcePathForTest(strategy, "projects/libs/apple/source/dev/resources/fruit");
        addMainResourcePathForTest(strategy, "projects/libs/apple/source/dev2/resources");
        addTestResourcePathForTest(strategy, "projects/libs/apple/source/test/resources");

        addSimQueryResult(strategy, "source/dev/java/com/salesforce/apple/api/Apple.java");
        addSimQueryResult(strategy, "source/dev2/java/com/salesforce/apple/api/ApplePie.java");
        addSimQueryResult(strategy, "source/dev/resources/fruit/kinds.properties");
        addSimQueryResult(strategy, "source/dev2/resources/recipes/pies.properties");
        addSimQueryResult(strategy, "source/dev2/resources/recipes/cakes.properties");
        addSimQueryResult(strategy, "source/dev2/resources/recipes/sauces.properties");
        addSimQueryResult(strategy, "source/test/java/com/salesforce/apple/api/AppleTest.java");
        addSimQueryResult(strategy, "src/test/java/com/salesforce/apple/api/ApplePieTest.java");
        addSimQueryResult(strategy, "source/test/resources/test.properties");

        // run the test
        String relPath = FSPathHelper.osSeps("projects/libs/apple");
        InMemoryPackageLocation packageLocation = new InMemoryPackageLocation(relPath);
        ProjectStructure structure = strategy.doStructureAnalysis(bazelWorkspace, packageLocation, nullCommandRunner);

        // validate
        assertNotNull(structure);
        assertContains(structure.mainSourceDirFSPaths, "projects/libs/apple/source/dev/java");
        assertContains(structure.mainSourceDirFSPaths, "projects/libs/apple/source/dev2/java");
        assertContains(structure.mainSourceDirFSPaths, "projects/libs/apple/source/dev/resources/fruit");
        assertContains(structure.mainSourceDirFSPaths, "projects/libs/apple/source/dev2/resources");
        assertContains(structure.testSourceDirFSPaths, "projects/libs/apple/source/test/java");
        assertContains(structure.testSourceDirFSPaths, "projects/libs/apple/src/test/java");
        assertContains(structure.testSourceDirFSPaths, "projects/libs/apple/source/test/resources");
    }

    // INTERNALS

    private void addSourcePathForTest(TestBazelQueryProjectStructureStrategy strategy, String unixPath) {
        strategy.sourcePathsForThisTest.add(FSPathHelper.osSeps(unixPath));
    }

    private void addMainResourcePathForTest(TestBazelQueryProjectStructureStrategy strategy, String unixPath) {
        strategy.mainResourcePathsForThisTest.add(FSPathHelper.osSeps(unixPath));
    }

    private void addTestResourcePathForTest(TestBazelQueryProjectStructureStrategy strategy, String unixPath) {
        strategy.testResourcePathsForThisTest.add(FSPathHelper.osSeps(unixPath));
    }

    private void addSimQueryResult(TestBazelQueryProjectStructureStrategy strategy, String unixPath) {
        strategy.queryResults.add(FSPathHelper.osSeps(unixPath));
    }

    private void assertContains(List<String> list, String unixPath) {
        assertTrue(list.contains(FSPathHelper.osSeps(unixPath)));
    }

    /**
     * Subclass for class under test, to override collaborators.
     */
    private static class TestBazelQueryProjectStructureStrategy extends BazelQueryProjectStructureStrategy {

        // to make this a unit test, we need to mock out the computations that would ordinarily involve collaborators
        // such as Bazel Query and filesystem scanning
        public Collection<String> queryResults = new ArrayList<>();
        public List<String> sourcePathsForThisTest = new ArrayList<>();
        public List<String> mainResourcePathsForThisTest = new ArrayList<>();
        public List<String> testResourcePathsForThisTest = new ArrayList<>();

        /**
         * Let the test set the expected Bazel query results, without running Bazel query.
         */
        @Override
        protected Collection<String> runBazelQueryForSourceFiles(File workspaceRootDir, BazelLabel packageLabel,
                BazelWorkspaceCommandRunner commandRunner) {
            // for these tests, we do not want to invoke the Mock Bazel Query infrastructure, we just simulate
            // the response here
            return queryResults;
        }

        @Override
        protected boolean doIgnoreFile(File packageDir, String srcPath) {
            // in the real class, this method does a File.exists() check, since we don't create files for these
            // tests we just pretend like the file exists
            return false;
        }

        /**
         * Doing this operation for real requires file system operations and reading the package name out of the source
         * file, that we dont want to do in a unit test.
         */
        @Override
        protected SplitSourcePath splitSourcePath(File packageDir, String srcPath) {
            // this is tested in JavaSourcePathSplitterStrategyTest, just simulate responses here
            for (String candidate : sourcePathsForThisTest) {
                if (srcPath.startsWith(candidate + File.separator)) {
                    SplitSourcePath split = new SplitSourcePath();
                    int dirPathLength = candidate.length();
                    split.sourceDirectoryPath = srcPath.substring(0, dirPathLength); // src/main/java
                    split.filePath = srcPath.substring(dirPathLength); // com/salesforce/Foo.java
                    return split;
                }
            }
            return null;
        }

        @Override
        protected void computeResourceDirectories(String bazelPackageFSRelativePath, ProjectStructure structure,
                FSTree otherSourcePaths) {
            // computing resource directories is tested elsewhere, just simulate response here
            structure.mainSourceDirFSPaths.addAll(mainResourcePathsForThisTest);
            structure.testSourceDirFSPaths.addAll(testResourcePathsForThisTest);
        }
    }
}
