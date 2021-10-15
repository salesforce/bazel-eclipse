package com.salesforce.bazel.sdk.lang.jvm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.salesforce.bazel.sdk.index.jvm.jar.JarIdentifier;
import com.salesforce.bazel.sdk.lang.jvm.external.MavenInstallExternalJarRuleType;
import com.salesforce.bazel.sdk.model.BazelWorkspace;
import com.salesforce.bazel.sdk.model.test.MockBazelWorkspaceMetadataStrategy;
import com.salesforce.bazel.sdk.path.FSPathHelper;
import com.salesforce.bazel.sdk.workspace.BazelWorkspaceMetadataStrategy;
import com.salesforce.bazel.sdk.workspace.OperatingEnvironmentDetectionStrategy;
import com.salesforce.bazel.sdk.workspace.RealOperatingEnvironmentDetectionStrategy;


public class MavenInstallExternalJarRuleTypeTest {

    // we use a matrix build in CI to test Mac, Linux, and Windows so we can use a real OS here not a mock one
    OperatingEnvironmentDetectionStrategy osEnvStrategy = new RealOperatingEnvironmentDetectionStrategy();
    BazelWorkspaceMetadataStrategy metadataStrategy = null;
    BazelWorkspace bazelWorkspace = null;
    MavenInstallExternalJarRuleType classUnderTest = null;

    @Before
    public void setup() throws Exception {
        Path testDir = Files.createTempDirectory("bzl-mvninstall-test-");
        File workspaceDir = new File(testDir.toFile(), "bzl-test-ws");
        File outputDir = new File(testDir.toFile(), "bzl-test-output");
        metadataStrategy =
                new MockBazelWorkspaceMetadataStrategy("testWorkspace", workspaceDir, outputDir, osEnvStrategy);
        bazelWorkspace = new BazelWorkspace("testWorkspace", workspaceDir, osEnvStrategy, metadataStrategy);
        classUnderTest = new MavenInstallExternalJarRuleType(osEnvStrategy);
    }

    @Test
    public void testMavenInstall() throws Exception {
        TestJarPaths jarPaths = populateBazelBin();
        assertTrue(classUnderTest.isUsedInWorkspace(bazelWorkspace));

        List<File> paths = classUnderTest.getDownloadedJarLocations(bazelWorkspace);
        assertEquals(4, paths.size());
        // bazel-bin
        assertTrue(findFilePath(paths, FSPathHelper.osSeps("bin/external/maven")) != null);
        assertTrue(findFilePath(paths, FSPathHelper.osSeps("bin/external/deprecated")) != null);
        // exec root
        assertTrue(findFilePath(paths, FSPathHelper.osSeps("bzl-test-output/external/maven")) != null);
        assertTrue(findFilePath(paths, FSPathHelper.osSeps("bzl-test-output/external/deprecated")) != null);

        // ownership
        assertTrue(classUnderTest.doesBelongToRuleType(bazelWorkspace, jarPaths.guavaJar.getAbsolutePath()));
        assertTrue(classUnderTest.doesBelongToRuleType(bazelWorkspace, jarPaths.slf4jJar.getAbsolutePath()));
        assertTrue(classUnderTest.doesBelongToRuleType(bazelWorkspace, jarPaths.guavaJarDeprecated.getAbsolutePath()));
        File otherJarFile = new File(jarPaths.bazelBinDir, "fake.jar");
        assertFalse(classUnderTest.doesBelongToRuleType(bazelWorkspace, otherJarFile.getAbsolutePath()));

        // labels
        JarIdentifier jarId = new JarIdentifier("com.google.guava", "guava", "30.1-jre");
        assertEquals("@maven//:com_google_guava_guava",
            classUnderTest.deriveBazelLabel(bazelWorkspace, jarPaths.guavaJar.getAbsolutePath(), jarId));
        jarId = new JarIdentifier("com.google.guava", "guava", "23.0-jre");
        assertEquals("@deprecated//:com_google_guava_guava",
            classUnderTest.deriveBazelLabel(bazelWorkspace, jarPaths.guavaJarDeprecated.getAbsolutePath(), jarId));
    }


    // HELPERS

    private TestJarPaths populateBazelBin() throws Exception {
        TestJarPaths paths = new TestJarPaths();
        paths.bazelBinDir = bazelWorkspace.getBazelBinDirectory();
        paths.bazelBinDir.mkdirs();

        // create the output dir for a maven_install rule named 'maven'
        File externalDir = new File(paths.bazelBinDir, "external/maven/v1/https/ourinternalrepo.com/path/public");
        externalDir.mkdirs();

        // Guava
        // bazel-bin/maven/v1/https/ourinternalrepo.com/path/public/com/google/guava/guava/30.1-jre/guava-30.1-jre.jar
        File guavaDir = new File(externalDir, "com/google/guava/guava/30.1-jre");
        guavaDir.mkdirs();
        paths.guavaJar = new File(guavaDir, "guava-30.1-jre.jar");
        paths.guavaJar.createNewFile();

        // SLF4J
        // ./v1/https/nexus-proxy-prd.soma.salesforce.com/nexus/content/groups/public/org/slf4j/slf4j-api/1.7.32/slf4j-api-1.7.32.jar
        File slf4jDir = new File(externalDir, "org/slf4j/slf4j-api/1.7.32");
        slf4jDir.mkdirs();
        paths.slf4jJar = new File(slf4jDir, "slf4j-api-1.7.32.jar");
        paths.slf4jJar.createNewFile();

        // DEPRECATED maven_install

        externalDir = new File(paths.bazelBinDir, "external/deprecated/v1/https/ourinternalrepo.com/path/public");
        externalDir.mkdirs();

        // Guava
        // bazel-bin/maven/v1/https/ourinternalrepo.com/path/public/com/google/guava/guava/30.1-jre/guava-30.1-jre.jar
        guavaDir = new File(externalDir, "com/google/guava/guava/23.0-jre");
        guavaDir.mkdirs();
        paths.guavaJarDeprecated = new File(guavaDir, "guava-23.0-jre.jar");
        paths.guavaJarDeprecated.createNewFile();

        return paths;
    }

    private String findFilePath(List<File> paths, String endsWithPathName) {
        for (File path : paths) {
            String pathName = path.getAbsolutePath();
            if (pathName.endsWith(endsWithPathName)) {
                return pathName;
            }
        }
        return null;
    }

    private static class TestJarPaths {
        File bazelBinDir;
        public File guavaJar;
        public File slf4jJar;
        public File guavaJarDeprecated;
    }
}
