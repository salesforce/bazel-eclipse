package com.salesforce.bazel.sdk.project;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelPackageLocation;

public class ProjectViewTest {

    @Test
    public void testBuildProjectViewFromScratch_OnlyDirectories() {
        StringBuilder expectedContent = new StringBuilder();
        expectedContent.append(ProjectView.DIRECTORIES_SECTION).append(System.lineSeparator());
        expectedContent.append(ProjectView.INDENT).append(ProjectView.DIRECTORIES_COMMENT)
                .append(System.lineSeparator());
        expectedContent.append(ProjectView.INDENT).append("a/b/c").append(System.lineSeparator()); // $SLASH_OK bazel path
        expectedContent.append(ProjectView.INDENT).append("d/e/f").append(System.lineSeparator()); // $SLASH_OK bazel path
        List<BazelPackageLocation> packages = new ArrayList<>();
        packages.add(getPackage("a/b/c")); // $SLASH_OK bazel path
        packages.add(getPackage("d/e/f")); // $SLASH_OK bazel path

        ProjectView projectView = new ProjectView(new File(""), packages, Collections.emptyList());

        assertEquals(expectedContent.toString(), projectView.getContent());
    }

    @Test
    public void testBuildProjectViewFromScratch_DirectoriesAndTargets() {
        StringBuilder expectedContent = new StringBuilder();
        expectedContent.append(ProjectView.DIRECTORIES_SECTION).append(System.lineSeparator());
        expectedContent.append(ProjectView.INDENT).append(ProjectView.DIRECTORIES_COMMENT)
                .append(System.lineSeparator());
        expectedContent.append(ProjectView.INDENT).append("a/b/c").append(System.lineSeparator()); // $SLASH_OK bazel path
        expectedContent.append(System.lineSeparator());
        expectedContent.append(ProjectView.TARGETS_SECTION).append(System.lineSeparator());
        expectedContent.append(ProjectView.INDENT).append("//a/b/c:t1").append(System.lineSeparator()); // $SLASH_OK bazel path
        List<BazelPackageLocation> packages = Collections.singletonList(getPackage("a/b/c")); // $SLASH_OK bazel path
        List<BazelLabel> targets = Collections.singletonList(new BazelLabel("a/b/c:t1")); // $SLASH_OK bazel path

        ProjectView projectView = new ProjectView(new File(""), packages, targets);

        assertEquals(expectedContent.toString(), projectView.getContent());
    }

    @Test
    public void testGetDirectories() {
        File root = new File(".");
        StringBuilder content = new StringBuilder();
        content.append("directories:").append(System.lineSeparator());
        content.append(ProjectView.INDENT).append(ProjectView.DIRECTORIES_COMMENT).append(System.lineSeparator());
        content.append(ProjectView.INDENT).append("a/b/c").append(System.lineSeparator()); // $SLASH_OK bazel path
        content.append(ProjectView.INDENT).append("d/e/f").append(System.lineSeparator()); // $SLASH_OK bazel path
        content.append(ProjectView.TARGETS_SECTION).append(System.lineSeparator());
        content.append(ProjectView.INDENT).append("//a/b/c:t1").append(System.lineSeparator()); // $SLASH_OK bazel path

        ProjectView projectView = new ProjectView(root, content.toString());
        List<BazelPackageLocation> packages = projectView.getDirectories();

        assertEquals(2, packages.size());
        assertEquals("a/b/c", packages.get(0).getBazelPackageFSRelativePath()); // $SLASH_OK bazel path
        assertEquals(Collections.singletonList(new BazelLabel("a/b/c:t1")), packages.get(0).getBazelTargets()); // $SLASH_OK bazel path
        assertEquals("d/e/f", packages.get(1).getBazelPackageFSRelativePath()); // $SLASH_OK bazel path
        assertEquals(null, packages.get(1).getBazelTargets());
    }

    @Test
    public void testAddDefaultTargets() {
        List<BazelPackageLocation> packages = Collections.singletonList(getPackage("a/b/c")); // $SLASH_OK bazel path
        ProjectView projectView = new ProjectView(new File(""), packages, Collections.emptyList());

        projectView.addDefaultTargets();

        assertEquals(1, projectView.getTargets().size());
        assertEquals(new BazelLabel("a/b/c:*"), projectView.getTargets().iterator().next()); // $SLASH_OK bazel path
    }

    @Test
    public void testEquals1() {
        List<BazelPackageLocation> packages = Collections.singletonList(getPackage("a/b/c")); // $SLASH_OK bazel path
        List<BazelLabel> targets = Collections.singletonList(new BazelLabel("a/b/c:t1")); // $SLASH_OK bazel path

        ProjectView p1 = new ProjectView(new File("."), packages, targets);
        ProjectView p2 = new ProjectView(new File("."), packages, targets);

        assertEquals(p1, p2);
    }

    @Test
    public void testEquals2() {
        List<BazelPackageLocation> packages = Collections.singletonList(getPackage("a/b/c")); // $SLASH_OK bazel path
        List<BazelLabel> targets1 = Collections.singletonList(new BazelLabel("a/b/c:t1")); // $SLASH_OK bazel path
        List<BazelLabel> targets2 = Collections.singletonList(new BazelLabel("a/b/c:t2")); // $SLASH_OK bazel path

        ProjectView p1 = new ProjectView(new File("."), packages, targets1);
        ProjectView p2 = new ProjectView(new File("."), packages, targets2);

        assertNotEquals(p1, p2);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testUnmodifiableDirectories() {
        File root = new File(".");
        ProjectView projectView = new ProjectView(root, "");
        projectView.getDirectories().add(null);
    }

    private BazelPackageLocation getPackage(String path) {
        return new ProjectViewPackageLocation(new File(""), path) {
            @Override
            public String getBazelPackageNameLastSegment() {
                throw new AssertionError();
            }

            @Override
            public File getWorkspaceRootDirectory() {
                throw new AssertionError();
            }

            @Override
            public boolean isWorkspaceRoot() {
                throw new AssertionError();
            }
        };
    }
}
