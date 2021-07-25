/**
 * Copyright (c) 2021, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.bazel.sdk.path;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * File system tree implementation that is focused on mapping out interesting paths in a file system tree.
 */
public class FSTree implements Comparable<FSTree> {

    private FSTree parent = null;
    private boolean isRoot = false;
    private final String name;
    private final String logicalPath;
    private final Set<FSTree> children = new TreeSet<>();
    private boolean isFile = false;

    /**
     * Creates a root node.
     */
    public FSTree() {
        name = "";
        logicalPath = "";
        isRoot = true;
    }

    /**
     * Creates a node into an existing tree.
     */
    public FSTree(FSTree parent, String name) {
        this.parent = parent;
        this.name = name;
        logicalPath = parent.logicalPath + ":" + name;
        this.parent.children.add(this);
    }

    /**
     * Gets the parent node. If this is the root node, this will return null.
     */
    public FSTree getParent() {
        return parent;
    }

    /**
     * Gets the iterable of children for this node.
     */
    public Iterable<FSTree> getChildren() {
        return children;
    }

    /**
     * Returns the number of children under this node.
     */
    public int getChildrenCount() {
        return children.size();
    }

    /**
     * Gets the child of this node with the provided name, or null if not found.
     */
    public FSTree getChild(String searchName) {
        for (FSTree child : children) {
            if (searchName.equals(child.name)) {
                return child;
            }
        }
        return null;
    }

    /**
     * Does this node represent a file (as opposed to a directory)?
     */
    public boolean isFile() {
        return isFile;
    }

    /**
     * Is this node the root node?
     */
    public boolean isRoot() {
        return isRoot;
    }

    /**
     * Gets the path to this node, using the passed delimiter. E.g. a/b/c/d
     */
    public String getPath(String delimiter) {
        if (parent == null) {
            return "";
        }
        String path = null;
        if (parent.isRoot) {
            path = name;
        } else {
            path = parent.getPath(delimiter) + delimiter + name;
        }
        return path;
    }

    // STATIC HELPERS

    /**
     * Adds a node to an existing FSTree and wires it up to the correct parent node.
     */
    public static void addNode(FSTree rootNode, String path, String delimiter, boolean isFile) {
        String[] pathTokens = path.split(delimiter);

        FSTree currentNode = rootNode;
        for (String pathToken : pathTokens) {
            FSTree nextNode = currentNode.getChild(pathToken);
            if (nextNode == null) {
                nextNode = new FSTree(currentNode, pathToken);
            }
            currentNode = nextNode;
        }
        currentNode.isFile = isFile;
    }

    /**
     * Translates a File tree on the filesystem into an FSTree object.
     */
    public static FSTree translate(File rootFile) {
        FSTree rootNode = new FSTree();

        if (rootFile.exists()) {
            translateRecur(rootFile, rootNode);
        }

        return rootNode;
    }

    private static void translateRecur(File currentFile, FSTree currentNode) {
        File[] fileChildren = currentFile.listFiles();
        for (File child : fileChildren) {
            boolean isFile = child.isFile();
            FSTree childNode = new FSTree(currentNode, child.getName());
            if (isFile) {
                childNode.isFile = true;
            } else {
                translateRecur(child, childNode);
            }
        }
    }

    /**
     * Traverses the tree and identifies "meaningful" directories. This is not an exact science. It includes:
     * <ul>
     * <li>For each subdirectory in the root node, at least one path will be in the result.</li>
     * <li>For each file in the root node, the file will be ignored.</li>
     * <li>It will stop descending at any directory that has more than one child (files + subdirs) and add that path to
     * the list.</li>
     * <li>If we reach the leaf of a branch and there is just one file, the parent directory path is added to the
     * list.</li>
     * <li>If we reach the leaf of a branch and it is a directory, the path is ignored.</li>
     * </ul>
     * <p>
     * The returned list of paths are in lexographic order.
     */
    public static List<String> computeMeaningfulDirectories(FSTree otherSourcePaths, String pathDelimiter) {
        List<String> paths = new ArrayList<>();
        // - For each child directory in the root of the project directory..
        // - Find the first subdirectory that contains more than one child
        // - Make that a source folder
        for (FSTree child : otherSourcePaths.getChildren()) {
            computeMeaningfulDirectoriesRecur(paths, child, pathDelimiter);
        }
        return paths;
    }

    private static void computeMeaningfulDirectoriesRecur(List<String> paths, FSTree currentNode,
            String pathDelimiter) {
        if (currentNode.getChildrenCount() > 1) {
            String path = currentNode.getPath(pathDelimiter);
            paths.add(path);
            return;
        }
        if (currentNode.getChildrenCount() == 1) {
            // directory contains a single directory, or single file; in either case keep going
            computeMeaningfulDirectoriesRecur(paths, currentNode.getChildren().iterator().next(), pathDelimiter);
            return;
        }
        // no children, probably a file
        if (currentNode.isFile()) {
            // the parent must be made a source folder
            FSTree parentNode = currentNode.getParent();
            if ((parentNode != null) && !parentNode.isRoot()) {
                String path = parentNode.getPath(pathDelimiter);
                paths.add(path);
            } else {
                // there is a file in the root node
                // not much we can do about it because we don't want to add the whole tree
            }
        } else {
            // somehow an empty directory ended up as the leaf node
            // ignore this
        }
    }

    // OTHER INTERNALS

    @Override
    public int compareTo(FSTree other) {
        if (other == null) {
            return -1;
        }
        return logicalPath.compareTo(other.logicalPath);
    }
}
