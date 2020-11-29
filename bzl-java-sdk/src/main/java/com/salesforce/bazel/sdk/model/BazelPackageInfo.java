/**
 * Copyright (c) 2019, Salesforce.com, Inc. All rights reserved.
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
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.salesforce.bazel.sdk.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model class for a Bazel Java package. It is a node in a tree of the hierarchy of packages. The root node in this tree
 * is the directory which contains the WORKSPACE file, and is a special case in that it does not typically represent
 * Bazel Java package.
 * <p>
 * Important qualifications:
 * <ul>
 * <li>There can be one of these objects for each package (currently, only Java) in the workspace.
 * <li>Directories that do not have a BUILD file will not have an BazelPackageInfo object
 * </ul>
 * <p>
 * Example:<br/>
 * WORKSPACE root (BazelPackageInfo instance 1)<br/>
 * //projects/libs/apple (BazelPackageInfo instance 2) <br/>
 * //projects/libs/banana (BazelPackageInfo instance 3) <br/>
 *
 * @author plaird
 */
public class BazelPackageInfo implements BazelPackageLocation {

    private final String relativeWorkspacePath;
    private final File directory;

    private BazelPackageInfo parent;
    private final boolean isWorkspaceRoot;
    protected final File workspaceRoot;
    protected BazelPackageInfo workspaceRootNode;

    public static final String WORKSPACE_FILENAME = "WORKSPACE";
    public static final String WORKSPACE_FILENAME_ALT = "WORKSPACE.bazel";

    private String computedPackageName = null;
    private String computedPackageNameLastSegment = null;

    private final Map<String, BazelPackageInfo> childPackages = new LinkedHashMap<String, BazelPackageInfo>();

    /**
     * Creates the root info object for a Bazel workspace. This is not normally associated with an actual Bazel package
     * (hopefully not), so it is a special case node. All other info nodes descend from this node.
     *
     * @param rootDirectory
     *            the file system location that holds the workspace. This directory must have a WORKSPACE file.
     */
    public BazelPackageInfo(File rootDirectory) {
        if (rootDirectory == null) {
            throw new IllegalArgumentException(
                    "A file system path is required to construct the root BazelPackageInfo.");
        }
        if (!rootDirectory.exists()) {
            throw new IllegalArgumentException("A non-existent path [" + rootDirectory.getAbsolutePath()
                    + "] was used to construct a BazelPackageInfo.");
        }

        this.workspaceRoot = rootDirectory;
        File workspaceFile = new File(this.workspaceRoot, WORKSPACE_FILENAME);
        if (!workspaceFile.exists()) {
            workspaceFile = new File(this.workspaceRoot, WORKSPACE_FILENAME_ALT);
            if (!workspaceFile.exists()) {
                throw new IllegalArgumentException("The path [" + rootDirectory.getAbsolutePath()
                        + "] does not contain a " + WORKSPACE_FILENAME + " file.");
            }
        }

        this.parent = null;
        this.isWorkspaceRoot = true;
        this.workspaceRootNode = this;
        this.relativeWorkspacePath = "";
        this.directory = rootDirectory;

        // compute and cache the package name
        getBazelPackageName();
    }

    /**
     * Creates a new info object for a Bazel package
     *
     * @param anotherNode
     *            another node of the BazelPackageInfo tree, this cannot be null. The 'best' parent node for this new
     *            node will be found using the passed node's links to the other nodes in the tree. The parent package
     *            info node is normally passed here, but it be any node in the tree.
     * @param relativeWorkspacePath
     *            the file system path, relative to the workspace root directory. The path separator must be the OS path
     *            separator.
     */
    public BazelPackageInfo(BazelPackageInfo anotherNode, String relativeWorkspacePath) {
        if (relativeWorkspacePath == null || relativeWorkspacePath.isEmpty()) {
            throw new IllegalArgumentException("An empty path was used to construct a BazelPackageInfo.");
        }
        if (relativeWorkspacePath.startsWith(File.separator)) {
            throw new IllegalArgumentException(
                    "BazelPackageInfo constructor requires a relative path, got [" + relativeWorkspacePath + "]");
        }
        if (relativeWorkspacePath.endsWith(File.separator)) {
            relativeWorkspacePath = relativeWorkspacePath.substring(0, relativeWorkspacePath.length() - 1);
        }
        this.relativeWorkspacePath = relativeWorkspacePath;

        if (anotherNode == null) {
            throw new IllegalArgumentException(
                    "You must pass a non-null anotherNode to the BazelPackageInfo constructor.");
        }

        this.isWorkspaceRoot = false;
        this.workspaceRoot = anotherNode.workspaceRoot;
        this.workspaceRootNode = anotherNode.workspaceRootNode;

        this.directory = new File(this.workspaceRoot, this.relativeWorkspacePath);
        if (!this.directory.exists()) {
            throw new IllegalArgumentException("A non-existent path [" + this.directory.getAbsolutePath()
                    + "] was used to construct a BazelPackageInfo.");
        }

        File workspaceFile = new File(this.directory, WORKSPACE_FILENAME);
        if (workspaceFile.exists()) {
            throw new IllegalArgumentException(
                    "The path [" + this.directory.getAbsolutePath() + "] contains a " + WORKSPACE_FILENAME
                            + " file. Nested workspaces are not supported by BazelPackageInfo at this time");
        }
        workspaceFile = new File(this.directory, WORKSPACE_FILENAME_ALT);
        if (workspaceFile.exists()) {
            throw new IllegalArgumentException(
                    "The path [" + this.directory.getAbsolutePath() + "] contains a " + WORKSPACE_FILENAME_ALT
                            + " file. Nested workspaces are not supported by BazelPackageInfo at this time");
        }

        // compute and cache the package name
        String packageName = getBazelPackageName();

        // check if this is a dupe node
        if (findByPackage(packageName) != null) {
            throw new IllegalArgumentException(
                    "The package [" + packageName + "] already exists in the BazelPackageInfo tree.");

        }

        // we passed all validation, find and hook us up to the right parent
        // find the parent, if one exists that is more narrow than the root
        this.parent = findBestParent(this.workspaceRootNode);
        if (this.parent == null) {
            this.parent = this.workspaceRootNode;
        }
        this.parent.childPackages.put(this.relativeWorkspacePath, this);
    }

    /**
     * Returns the parent info object, or null if this is the workspace root.
     */
    public BazelPackageInfo getParentPackageInfo() {
        return parent;
    }

    /**
     * Get the child packages (if any)
     */
    public Collection<BazelPackageInfo> getChildPackageInfos() {
        return this.childPackages.values();
    }

    /**
     * Is this node the workspace root?
     *
     * @return true if the root, false otherwise
     */
    @Override
    public boolean isWorkspaceRoot() {
        return this.isWorkspaceRoot;
    }

    /**
     * Gets the workspace root filesystem directory.
     *
     * @return the root directory
     */
    @Override
    public File getWorkspaceRootDirectory() {
        // now is a good time to check that the root directory is still there
        if (!this.workspaceRoot.exists()) {
            throw new IllegalStateException("The workspace root directory [" + this.workspaceRoot.getAbsolutePath()
                    + "] has been deleted or moved.");
        }

        return this.workspaceRoot;
    }

    /**
     * Gets the WORKSPACE file
     *
     * @return the file
     */
    public File getWorkspaceFile() {
        // now is a good time to check that the root directory is still there
        if (!this.workspaceRoot.exists()) {
            throw new IllegalStateException("The workspace root directory [" + this.workspaceRoot.getAbsolutePath()
                    + "] has been deleted or moved.");
        }
        File workspaceFile = new File(this.workspaceRoot, WORKSPACE_FILENAME);
        // and that the WORKSPACE file is still there
        if (!workspaceFile.exists()) {
            throw new IllegalStateException(
                    "The WORKSPACE file [" + workspaceFile.getAbsolutePath() + "] has been deleted or moved.");
        }

        return workspaceFile;
    }

    /**
     * Returns the absolute file system path of the package in the workspace. The separator char will be the OS file
     * separator.
     * <p>
     *
     * e.g. "/home/joe/dev/projects/libs/apple" or "C:\dev\projects\libs\apple"
     */
    public String getBazelPackageFSAbsolutePath() {
        File absPath = new File(workspaceRoot, relativeWorkspacePath);
        return absPath.getAbsolutePath();
    }

    /**
     * Returns the relative file system path of the package in the workspace. The separator char will be the OS file
     * separator.
     * <p>
     *
     * e.g. "projects/libs/apple" or "projects\libs\apple"
     */
    @Override
    public String getBazelPackageFSRelativePath() {
        return relativeWorkspacePath;
    }

    /**
     * This is a slight variation of getBazelPackageFSRelativePath() for cases in which the file system path is
     * presented in the UI.
     * <p>
     * It returns a special String if this package is the root directory in the Bazel workspace, otherwise it just
     * returns the results of getBazelPackageFSPath(). The special String, if returned, cannot be used for any file
     * system operation (i.e. it is only for human consumption).
     */
    public String getBazelPackageFSRelativePathForUI() {
        if ("".equals(relativeWorkspacePath)) {
            // return a String that is a cue in the UI that this directory is the root of the WORKSPACE

            // TODO replace this with the workspace id in the WORKSPACE file
            // workspace(name = "my_mono_repo")

            return WORKSPACE_FILENAME;
        }

        return getBazelPackageFSRelativePath();
    }

    /**
     * Provides the proper Bazel label for the Bazel package.
     * <p>
     *
     * e.g. "//projects/libs/apple"
     */
    @Override
    public String getBazelPackageName() {
        if (computedPackageName != null) {
            return computedPackageName;
        }

        if ("".equals(relativeWorkspacePath)) {
            // the caller is referring to the WORKSPACE root, which for build operations can
            // (but not always) means that the user wants to build the entire workspace.

            // TODO refine this, so that if the root directory contains a BUILD file with a Java package to
            // somehow handle that workspace differently
            // Docs should indicate that a better practice is to keep the root dir free of an actual package
            // For now, assume that anything referring to the root dir is a proxy for 'whole repo'
            computedPackageName = "//...";
            return computedPackageName;
        }

        // split the file system path by OS path separator
        String[] pathElements = relativeWorkspacePath.split(File.separator);

        // assemble the path elements into a proper Bazel package name
        String name = "/";
        for (String e : pathElements) {
            if (e.isEmpty()) {
                continue;
            }
            name = name + "/" + e;
        }

        // set computedPackageName only when done computing it, to avoid threading issues
        computedPackageName = name;
        // and cache the last segment as well
        getBazelPackageNameLastSegment();

        return computedPackageName;
    }

    /**
     * Provides the last segment of the proper Bazel label for the Bazel package.
     * <p>
     * e.g. if "//projects/libs/apple" is the package name, will return 'apple'
     */
    @Override
    public String getBazelPackageNameLastSegment() {
        if (computedPackageNameLastSegment != null) {
            return computedPackageNameLastSegment;
        }
        int lastSlash = computedPackageName.lastIndexOf("/");
        if (lastSlash == -1) {
            computedPackageNameLastSegment = "";
            return computedPackageNameLastSegment;
        }
        computedPackageNameLastSegment = computedPackageName.substring(lastSlash + 1);

        if ("...".equals(computedPackageNameLastSegment)) {
            // this is the special case root node, instead of using ... from //... just set to empty
            computedPackageNameLastSegment = "";
        }

        return computedPackageNameLastSegment;
    }

    /**
     * Find a node in the tree that has the passed Bazel package path
     *
     * @param bazelPackagePath
     *            path to find, such as //projects/libs/apple
     * @return the node if found, or null
     */
    public BazelPackageInfo findByPackage(String bazelPackagePath) {
        if ((bazelPackagePath == null) || bazelPackagePath.isEmpty()) {
            throw new IllegalArgumentException("An empty path was passed to BazelPackageInfo.findByPackage()");
        }
        if (!bazelPackagePath.startsWith("//")) {
            throw new IllegalArgumentException(
                    "You must pass a Bazel path (e.g. //projects/libs/apple) to BazelPackageInfo.findByPackage(), got ["
                            + bazelPackagePath + "]");
        }

        if ("//...".equals(bazelPackagePath)) {
            // special case
            return this.workspaceRootNode;
        }
        return findByPackageRecur(this.workspaceRootNode, bazelPackagePath);
    }

    private BazelPackageInfo findByPackageRecur(BazelPackageInfo currentNode, String bazelPackagePath) {
        if (bazelPackagePath.equals(currentNode.getBazelPackageName())) {
            return currentNode;
        }

        for (BazelPackageInfo child : currentNode.childPackages.values()) {
            if (bazelPackagePath.startsWith(child.getBazelPackageName())) {
                return findByPackageRecur(child, bazelPackagePath);
            }
        }
        return null;
    }

    private BazelPackageInfo findBestParent(BazelPackageInfo candidate) {
        for (BazelPackageInfo child : candidate.childPackages.values()) {
            if (relativeWorkspacePath.startsWith(child.relativeWorkspacePath + File.separator)) {
                BazelPackageInfo betterParent = findBestParent(child);
                if (betterParent == null) {
                    return child;
                }
                return betterParent;
            }
        }
        return null;
    }

    @Override
    public List<BazelPackageLocation> gatherChildren() {
        List<BazelPackageLocation> gatherList = new ArrayList<>();
        gatherChildrenRecur(gatherList);
        return gatherList;
    }

    public void gatherChildrenRecur(List<BazelPackageLocation> gatherList) {
        if (!this.isWorkspaceRoot()) {
            gatherList.add(this);
        }
        for (BazelPackageLocation child : this.childPackages.values()) {
            BazelPackageInfo childInfo = (BazelPackageInfo) child;
            childInfo.gatherChildrenRecur(gatherList);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((computedPackageName == null) ? 0 : computedPackageName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BazelPackageInfo other = (BazelPackageInfo) obj;
        if (computedPackageName == null) {
            if (other.computedPackageName != null)
                return false;
        } else if (!computedPackageName.equals(other.computedPackageName))
            return false;
        return true;
    }

}
