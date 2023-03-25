package com.salesforce.bazel.sdk.path;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

/**
 * FSTree is a utility class that helps us reason about file system structures
 */
public class FSTreeTest {

    @Test
    public void happyTrees() {
        var rootNode = new FSTree();
        var a1 = new FSTree(rootNode, "a1");
        var a2 = new FSTree(rootNode, "a2");
        var a1b1 = new FSTree(a1, "b1");
        var a1b2 = new FSTree(a1, "b2");

        assertEquals("a2", a2.getPath(":"));
        assertEquals("a1:b1", a1b1.getPath(":"));
        assertEquals("a1:b2", a1b2.getPath(":"));
        assertEquals(2, a1.getChildrenCount());
    }

    @Test
    public void happyTrees2() {
        var rootNode = new FSTree();
        FSTree.addNode(rootNode, "a:b:c:d:e:f", ":", true);
        FSTree.addNode(rootNode, "a:b:c:d:g:h", ":", true);

        var a = rootNode.getChild("a");
        var b = a.getChild("b");
        var c = b.getChild("c");
        var d = c.getChild("d");
        assertEquals(2, d.getChildrenCount());
    }

    @Test
    public void meaningfulDirs() {
        var rootNode = new FSTree();
        FSTree.addNode(rootNode, "a:b:c:d:e:f", ":", true);
        FSTree.addNode(rootNode, "a:b:c:d:g:h", ":", true);
        FSTree.addNode(rootNode, "x:y:z1", ":", true);
        FSTree.addNode(rootNode, "x:y:z2", ":", true);

        // these should not be represented in the paths
        FSTree.addNode(rootNode, "nope:leafdir", ":", false);
        FSTree.addNode(rootNode, "rootfile.txt", ":", true);

        var dirs = FSTree.computeMeaningfulDirectories(rootNode, ":");
        assertEquals(2, dirs.size());
        assertEquals("a:b:c:d", dirs.get(0));
        assertEquals("x:y", dirs.get(1));
    }
}
