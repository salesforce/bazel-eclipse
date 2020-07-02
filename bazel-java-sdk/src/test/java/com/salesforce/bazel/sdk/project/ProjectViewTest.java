package com.salesforce.bazel.sdk.project;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.salesforce.bazel.sdk.model.BazelPackageLocation;
import com.salesforce.bazel.sdk.project.ProjectView;
import com.salesforce.bazel.sdk.project.ProjectViewPackageLocation;

public class ProjectViewTest {
    
    @Test
    public void testBuildProjectViewFromScratch() {
        List<BazelPackageLocation> packages = new ArrayList<>();
        packages.add(getPackage("a/b/c"));
        packages.add(getPackage("d/e/f"));
        StringBuilder expectedContent = new StringBuilder();
        expectedContent.append("directories:").append(System.lineSeparator());
        expectedContent.append(ProjectView.INDENT).append(ProjectView.DIRECTORIES_COMMENT).append(System.lineSeparator());
        expectedContent.append(ProjectView.INDENT).append("a/b/c").append(System.lineSeparator());
        expectedContent.append(ProjectView.INDENT).append("d/e/f").append(System.lineSeparator());
        
        ProjectView projectView = new ProjectView(new File(""), packages);
        
        assertEquals(expectedContent.toString(), projectView.getContent());        
    }
    
    @Test
    public void testParseProjectViewFile() {
        File root = new File(".");
        StringBuilder content = new StringBuilder();
        content.append("directories:").append(System.lineSeparator());
        content.append(ProjectView.INDENT).append(ProjectView.DIRECTORIES_COMMENT).append(System.lineSeparator());
        content.append(ProjectView.INDENT).append("a/b/c").append(System.lineSeparator());
        content.append(ProjectView.INDENT).append("d/e/f").append(System.lineSeparator());
        
        ProjectView projectView = new ProjectView(root, content.toString());
        List<BazelPackageLocation> packages = projectView.getPackages();
        
        assertEquals(2, packages.size());
        
        assertEquals("a/b/c", packages.get(0).getBazelPackageFSRelativePath());
        assertEquals("c", packages.get(0).getBazelPackageNameLastSegment());
        assertEquals("d/e/f", packages.get(1).getBazelPackageFSRelativePath());
        assertEquals("f", packages.get(1).getBazelPackageNameLastSegment());
        for (BazelPackageLocation pack : packages) {
            assertSame(root, pack.getWorkspaceRootDirectory());
            assertFalse(pack.isWorkspaceRoot());
        }
    }
    
    private BazelPackageLocation getPackage(String path) {
        return new ProjectViewPackageLocation(new File(""), path) {
            @Override public String getBazelPackageNameLastSegment() { throw new AssertionError(); }
            @Override public File getWorkspaceRootDirectory() { throw new AssertionError(); }
            @Override public boolean isWorkspaceRoot() { throw new AssertionError(); }
        };
    }
}
